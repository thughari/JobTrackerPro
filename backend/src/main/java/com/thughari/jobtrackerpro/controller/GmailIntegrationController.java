package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.service.GmailIntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/integrations")
@Slf4j
public class GmailIntegrationController {

    private final GmailIntegrationService gmailAutomationService;

    // constructor injection - clean & thread-safe
    public GmailIntegrationController(GmailIntegrationService gmailAutomationService) {
        this.gmailAutomationService = gmailAutomationService;
    }

    @PostMapping("/gmail/connect")
    public ResponseEntity<String> connectGmail(@RequestBody Map<String, String> body) {
        String authCode = body.get("code");
        String email = getAuthenticatedEmail();
        
        try {
            // Business logic and DB lookup are hidden inside the service
            gmailAutomationService.connectAndSetupPush(authCode, email);
            return ResponseEntity.ok("Gmail Automation enabled successfully.");
        } catch (Exception e) {
            log.error("Failed to setup Gmail for user {}: {}", email, e.getMessage());
            return ResponseEntity.status(500).body("Failed to setup Gmail: " + e.getMessage());
        }
    }

    @PostMapping("/gmail/sync")
    public ResponseEntity<String> syncGmail(OAuth2AuthenticationToken authentication) {
        // High Performance: Service uses @Async to return immediately
        gmailAutomationService.initiateManualSync(authentication);
        return ResponseEntity.ok("Sync started in background. Your dashboard will update shortly.");
    }

    private String getAuthenticatedEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName().toLowerCase();
    }
}