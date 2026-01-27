package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.service.EmailService;
import com.thughari.jobtrackerpro.service.JobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class WebhookController {

    private final JobService jobService;
    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final EmailService emailService;

    public WebhookController(JobService jobService, UserRepository userRepository, GeminiService geminiService, EmailService emailService) {
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.geminiService = geminiService;
        this.emailService = emailService;
    }

    @PostMapping("/inbound-email")
    public ResponseEntity<String> handleInboundEmail(@RequestBody Map<String, Object> payload) {
        try {
            if (payload == null || !payload.containsKey("headers")) {
                log.warn("Webhook received invalid payload");
                return ResponseEntity.ok("Invalid Payload");
            }

            Map<String, Object> headers = (Map<String, Object>) payload.get("headers");
            
            String from = (String) headers.getOrDefault("from", ""); 
            String to = (String) headers.getOrDefault("to", "");
            String subject = (String) headers.getOrDefault("subject", "");
            
            String plainText = (String) payload.get("plain"); 
            if (plainText == null) {
                String html = (String) payload.get("html");
                if (html != null) {
                    plainText = html.replaceAll("<[^>]*>", " ");
                } else {
                    plainText = "";
                }
            }
            
            String xForwardedFor = (String) headers.getOrDefault("x-forwarded-for", "");

            log.info("Processing Email: '{}'", subject);
            
            if (from.contains("google.com") && subject.contains("Forwarding Confirmation")) {
                 String targetEmail = null;
                Matcher emailMatcher = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}) has requested").matcher(plainText);
                if (emailMatcher.find()) {
                    targetEmail = emailMatcher.group(1);
                }

                String code = null;
                Matcher codeMatcher = Pattern.compile("Confirmation code:\\s*([0-9]+)").matcher(plainText);
                if (codeMatcher.find()) {
                    code = codeMatcher.group(1);
                }

                String link = null;
                Matcher linkMatcher = Pattern.compile("(https://(mail|mail-settings)\\.google\\.com/mail/vf-[^\\s\\)]+)").matcher(plainText);
                if (linkMatcher.find()) {
                    link = linkMatcher.group(1);
                }

                if (targetEmail != null && (code != null || link != null)) {
                    log.info("Forwarding verification to {}", targetEmail);
                    emailService.sendForwardingHelper(targetEmail, code, link);
                    return ResponseEntity.ok("Verification Forwarded");
                }
                
                return ResponseEntity.ok("Verification Parse Failed");
            }

            if (subject.toLowerCase().contains("verify") || 
                subject.toLowerCase().contains("postmaster")) {
                return ResponseEntity.ok("Ignored System Email");
            }

            User user = null;
            String identifiedEmail = null;

            identifiedEmail = extractEmailAddress(from);
            if (identifiedEmail != null) user = userRepository.findByEmail(identifiedEmail).orElse(null);

            if (user == null) {
                identifiedEmail = extractEmailAddress(to);
                if (identifiedEmail != null) user = userRepository.findByEmail(identifiedEmail).orElse(null);
            }

            if (user == null && xForwardedFor != null && !xForwardedFor.isEmpty()) {
                String[] parts = xForwardedFor.split("[\\s,]+");
                for (String part : parts) {
                    identifiedEmail = extractEmailAddress(part);
                    if (identifiedEmail != null) {
                        user = userRepository.findByEmail(identifiedEmail).orElse(null);
                        if (user != null) break;
                    }
                }
            }

            if (user == null) {
                log.warn("User unknown. From: [{}], To: [{}]", from, to);
                return ResponseEntity.ok("User Unknown");
            }

            JobDTO job = geminiService.extractJobFromEmail(from, subject, plainText);
            
            if (job == null) {
                log.info("Skipping email (Not a job application or extraction failed)");
                return ResponseEntity.ok("Skipped");
            }
            
            String normalizedEmail = user.getEmail().toLowerCase();
            
            jobService.createOrUpdateJob(job, normalizedEmail);
            
            log.info("Processed Email for User: {} | Company: {}", user.getEmail(), job.getCompany());
            return ResponseEntity.ok("Processed");

        } catch (Exception e) {
            log.error("Webhook failed", e);
            return ResponseEntity.ok("Error handled");
        }
    }
    
    private String extractEmailAddress(String raw) {
        if (raw == null) return null;
        Matcher m = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6})").matcher(raw);
        if (m.find()) return m.group(1).toLowerCase().trim();
        return null;
    }
}