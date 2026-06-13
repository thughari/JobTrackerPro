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

import java.lang.Thread;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class GmailIntegrationService {

    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final JobService jobService;
    private final RestClient restClient;
    private final CacheEvictService cacheEvictService;
    private final Executor taskExecutor;
    private final String APPLICATION_NAME = "JobTrackerPro";

    @Value("${app.gmail.sync-range:30d}")
    private String syncRange;

    private static final NetHttpTransport HTTP_TRANSPORT;
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Google Transport", e);
        }
    }

    private static final String ATS_FILTER = "from:(myworkday.com OR greenhouse.io OR lever.co OR smartrecruiters.com OR icims.com OR jobvite.com OR bamboo.hr OR workablemail.com OR successfactors.com OR taleo.net OR avature.net OR jobs2careers.com OR ziprecruiter.com OR monster.com OR careerbuilder.com OR wellfound.com OR lu.ma OR breezy.hr OR jazzhr.com OR comeet.com OR recruitee.com OR teamtailor.com OR applytojob.com OR jobs.github.com OR hackerrankforwork.com OR hackerrank.com OR hackerearth.com OR codility.com OR testgorilla.com OR hirevue.com OR vidcruiter.com OR codemetry.com OR pymetrics.com OR hired.com OR triplebyte.com OR newtonsoftware.com OR jobadder.com OR jobscore.com OR jobsoid.com OR jobplex.com OR jobdroid.com OR jobiak.com OR jobg8.com OR jobisjob.com OR jobrapido.com OR simplyhired.com OR glassdoor.com OR indeed.com OR remoteok.io OR weworkremotely.com OR remote.co OR angel.co OR stackoverflow.com)";
    private static final String SUBJECT_FILTER = "subject:(Application OR Applied OR Applying OR Invited OR Candidate OR Thanks OR \"Thank You\" OR Received OR Confirmation OR Interview OR Status OR Sollicitatie OR Engineer OR Developer OR Analyst OR Scientist OR Specialist OR Invitation OR Invite OR Assessment OR Challenge OR Test OR Screened OR Position OR Declaration OR Talent OR Opportunity OR Role OR Job OR Opening OR Opened OR Lead OR Recruiter OR HR OR \"Human Resources\" OR Hiring OR Resume OR CV)";
    private static final String EXCLUSIONS = " -\"payment\" -\"invoice\" -\"otp\" -\"transaction\" -\"statement\" -\"bank\" -\"security alert\" -\"verification code\"";

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${app.google.pubsub-topic}")
    private String pubsubTopic;

    public GmailIntegrationService(UserRepository userRepository, GeminiService geminiService, JobService jobService,
            CacheEvictService cacheEvictService, Executor taskExecutor) {
        this.userRepository = userRepository;
        this.geminiService = geminiService;
        this.jobService = jobService;
        this.restClient = RestClient.create();
        this.cacheEvictService = cacheEvictService;
        this.taskExecutor = taskExecutor;
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

        Gmail service = new Gmail.Builder(transport, jsonFactory,
                request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                .setApplicationName(APPLICATION_NAME).build();

        String labelId = getOrCreateLabel(service);
        createJobFilter(service, labelId);

        String watchHistoryId = null;
        Long watchExpiration = null;
        try {
            WatchRequest watchRequest = new WatchRequest()
                    .setTopicName(pubsubTopic)
                    .setLabelIds(List.of(labelId));

            WatchResponse watchResponse = service.users().watch("me", watchRequest).execute();
            watchHistoryId = watchResponse.getHistoryId().toString();
            watchExpiration = watchResponse.getExpiration();
            log.info("Gmail Push Watch registered successfully.");
        } catch (Exception e) {
            log.warn("Failed to set up Gmail Push Watch (OIDC PubSub): {}. Falling back to manual pull-sync mode.",
                    e.getMessage());
            try {
                watchHistoryId = service.users().getProfile("me").execute().getHistoryId().toString();
            } catch (Exception ex) {
                log.error("Failed to retrieve initial Gmail profile history ID", ex);
            }
        }

        user.setGmailConnected(true);
        if (refreshToken != null) {
            user.setGmailRefreshToken(refreshToken);
        }
        user.setGmailLabelId(labelId);
        if (watchHistoryId != null) {
            user.setGmailHistoryId(watchHistoryId);
        }
        user.setGmailWatchExpiration(watchExpiration);

        userRepository.saveAndFlush(user);

        log.info("User {} successfully connected Gmail. Watch set with label ID: {}", email, labelId);
    }

    public void initiateManualSync(String email) {
        log.info("Manual sync requested for user: {}", email);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryThreshold = now.minusMinutes(15);

        int updatedRows = userRepository.claimSyncLock(email, now, expiryThreshold);
        if (updatedRows == 0) {
            log.info("Manual sync already in progress for user: {}. Request ignored.", email);
            return;
        }
        
        updateSyncStatus(email, "Initializing sync...");
        taskExecutor.execute(() -> runManualSync(email));
    }

    private void runManualSync(String email) {
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
            log.info("Manual sync successfully completed for user: {}. New jobs found: {}", email, found);
            updateSyncStatus(email, "Sync completed. Found " + found + " new jobs.");
        } catch (Exception e) {
            if ((e.getMessage() != null && e.getMessage().contains("GEMINI_QUOTA_EXCEEDED")) || 
                (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("GEMINI_QUOTA_EXCEEDED"))) {
                log.error("Manual sync paused: Gemini API daily quota exceeded.");
                updateSyncStatus(email, "Sync paused: Gemini API daily quota exceeded. Please try again tomorrow.");
            } else {
                log.error("Manual sync failed for {}: {}", email, e.getMessage());
                updateSyncStatus(email, "Sync failed: " + e.getMessage());
            }
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
        updateSyncStatus(userEmail, "Scanning inbox...");
        try {
            Gmail service = createGmailClient(accessToken);
            String query = "newer_than:" + syncRange + " (" + ATS_FILTER + " OR " + SUBJECT_FILTER + ")" + EXCLUSIONS;
            log.info("Scanning inbox with Gmail query: {}", query);

            List<Message> messageList = fetchMessageList(service, query);
            log.info("Found {} emails matching filters.", messageList.size());

            updateSyncStatus(userEmail, "Downloading details (0/" + messageList.size() + " - 0%)...");
            List<Message> fullMessages = fetchMessageDetailsInParallel(service, messageList, userEmail);
            List<EmailBatchItem> batchItems = processMessagesToBatchItems(fullMessages);
            log.info("Processed {} valid job-related batch items.", batchItems.size());

            return extractAndSaveJobs(batchItems, userEmail);
        } catch (Exception e) {
            if ((e.getMessage() != null && e.getMessage().contains("GEMINI_QUOTA_EXCEEDED")) || 
                (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("GEMINI_QUOTA_EXCEEDED"))) {
                throw new RuntimeException("GEMINI_QUOTA_EXCEEDED", e);
            }
            log.error("Historical batch scan failed for {}: {}", userEmail, e.getMessage());
            return 0;
        }
    }

    private List<Message> fetchMessageList(Gmail service, String query) throws Exception {
        List<Message> messages = new ArrayList<>();
        String pageToken = null;
        do {
            ListMessagesResponse response = service.users().messages().list("me")
                    .setQ(query)
                    .setPageToken(pageToken)
                    .execute();
            if (response.getMessages() != null) {
                messages.addAll(response.getMessages());
            }
            pageToken = response.getNextPageToken();
        } while (pageToken != null);
        return messages;
    }

    private List<Message> fetchMessageDetailsInParallel(Gmail service, List<Message> messageList, String userEmail) {
        List<Message> fullMessages = new ArrayList<>();
        int chunkSize = 15;
        for (int i = 0; i < messageList.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, messageList.size());
            int percent = (messageList.isEmpty()) ? 100 : (int) Math.round(((double) end / messageList.size()) * 100);
            String progressMsg = String.format("Downloading details (%d/%d - %d%%)...", end, messageList.size(), percent);
            updateSyncStatus(userEmail, progressMsg);
            log.info("Fetching details for messages {} to {} of {}...", i + 1, end, messageList.size());

            List<Message> chunk = messageList.subList(i, end);
            List<CompletableFuture<Message>> futures = chunk.stream()
                    .map(msg -> CompletableFuture.supplyAsync(() -> fetchMessageWithRetry(service, msg.getId()),
                            taskExecutor))
                    .toList();

            fullMessages.addAll(futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList());
            throttleChunkExecution();
        }
        return fullMessages;
    }

    private Message fetchMessageWithRetry(Gmail service, String messageId) {
        try {
            return service.users().messages().get("me", messageId).setFormat("full").execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 429) {
                log.warn("Gmail API Rate Limit (429) hit. Retrying message {} after delay...", messageId);
                backoffDelay();
                try {
                    return service.users().messages().get("me", messageId).setFormat("full").execute();
                } catch (Exception ex) {
                    log.error("Retry failed for message details: id {}", messageId, ex);
                }
            } else {
                log.error("Failed to fetch message details for id " + messageId, e);
            }
        } catch (Exception e) {
            log.error("Failed to fetch message details for id " + messageId, e);
        }
        return null;
    }

    private void backoffDelay() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void throttleChunkExecution() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private List<EmailBatchItem> processMessagesToBatchItems(List<Message> fullMessages) {
        List<EmailBatchItem> batchItems = new ArrayList<>();
        for (Message fullMsg : fullMessages) {
            String from = "", subj = "", replyTo = "";
            if (fullMsg.getPayload().getHeaders() != null) {
                for (var h : fullMsg.getPayload().getHeaders()) {
                    if ("From".equalsIgnoreCase(h.getName()))
                        from = h.getValue();
                    if ("Subject".equalsIgnoreCase(h.getName()))
                        subj = h.getValue();
                    if ("Reply-To".equalsIgnoreCase(h.getName()))
                        replyTo = h.getValue();
                }
            }
            if (!isSystemNoise(subj)) {
                String body = extractTextFromBody(fullMsg.getPayload());
                long millisecondTimestamp = fullMsg.getInternalDate();
                LocalDateTime emailDate = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(millisecondTimestamp), ZoneOffset.UTC);
                batchItems.add(new EmailBatchItem(from, subj, replyTo, body, emailDate));
            }
        }
        return batchItems;
    }

    private int extractAndSaveJobs(List<EmailBatchItem> batchItems, String userEmail) {
        int totalFound = 0;
        int batchSize = 25;
        int totalBatches = (int) Math.ceil((double) batchItems.size() / batchSize);
        for (int i = 0; i < batchItems.size(); i += batchSize) {
            int batchNum = (i / batchSize) + 1;
            int percent = (totalBatches == 0) ? 100 : (int) Math.round(((double) batchNum / totalBatches) * 100);
            String statusMsg = String.format("Extracting jobs with Gemini AI (batch %d/%d - %d%%)...", batchNum, totalBatches, percent);
            updateSyncStatus(userEmail, statusMsg);
            log.info("Running Gemini AI extraction: batch {} of {}...", batchNum, totalBatches);

            List<EmailBatchItem> subList = batchItems.subList(i, Math.min(i + batchSize, batchItems.size()));
            List<List<String>> urlMaps = subList.stream()
                    .map(item -> UrlParser.extractAndCleanUrls(item.body()))
                    .toList();

            List<JobDTO> extractedJobs = geminiService.extractJobsFromBatch(subList);
            for (JobDTO job : extractedJobs) {
                hydrateJobUrl(job, urlMaps);
                jobService.createOrUpdateJob(job, userEmail);
                totalFound++;
            }
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
            if (part.getMimeType().contains("text/plain"))
                return content;
            if (part.getMimeType().contains("text/html"))
                return content.replaceAll("<[^>]*>", " ");
        }
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String text = extractTextFromBody(subPart);
                if (text != null && !text.isBlank())
                    return text;
            }
        }
        return "";
    }

    private boolean isSystemNoise(String subject) {
        if (subject == null)
            return true;
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
    }

    protected void cleanupGoogleResourcesAsync(String refreshToken, String labelId) {
        try {
            String accessToken = getFreshAccessToken(refreshToken);
            if (refreshToken == null)
                return;

            Gmail service = createGmailClient(accessToken);

            service.users().stop("me").execute();

            restClient.post()
                    .uri("https://oauth2.googleapis.com/revoke?token=" + refreshToken)
                    .retrieve();

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
                if ("JobTrackerPro".equalsIgnoreCase(l.getName()))
                    return l.getId();
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

        if (listResponse != null && listResponse.getFilter() != null) {

            List<Filter> existingFilters = listResponse.getFilter();

            for (Filter existingFilter : existingFilters) {

                if (existingFilter.getAction() != null &&
                        existingFilter.getAction().getAddLabelIds() != null &&
                        existingFilter.getAction().getAddLabelIds().contains(labelId)) {

                    log.info("Found outdated JobTrackerPro filter (ID: {}). Deleting for update...",
                            existingFilter.getId());
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

    private void updateSyncStatus(String email, String status) {
        userRepository.updateSyncStatus(email, status);
        cacheEvictService.evictAllForUser(email);
    }
}