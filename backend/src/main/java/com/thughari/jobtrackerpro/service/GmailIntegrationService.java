package com.thughari.jobtrackerpro.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.util.CacheEvictService;
import com.thughari.jobtrackerpro.util.UrlParser;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class GmailIntegrationService {

    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final JobService jobService;
    private final RestClient restClient;
    private final CacheEvictService cacheEvictService;
    private final String APPLICATION_NAME = "JobTrackerPro";
    
    private static final NetHttpTransport HTTP_TRANSPORT;
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    
    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Google Transport", e);
        }
    }

    private static final String ATS_FILTER = "from:(myworkday.com OR greenhouse.io OR lever.co OR smartrecruiters.com OR icims.com OR jobvite.com OR bamboo.hr OR workablemail.com OR successfactors.com OR taleo.net OR avature.net OR jobs2careers.com OR ziprecruiter.com OR monster.com OR careerbuilder.com OR wellfound.com OR lu.ma OR breezy.hr OR jazzhr.com OR comeet.com OR recruitee.com OR teamtailor.com OR applytojob.com OR jobs.github.com OR hackerrankforwork.com OR hackerrank.com OR hackerearth.com OR codility.com OR testgorilla.com OR hirevue.com OR vidcruiter.com OR codemetry.com OR pymetrics.com OR hired.com OR triplebyte.com)";
    private static final String SUBJECT_FILTER = "subject:(\"Application\" OR \"Applied\" OR \"Applying\" OR \"Thank You\" OR \"Received\" OR \"Confirmation\" OR \"Interview\" OR \"Status\" OR \"Sollicitatie\" OR \"Engineer\" OR \"Developer\" OR \"Analyst\" OR \"Scientist\" OR \"Specialist\" OR \"Invitation\" OR \"Invite\" OR \"Assessment\" OR \"Challenge\" OR \"Test\" OR \"Screened\" OR \"Position\" OR \"Declaration\" OR \"Talent\")";
    private static final String EXCLUSIONS = " -\"payment\" -\"invoice\" -\"otp\" -\"transaction\" -\"statement\" -\"bank\" -\"security alert\" -\"verification code\"";
    
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;
    
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${app.google.pubsub-topic}")
    private String pubsubTopic;

    public GmailIntegrationService(UserRepository userRepository, GeminiService geminiService, JobService jobService, CacheEvictService cacheEvictService) {
        this.userRepository = userRepository;
        this.geminiService = geminiService;
        this.jobService = jobService;
        this.restClient = RestClient.create();
        this.cacheEvictService = cacheEvictService;
    }

    @Transactional
    @Caching(evict = {
    		@CacheEvict(value = "users", key = "#email"),
            @CacheEvict(value = "userEntities", key = "#email")
    	})
    public void connectAndSetupPush(String authCode, String email) throws Exception {
    	
    	User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    	
        NetHttpTransport transport = HTTP_TRANSPORT;
        GsonFactory jsonFactory = JSON_FACTORY;

        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                transport, jsonFactory, "https://oauth2.googleapis.com/token",
                clientId, clientSecret, authCode, "postmessage").execute();

        String refreshToken = tokenResponse.getRefreshToken();
        String accessToken = tokenResponse.getAccessToken();

        Gmail service = new Gmail.Builder(transport, jsonFactory, request -> 
                request.getHeaders().setAuthorization("Bearer " + accessToken))
                .setApplicationName(APPLICATION_NAME).build();

        String labelId = getOrCreateLabel(service);
        createJobFilter(service, labelId);

        WatchRequest watchRequest = new WatchRequest()
                .setTopicName(pubsubTopic)
                .setLabelIds(List.of(labelId));
        
        WatchResponse watchResponse = service.users().watch("me", watchRequest).execute();

        user.setGmailConnected(true);
        if (refreshToken != null) {
            user.setGmailRefreshToken(refreshToken);
        }
        user.setGmailLabelId(labelId);
        user.setGmailHistoryId(watchResponse.getHistoryId().toString());
        user.setGmailWatchExpiration(watchResponse.getExpiration());

        userRepository.saveAndFlush(user);
        
        log.info("Gmail Automation enabled with 1 DB transaction for: {}", user.getEmail());
    }

    @Async("taskExecutor")
    public void initiateManualSync(String email) {
    	
    	int updatedRows = userRepository.claimSyncLock(email);
    	
        if (updatedRows == 0) {
            return; 
        }
        cacheEvictService.evictAllForUser(email);
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not connected to Gmail"));
            
            if (!user.getGmailConnected() || user.getGmailRefreshToken() == null) {
                log.warn("User {} attempted sync without valid Gmail connection", email);
                return;
            }

            String accessToken = getFreshAccessToken(user.getGmailRefreshToken());
            
            int found = scanInbox(accessToken, email);
            
            Gmail service = createGmailClient(accessToken);
            String currentHistoryId = service.users().getProfile("me").execute().getHistoryId().toString();
            
            jobService.finalizeManualSync(email, currentHistoryId);

            log.info("Manual sync finished for {}. Found {} jobs.", email, found);
        } catch (Exception e) {
            log.error("Manual sync failed for {}: {}", email, e.getMessage());
        } finally {
            userRepository.releaseSyncLock(email);
            cacheEvictService.evictAllForUser(email);
        }
    }

    @Transactional
    public void renewWatch(User user) {
        try {
            String accessToken = getFreshAccessToken(user.getGmailRefreshToken());

            Gmail service = createGmailClient(accessToken);

            WatchRequest watchRequest = new WatchRequest()
                    .setTopicName(pubsubTopic)
                    .setLabelIds(List.of(user.getGmailLabelId()));

            WatchResponse watchResponse = service.users().watch("me", watchRequest).execute();

            user.setGmailWatchExpiration(watchResponse.getExpiration());
            user.setGmailHistoryId(watchResponse.getHistoryId().toString());
            userRepository.saveAndFlush(user);

            log.info("Successfully renewed Gmail watch for: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to renew watch for user {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public int scanInbox(String accessToken, String userEmail) {
        List<EmailBatchItem> batchItems = new ArrayList<>();
        int totalFound = 0;

        try {
            Gmail service = createGmailClient(accessToken);
            String query = "newer_than:7d (" + ATS_FILTER + " OR " + SUBJECT_FILTER + ")" + EXCLUSIONS;
            
            String pageToken = null;
            do {
                ListMessagesResponse response = service.users().messages().list("me")
                        .setQ(query)
                        .setPageToken(pageToken)
                        .execute();

                if (response.getMessages() != null) {
                    for (Message msg : response.getMessages()) {
                        Message fullMsg = service.users().messages().get("me", msg.getId()).setFormat("full").execute();
                        long millisecondTimestamp = fullMsg.getInternalDate(); 
                        LocalDateTime emailDate = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(millisecondTimestamp), ZoneOffset.UTC);
                        
                        String from = "", subj = "", replyTo="";
                        if (fullMsg.getPayload().getHeaders() != null) {
                            for (var h : fullMsg.getPayload().getHeaders()) {
                                if ("From".equalsIgnoreCase(h.getName())) from = h.getValue();
                                if ("Subject".equalsIgnoreCase(h.getName())) subj = h.getValue();
                                if ("Reply-To".equalsIgnoreCase(h.getName())) replyTo = h.getValue();
                            }
                        }

                        if (!isSystemNoise(subj)) {
                            String body = extractTextFromBody(fullMsg.getPayload());
                            batchItems.add(new EmailBatchItem(from, subj, replyTo, body, emailDate));
                        }
                    }
                }
                pageToken = response.getNextPageToken();
            } while (pageToken != null);

            if (!batchItems.isEmpty()) {
                
                List<List<String>> urlMaps = batchItems.parallelStream()
                        .map(item -> UrlParser.extractAndCleanUrls(item.body()))
                        .toList();

                List<JobDTO> extractedJobs = geminiService.extractJobsFromBatch(batchItems);
                
                for (JobDTO job : extractedJobs) {
                    hydrateJobUrl(job, urlMaps);
                    jobService.createOrUpdateJob(job, userEmail);
                    totalFound++;
                }
                
//                for (JobDTO job : extractedJobs) {
//                    Integer inputIdx = job.getInputIndex();
//                    
//                    if (inputIdx != null && inputIdx >= 0 && inputIdx < batchUrlLists.size()) {
//                        List<String> urlsForThisEmail = batchUrlLists.get(inputIdx);
//
//                        if (job.getUrlIndex() != null && job.getUrlIndex() >= 0 && job.getUrlIndex() < urlsForThisEmail.size()) {
//                            job.setUrl(urlsForThisEmail.get(job.getUrlIndex()));
//                        } 
//                        else if (job.getUrl() == null || job.getUrl().isEmpty()) {
//                            job.setUrl(urlsForThisEmail.stream()
//                                .filter(u -> u.toLowerCase().contains("career") || u.toLowerCase().contains("job") || u.toLowerCase().contains("apply"))
//                                .findFirst().orElse(""));
//                        }
//                    }
//
//                    sanitizeUrl(job);
//
//                    jobService.createOrUpdateJob(job, userEmail);
//                    totalFound++;
//                }
            }

        } catch (Exception e) {
            log.error("Historical batch scan failed for {}: {}", userEmail, e.getMessage());
        }
        return totalFound;
    }
    
    private void hydrateJobUrl(JobDTO job, List<List<String>> urlMaps) {
        Integer idx = job.getInputIndex();
        if (idx != null && idx >= 0 && idx < urlMaps.size()) {
            List<String> urls = urlMaps.get(idx);
            if (job.getUrlIndex() != null && job.getUrlIndex() >= 0 && job.getUrlIndex() < urls.size()) {
                job.setUrl(urls.get(job.getUrlIndex()));
            } else if (job.getUrl() == null || job.getUrl().isBlank()) {
                job.setUrl(urls.stream().filter(u -> u.contains("job") || u.contains("career")).findFirst().orElse(""));
            }
        }
        sanitizeUrl(job);
    }

    private void sanitizeUrl(JobDTO job) {
        if (job.getUrl() != null) {
            String lower = job.getUrl().toLowerCase();
            if (lower.contains("unsubscribe") || lower.contains("privacy") || lower.contains("settings")) {
                job.setUrl("");
            }
        }
    }
    
    private String extractTextFromBody(MessagePart part) {
        if (part.getBody() != null && part.getBody().getData() != null) {
            byte[] decodedBytes = java.util.Base64.getUrlDecoder().decode(part.getBody().getData());
            String content = new String(decodedBytes);
            if (part.getMimeType().contains("text/plain")) return content;
            if (part.getMimeType().contains("text/html")) return content.replaceAll("<[^>]*>", " ");
        }
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String text = extractTextFromBody(subPart);
                if (text != null && !text.isBlank()) return text;
            }
        }
        return "";
    }
    
    private boolean isSystemNoise(String subject) {
        if (subject == null) return true;
        String s = subject.toLowerCase();
        return s.contains("security alert") || s.contains("sign-in") || s.contains("verification code")
                || s.contains("payment") || s.contains("otp");
    }

    private Gmail createGmailClient(String token) throws Exception {
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, 
                request -> request.getHeaders().setAuthorization("Bearer " + token))
                .setApplicationName(APPLICATION_NAME).build();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#email"),
        @CacheEvict(value = "userEntities", key = "#email")
    })
    public void disconnectGmail(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String refreshToken = user.getGmailRefreshToken();
        String labelId = user.getGmailLabelId();

        user.setGmailConnected(false);
        user.setGmailRefreshToken(null);
        user.setGmailHistoryId(null);
        user.setGmailLabelId(null);
        user.setGmailWatchExpiration(null);
        user.setGmailSyncInProgress(false);
        
        userRepository.saveAndFlush(user);

        cleanupGoogleResourcesAsync(refreshToken, labelId);
        
        log.info("User {} disconnected from Gmail. Local state cleared.", email);
    }

    @Async("taskExecutor")
    protected void cleanupGoogleResourcesAsync(String refreshToken, String labelId) {
        try {
            String accessToken = getFreshAccessToken(refreshToken);
            if (refreshToken == null) return;
            
            Gmail service = createGmailClient(accessToken);

            service.users().stop("me").execute();
            
            restClient.post()
                    .uri("https://oauth2.googleapis.com/revoke?token=" + refreshToken)
                    .retrieve();
                    
            log.info("Google resources cleaned up and token revoked.");
        } catch (Exception e) {
            log.warn("Non-critical: Google resource cleanup failed: {}", e.getMessage());
        }
    }

    public String getFreshAccessToken(String refreshToken) throws Exception {
        GoogleTokenResponse response = new GoogleRefreshTokenRequest(
        		HTTP_TRANSPORT,
        		JSON_FACTORY,
                refreshToken, clientId, clientSecret).execute();
        return response.getAccessToken();
    }

    private String getOrCreateLabel(Gmail service) throws Exception {
        ListLabelsResponse list = service.users().labels().list("me").execute();
        if (list.getLabels() != null) {
            for (Label l : list.getLabels()) {
                if ("JobTrackerPro".equalsIgnoreCase(l.getName())) return l.getId();
            }
        }
        Label newLabel = new Label().setName("JobTrackerPro")
                .setLabelListVisibility("labelShow")
                .setMessageListVisibility("show");
        return service.users().labels().create("me", newLabel).execute().getId();
    }

    private void createJobFilter(Gmail service, String labelId) throws Exception {
    	
    	String finalQuery = "(" + ATS_FILTER + " OR " + SUBJECT_FILTER + ")" + EXCLUSIONS;
    	
    	ListFiltersResponse listResponse = service.users().settings().filters().list("me").execute();
        
        List<Filter> existingFilters = listResponse.getFilter();

        if (existingFilters != null) {
            for (Filter existingFilter : existingFilters) {

                if (existingFilter.getAction() != null && 
                    existingFilter.getAction().getAddLabelIds() != null && 
                    existingFilter.getAction().getAddLabelIds().contains(labelId)) {
                    
                    log.info("Found outdated JobTrackerPro filter (ID: {}). Deleting for update...", existingFilter.getId());
                    service.users().settings().filters().delete("me", existingFilter.getId()).execute();
                }
            }
        }
    	
    	Filter newFilter = new Filter()
                .setCriteria(new FilterCriteria().setQuery(finalQuery))
                .setAction(new FilterAction().setAddLabelIds(List.of(labelId)));
                
        try { 
            service.users().settings().filters().create("me", newFilter).execute(); 
            log.info("Gmail Filter created successfully.");
        } catch (GoogleJsonResponseException e) { 
            if (e.getStatusCode() == 409 || 
               (e.getStatusCode() == 400 && e.getDetails().getMessage().contains("Filter already exists"))) {
                log.info("Gmail filter already exists, skipping creation.");
            } else {
                log.error("Failed to create Gmail filter: {}", e.getDetails().getMessage());
                throw e; 
            }
        }
    }
}