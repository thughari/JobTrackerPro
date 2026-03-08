package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.service.EmailService;
import com.thughari.jobtrackerpro.service.IngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class WebhookController {
	
	private static final Pattern GOOGLE_TARGET_EMAIL = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}) has requested");
	private static final Pattern GOOGLE_CONFIRM_CODE = Pattern.compile("Confirmation code:\\s*([0-9]+)");
	private static final Pattern GOOGLE_VERIFY_LINK = Pattern.compile("(https://(mail|mail-settings)\\.google\\.com/mail/vf-[^\\s\\)]+)");

	private final UserRepository userRepository;
    private final EmailService emailService;
    private final IngestionService ingestionService;

    public WebhookController(UserRepository userRepository, 
                             EmailService emailService, 
                             IngestionService ingestionService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/inbound-email")
    public ResponseEntity<String> handleInboundEmail(@RequestBody Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("headers")) return ResponseEntity.ok("Invalid");

        Map<String, Object> headers = (Map<String, Object>) payload.get("headers");
        String from = (String) headers.getOrDefault("from", "");
        String to = (String) headers.getOrDefault("to", "");
        String subject = (String) headers.getOrDefault("subject", "");
        String body = extractBody(payload);

        if (from.contains("google.com") && subject.contains("Forwarding Confirmation")) {
            handleGoogleVerification(body);
            return ResponseEntity.ok("Verification Processed");
        }

        if (subject.toLowerCase().contains("verify") || subject.toLowerCase().contains("postmaster")) {
            return ResponseEntity.ok("Ignored");
        }

        String xForwardedFor = (String) headers.getOrDefault("x-forwarded-for", "");
        User user = findUser(from, to, xForwardedFor);

        if (user == null) {
            log.warn("Forwarding Webhook: Unknown user from {}", from);
            return ResponseEntity.ok("User Unknown");
        }

        ingestionService.handleManualForward(from, subject, body, user.getEmail().toLowerCase());

        return ResponseEntity.ok("Accepted for processing");
    }
    
    private User findUser(String from, String to, String xForwarded) {
        return Stream.of(extractEmailAddress(from), extractEmailAddress(to), extractEmailAddress(xForwarded))
                .filter(Objects::nonNull)
                .map(email -> userRepository.findByEmail(email).orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
    
    private String extractBody(Map<String, Object> payload) {
        String plain = (String) payload.get("plain");
        if (plain != null) return plain;
        String html = (String) payload.get("html");
        return html != null ? html.replaceAll("<[^>]*>", " ") : "";
    }
    

    private void handleGoogleVerification(String body) {
        if (body == null || body.isBlank()) {
            log.warn("Received empty Google verification body.");
            return;
        }

        String targetEmail = null;
        Matcher emailMatcher = GOOGLE_TARGET_EMAIL.matcher(body);
        if (emailMatcher.find()) {
            targetEmail = emailMatcher.group(1).toLowerCase().trim();
        }

        String code = null;
        Matcher codeMatcher = GOOGLE_CONFIRM_CODE.matcher(body);
        if (codeMatcher.find()) {
            code = codeMatcher.group(1);
        }

        String link = null;
        Matcher linkMatcher = GOOGLE_VERIFY_LINK.matcher(body);
        if (linkMatcher.find()) {
            link = linkMatcher.group(1);
        }

        if (targetEmail != null) {
            log.info("Google Forwarding intercepted for: {}. (Code: {}, Link: {})", 
                     targetEmail, code != null, link != null);
            
            emailService.sendForwardingHelper(targetEmail, code, link);
        } else {
            log.error("Google Verification Email detected but target address could not be parsed.");
        }
    }
    
    private String extractEmailAddress(String raw) {
        if (raw == null) return null;
        Matcher m = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6})").matcher(raw);
        if (m.find()) return m.group(1).toLowerCase().trim();
        return null;
    }
}