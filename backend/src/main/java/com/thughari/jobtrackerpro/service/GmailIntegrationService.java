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
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import com.thughari.jobtrackerpro.repo.UserRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class GmailIntegrationService {

    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final JobService jobService;
    private final String APPLICATION_NAME = "JobTrackerPro";

    private static final String ATS_FILTER = "from:(myworkday.com OR greenhouse.io OR lever.co OR smartrecruiters.com OR icims.com OR jobvite.com OR bamboo.hr OR workablemail.com OR successfactors.com OR taleo.net OR avature.net OR jobs2careers.com OR ziprecruiter.com OR monster.com OR careerbuilder.com OR wellfound.com OR lu.ma OR breezy.hr OR jazzhr.com OR comeet.com OR recruitee.com OR teamtailor.com OR applytojob.com OR jobs.github.com OR hackerrankforwork.com OR hackerrank.com OR hackerearth.com OR codility.com OR testgorilla.com OR hirevue.com OR vidcruiter.com OR codemetry.com OR pymetrics.com OR hired.com OR triplebyte.com)";
    private static final String SUBJECT_FILTER = "subject:(\"Application\" OR \"Applied\" OR \"Applying\" OR \"Thank You\" OR \"Received\" OR \"Confirmation\" OR \"Interview\" OR \"Status\" OR \"Sollicitatie\" OR \"Engineer\" OR \"Developer\" OR \"Analyst\" OR \"Scientist\" OR \"Specialist\" OR \"Invitation\" OR \"Invite\" OR \"Assessment\" OR \"Challenge\" OR \"Test\")";

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;
    
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    public GmailIntegrationService(UserRepository userRepository, GeminiService geminiService, JobService jobService) {
        this.userRepository = userRepository;
        this.geminiService = geminiService;
        this.jobService = jobService;
    }

    @Transactional
    @Caching(evict = {
    	    @CacheEvict(value = "users", key = "#user.email"),
    	    @CacheEvict(value = "userEntities", key = "#user.email")
    	})
    public void connectAndSetupPush(String authCode, String email) throws Exception {
    	
    	User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    	
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        // 1. Exchange Code for Tokens
        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                transport, jsonFactory, "https://oauth2.googleapis.com/token",
                clientId, clientSecret, authCode, "postmessage").execute();

        String refreshToken = tokenResponse.getRefreshToken();
        String accessToken = tokenResponse.getAccessToken();

        // 2. Prepare Gmail Service
        Gmail service = new Gmail.Builder(transport, jsonFactory, request -> 
                request.getHeaders().setAuthorization("Bearer " + accessToken))
                .setApplicationName(APPLICATION_NAME).build();

        // 3. Setup Gmail Environment (Label, Filter, Watch)
        String labelId = getOrCreateLabel(service);
        createJobFilter(service, labelId);

        WatchRequest watchRequest = new WatchRequest()
                .setTopicName("projects/job-tracker-pro-480917/topics/gmail-notifications")
                .setLabelIds(List.of(labelId));
        
        WatchResponse watchResponse = service.users().watch("me", watchRequest).execute();

        // 4. BATCH UPDATE: Update all fields in memory
        user.setGmailConnected(true);
        if (refreshToken != null) {
            user.setGmailRefreshToken(refreshToken);
        }
        user.setGmailLabelId(labelId);
        user.setGmailHistoryId(watchResponse.getHistoryId().toString());
        user.setGmailWatchExpiration(watchResponse.getExpiration());

        // 5. ATOMIC WRITE: One single save call to the database
        userRepository.saveAndFlush(user);
        
        log.info("Gmail Automation enabled with 1 DB transaction for: {}", user.getEmail());
    }

    /**
     * High Performance Manual Sync
     * Uses @Async to offload the 7-day scan to a background thread
     */
    @Async("taskExecutor")
    public void initiateManualSync(OAuth2AuthenticationToken auth) {
        // FIXED: Explicitly stringify and cast the attribute
        Object emailAttr = auth.getPrincipal().getAttribute("email");
        if (emailAttr == null) return;
        
        String email = emailAttr.toString().toLowerCase();
        log.info("Manual sync initiated for: {}", email);

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not connected to Gmail"));

            String accessToken = getFreshAccessToken(user.getGmailRefreshToken());
            
            // 1. Perform a historical scan (7 days)
            int found = scanInbox(accessToken, email);
            
            // 2. Align historyId so future webhooks don't process these emails again
            Gmail service = new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                    .setApplicationName(APPLICATION_NAME).build();
            
            String currentHistoryId = service.users().getProfile("me").execute().getHistoryId().toString();
            user.setGmailHistoryId(currentHistoryId);
            userRepository.save(user);

            log.info("Manual sync finished for {}. Found {} jobs.", email, found);
        } catch (Exception e) {
            log.error("Manual sync failed for {}: {}", email, e.getMessage());
        }
    }

    /**
     * High Performance Watch Renewal.
     * Refreshes the Google Push notification lease for a specific user.
     */
    @Transactional
    public void renewWatch(User user) {
        try {
            String accessToken = getFreshAccessToken(user.getGmailRefreshToken());
            
            NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            Gmail service = new Gmail.Builder(transport, jsonFactory, request -> 
                    request.getHeaders().setAuthorization("Bearer " + accessToken))
                    .setApplicationName(APPLICATION_NAME).build();

            WatchRequest watchRequest = new WatchRequest()
                    .setTopicName("projects/job-tracker-pro-480917/topics/gmail-notifications")
                    .setLabelIds(List.of(user.getGmailLabelId()));

            WatchResponse watchResponse = service.users().watch("me", watchRequest).execute();

            // Update expiration and bookmark
            user.setGmailWatchExpiration(watchResponse.getExpiration());
            user.setGmailHistoryId(watchResponse.getHistoryId().toString());
            userRepository.saveAndFlush(user);

            log.info("Successfully renewed Gmail watch for: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to renew watch for user {}: {}", user.getEmail(), e.getMessage());
            // If the token is revoked, we could mark gmail_connected = false here
        }
    }

    public int scanInbox(String accessToken, String userEmail) {
        try {
            Gmail service = new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                    .setApplicationName(APPLICATION_NAME).build();

            // Only scan emails that the user's filters have already labeled
            String query = "label:JobTrackerPro newer_than:7d";
            ListMessagesResponse response = service.users().messages().list("me").setQ(query).execute();

            if (response.getMessages() == null) return 0;

            int count = 0;
            for (Message msg : response.getMessages()) {
                Message fullMsg = service.users().messages().get("me", msg.getId()).setFormat("full").execute();
                if (processSingleMessage(fullMsg, userEmail)) count++;
            }
            return count;
        } catch (Exception e) {
            log.error("Historical scan failed: {}", e.getMessage());
            return 0;
        }
    }

    private boolean processSingleMessage(Message msg, String userEmail) {
        String subject = "No Subject", from = "Unknown";
        if (msg.getPayload() != null && msg.getPayload().getHeaders() != null) {
            for (var h : msg.getPayload().getHeaders()) {
                if ("Subject".equalsIgnoreCase(h.getName())) subject = h.getValue();
                if ("From".equalsIgnoreCase(h.getName())) from = h.getValue();
            }
        }
        
        JobDTO extracted = geminiService.extractJobFromEmail(from, subject, msg.getSnippet());
        if (extracted != null) {
            jobService.createOrUpdateJob(extracted, userEmail);
            return true;
        }
        return false;
    }

    public String getFreshAccessToken(String refreshToken) throws Exception {
        GoogleTokenResponse response = new GoogleRefreshTokenRequest(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
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
        String finalQuery = String.format("(%s OR %s) -\"Security alert\"", ATS_FILTER, SUBJECT_FILTER);
        
        Filter filter = new Filter()
                .setCriteria(new FilterCriteria().setQuery(finalQuery))
                .setAction(new FilterAction().setAddLabelIds(List.of(labelId)));
                
        try { 
            service.users().settings().filters().create("me", filter).execute(); 
            log.info("Gmail Filter created successfully.");
        } catch (GoogleJsonResponseException e) { 
            // 409 is standard conflict, but 400 with "Filter already exists" is common in Gmail API
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