package com.thughari.jobtrackerpro.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thughari.jobtrackerpro.service.GmailIntegrationService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/integrations")
@Slf4j
public class GmailIntegrationController {

    private final GmailIntegrationService gmailAutomationService;
    
    private final Cache<String, Boolean> syncThrottler = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS) // Block duplicate clicks for 10s
            .build();

    public GmailIntegrationController(GmailIntegrationService gmailAutomationService) {
        this.gmailAutomationService = gmailAutomationService;
    }

    @PostMapping("/gmail/connect")
    public ResponseEntity<String> connectGmail(@RequestBody Map<String, String> body) {
        String authCode = body.get("code");
        String email = getAuthenticatedEmail();
        
        try {
            gmailAutomationService.connectAndSetupPush(authCode, email);
            gmailAutomationService.initiateManualSync(email);
            return ResponseEntity.ok("Gmail Automation enabled successfully.");
        } catch (Exception e) {
            log.error("Failed to setup Gmail for user {}: {}", email, e.getMessage());
            return ResponseEntity.status(500).body("Failed to setup Gmail: " + e.getMessage());
        }
    }
    
    @PostMapping("/gmail/disconnect")
    public ResponseEntity<Void> disconnectGmail() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName().toLowerCase();
        gmailAutomationService.disconnectGmail(email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/gmail/sync")
    public ResponseEntity<String> syncGmail() {
    	String email = getAuthenticatedEmail();
    	
    	if (syncThrottler.getIfPresent(email) != null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Sync already requested. Please wait.");
        }
    	syncThrottler.put(email, true);
        gmailAutomationService.initiateManualSync(email);
        return ResponseEntity.ok("Sync started in background. Your dashboard will update shortly.");
    }

    private String getAuthenticatedEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName().toLowerCase();
    }
}