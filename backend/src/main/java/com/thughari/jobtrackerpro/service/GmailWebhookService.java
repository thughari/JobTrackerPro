package com.thughari.jobtrackerpro.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.AiExtractionService;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.util.CacheEvictService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GmailWebhookService {

    private final AiExtractionService aiService;
    private final JobService jobService;
    private final UserRepository userRepository;
    private final CacheEvictService cacheEvictService;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    public GmailWebhookService(AiExtractionService aiService, JobService jobService, UserRepository userRepository, CacheEvictService cacheEvictService) {
        this.aiService = aiService;
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.cacheEvictService = cacheEvictService;
    }

    public void processHistorySync(String userEmail) {
        final String email = userEmail.toLowerCase();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryThreshold = now.minusMinutes(15);

        if (userRepository.claimSyncLock(email, now, expiryThreshold) == 0) return;

        try {
            User user = userRepository.findByEmail(email).orElseThrow();
            if (user.getGmailRefreshToken() == null) return;

            String accessToken = getFreshAccessToken(user.getGmailRefreshToken());
            Gmail service = new Gmail.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
                    request -> request.getHeaders().setAuthorization("Bearer " + accessToken))
                    .setApplicationName("JobTrackerPro").build();

            if (user.getGmailHistoryId() == null || user.getGmailHistoryId().isBlank()) {
                bootstrapUserHistory(service, user);
                return;
            }

            ListHistoryResponse historyResponse = service.users().history().list("me")
                    .setStartHistoryId(new BigInteger(user.getGmailHistoryId()))
                    .setLabelId(user.getGmailLabelId()).execute();

            if (historyResponse.getHistoryId() != null) {
                user.setGmailHistoryId(historyResponse.getHistoryId().toString());
                userRepository.saveAndFlush(user);
            }

            List<EmailBatchItem> batchItems = collectMessages(service, historyResponse.getHistory());

            if (!batchItems.isEmpty()) {
                log.info("Ingesting batch of {} emails for {}", batchItems.size(), email);
                List<JobDTO> extractedJobs = aiService.extractJobsFromBatch(batchItems);
                jobService.saveBatchResults(email, batchItems, extractedJobs);
            }
        } catch (Exception e) {
            log.error("Sync failed for {}: ", email, e);
        } finally {
            userRepository.releaseSyncLock(email);
            cacheEvictService.evictAllForUser(email);
        }
    }

    private List<EmailBatchItem> collectMessages(Gmail service, List<History> historyRecords) {
        List<EmailBatchItem> items = new ArrayList<>();
        if (historyRecords == null) return items;

        for (History history : historyRecords) {
            if (history.getMessagesAdded() == null) continue;
            for (HistoryMessageAdded added : history.getMessagesAdded()) {
                try {
                    Message m = service.users().messages().get("me", added.getMessage().getId()).setFormat("full").execute();
                    LocalDateTime emailDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(m.getInternalDate()), ZoneOffset.UTC);
                    
                    String from = "", subj = "", replyTo = "";
                    for (var h : m.getPayload().getHeaders()) {
                        if ("From".equalsIgnoreCase(h.getName())) from = h.getValue();
                        if ("Subject".equalsIgnoreCase(h.getName())) subj = h.getValue();
                        if ("Reply-To".equalsIgnoreCase(h.getName())) replyTo = h.getValue();
                    }

                    if (!isSystemNoise(subj)) {
                        String body = extractProcessedBody(m.getPayload());
                        items.add(new EmailBatchItem(from, subj, replyTo, body, emailDate));
                    }
                } catch (Exception e) {
                    log.warn("Failed message fetch: {}", e.getMessage());
                }
            }
        }
        return items;
    }

    private String extractProcessedBody(MessagePart payload) {
        StringBuilder rawBuffer = new StringBuilder();
        recursiveRawCollect(payload, rawBuffer);
        
        String cleaned = surgicalClean(rawBuffer.toString());
                
        return cleaned;
    }

    private void recursiveRawCollect(MessagePart part, StringBuilder buffer) {
        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) recursiveRawCollect(subPart, buffer);
        }
        if (part.getBody() != null && part.getBody().getData() != null) {
            buffer.append(new String(Base64.getUrlDecoder().decode(part.getBody().getData()))).append("\n");
        }
    }

    private String surgicalClean(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return "";

        String content = rawHtml.replaceAll("(?is)<style.*?>.*?</style>", "")
                                .replaceAll("(?is)<script.*?>.*?</script>", "");

        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("(?is)<a\\s+[^>]*?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*?>(.*?)</a>").matcher(content);

        int lastEnd = 0;
        while (m.find()) {
            sb.append(content, lastEnd, m.start());
            
            String rawUrl = m.group(1).replace("&amp;", "&");
            String linkText = m.group(2).replaceAll("<[^>]*>", "").trim();
            
            String processedUrl = processUrlByDomain(rawUrl);
            
            boolean isJobLink = processedUrl.contains("viewjob") || processedUrl.contains("confirmemail") || 
                               processedUrl.contains("linkedin.com/jobs") || processedUrl.contains("careers") || 
                               processedUrl.contains("apply");
            
            if (isJobLink && processedUrl.length() > 15) {
                sb.append(" [LINK_START]").append(linkText).append("[LINK_URL]").append(processedUrl).append("[LINK_END] ");
            } else {
                sb.append(" ").append(linkText).append(" ");
            }
            
            lastEnd = m.end();
        }
        sb.append(content.substring(lastEnd));

        return sb.toString()
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</td>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String processUrlByDomain(String url) {
        if (url == null) return "";
        String lowerUrl = url.toLowerCase();

        if (lowerUrl.contains("linkedin.com/jobs") || lowerUrl.contains("linkedin.com/comm/jobs")) {
            int queryIndex = url.indexOf("?");
            return queryIndex > 0 ? url.substring(0, queryIndex) : url;
        }

        if (lowerUrl.contains("indeed.com")) {
            return url;
        }

        if (url.contains("utm_") || url.contains("ref=")) {
            return url.replaceAll("[?&]utm_[^&]+", "").replaceAll("[?&]ref=[^&]+", "");
        }

        return url;
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

    public String getFreshAccessToken(String refreshToken) throws Exception {
        return new GoogleRefreshTokenRequest(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
                refreshToken, clientId, clientSecret).execute().getAccessToken();
    }
}