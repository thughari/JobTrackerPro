package com.thughari.jobtrackerpro.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import com.thughari.jobtrackerpro.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class GmailWebhookService {

    private final GeminiService geminiService;
    private final JobService jobService;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    public GmailWebhookService(GeminiService geminiService, JobService jobService, UserRepository userRepository, CacheManager cacheManager) {
        this.geminiService = geminiService;
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.cacheManager = cacheManager;
    }

    @Async("taskExecutor")
    @Transactional
    public void processHistorySync(String userEmail) {
        final String email = userEmail.toLowerCase();

        // 1. ATOMIC LOCK: Prevent parallel threads for the same user
        int updatedRows = userRepository.claimSyncLock(email);
        if (updatedRows == 0) return; 

        evictUserCaches(email);

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found after lock"));

            if (user.getGmailRefreshToken() == null) return;

            String accessToken = getFreshAccessToken(user.getGmailRefreshToken());
            Gmail service = new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                    .setApplicationName("JobTrackerPro").build();

            // 2. BOOTSTRAP: If historyId is missing
            if (user.getGmailHistoryId() == null || user.getGmailHistoryId().isBlank()) {
                bootstrapUserHistory(service, user);
                return;
            }

            // 3. FETCH HISTORY
            ListHistoryResponse historyResponse = service.users().history().list("me")
                    .setStartHistoryId(new BigInteger(user.getGmailHistoryId()))
                    .setLabelId(user.getGmailLabelId()).execute();

            // 4. PRE-CLAIM BOOKMARK: Prevent redundant processing in next webhook push
            if (historyResponse.getHistoryId() != null) {
                user.setGmailHistoryId(historyResponse.getHistoryId().toString());
                userRepository.saveAndFlush(user);
            }

            // 5. COLLECT BATCH (High Performance: Full body extraction)
            List<EmailBatchItem> batchItems = collectMessages(service, historyResponse.getHistory());

            // 6. BULK AI INGESTION
            if (!batchItems.isEmpty()) {
                log.info("Ingesting batch of {} emails via Gemini for {}", batchItems.size(), email);
                List<JobDTO> extractedJobs = geminiService.extractJobsFromBatch(batchItems);
                extractedJobs.forEach(job -> jobService.createOrUpdateJob(job, email));
            }

        } catch (Exception e) {
            log.error("High-Performance Sync failed for {}: ", email, e);
        } finally {
            userRepository.releaseSyncLock(email);
            evictUserCaches(email);
        }
    }

    private List<EmailBatchItem> collectMessages(Gmail service, List<History> historyRecords) {
        List<EmailBatchItem> items = new ArrayList<>();
        if (historyRecords == null) return items;

        for (History history : historyRecords) {
            if (history.getMessagesAdded() == null) continue;
            for (HistoryMessageAdded added : history.getMessagesAdded()) {
                try {
                    Message m = service.users().messages().get("me", added.getMessage().getId())
                            .setFormat("full").execute();
                    
                    String from = "", subj = "";
                    for (var h : m.getPayload().getHeaders()) {
                        if ("From".equalsIgnoreCase(h.getName())) from = h.getValue();
                        if ("Subject".equalsIgnoreCase(h.getName())) subj = h.getValue();
                    }

                    if (!isSystemNoise(subj)) {
                        String body = extractTextFromBody(m.getPayload());
                        items.add(new EmailBatchItem(from, subj, body));
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch message {}: {}", added.getMessage().getId(), e.getMessage());
                }
            }
        }
        return items;
    }

    private String extractTextFromBody(MessagePart part) {
        // Priority 1: Check this part's body
        if (part.getBody() != null && part.getBody().getData() != null) {
            String content = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            if (part.getMimeType().contains("text/plain")) return content;
            if (part.getMimeType().contains("text/html")) return content.replaceAll("<[^>]*>", " ");
        }
        // Priority 2: Recurse into children
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                String text = extractTextFromBody(subPart);
                if (text != null && !text.isBlank()) return text;
            }
        }
        return "";
    }

    private void bootstrapUserHistory(Gmail service, User user) throws Exception {
        log.info("Bootstrapping historyId for: {}", user.getEmail());
        String startId = service.users().getProfile("me").execute().getHistoryId().toString();
        user.setGmailHistoryId(startId);
        userRepository.saveAndFlush(user);
    }

    private boolean isSystemNoise(String subject) {
        if (subject == null) return true;
        String s = subject.toLowerCase();
        return s.contains("security alert") || s.contains("sign-in") || s.contains("verification code");
    }

    private void evictUserCaches(String email) {
        Cache userCache = cacheManager.getCache("users");
        Cache entityCache = cacheManager.getCache("userEntities");
        if (userCache != null) userCache.evict(email);
        if (entityCache != null) entityCache.evict(email);
    }

    public String getFreshAccessToken(String refreshToken) throws Exception {
        return new GoogleRefreshTokenRequest(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
                refreshToken, clientId, clientSecret).execute().getAccessToken();
    }
}