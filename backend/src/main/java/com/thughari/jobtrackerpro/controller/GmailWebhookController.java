package com.thughari.jobtrackerpro.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thughari.jobtrackerpro.service.GmailWebhookService;
import com.thughari.jobtrackerpro.util.GoogleNotificationDecoder;
import com.thughari.jobtrackerpro.util.GoogleOidcVerifier;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class GmailWebhookController {

    private final GmailWebhookService gmailService;
    private final GoogleNotificationDecoder decoder;
    private final GoogleOidcVerifier oidcVerifier;

    private final Cache<String, Boolean> pushDeduplicator = Caffeine.newBuilder()
            .expireAfterWrite(20, TimeUnit.SECONDS)
            .build();

    public GmailWebhookController(GmailWebhookService gmailService, GoogleNotificationDecoder decoder, GoogleOidcVerifier oidcVerifier) {
        this.gmailService = gmailService;
        this.decoder = decoder;
        this.oidcVerifier = oidcVerifier;
    }

    @PostMapping("/gmail/push")
    public ResponseEntity<Void> handleGmailPush(
    		@RequestHeader(value = "Authorization", required = false) String authHeader, 
    		@RequestBody Map<String, Object> body) {
    	
    	if (!oidcVerifier.verify(authHeader)) {
            log.error("SECURITY ALERT: Blocked unauthorized Webhook attempt. Header: {}", 
                     authHeader != null ? "Present (Invalid)" : "Missing");
            return ResponseEntity.ok().build(); 
        }
          	
        String email = decoder.extractEmail(body);
        
        if (email != null) {
            if (pushDeduplicator.getIfPresent(email) == null) {
                pushDeduplicator.put(email, true);
                gmailService.processHistorySync(email);
            } else {
                log.debug("Discarding redundant push notification for: {}", email);
            }
        }
        
        return ResponseEntity.ok().build();
    }
}