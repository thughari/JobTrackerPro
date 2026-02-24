package com.thughari.jobtrackerpro.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.thughari.jobtrackerpro.service.GmailWebhookService;
import com.thughari.jobtrackerpro.util.GoogleNotificationDecoder;
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

    // HIGH PERFORMANCE: In-memory "Deduplicator"
    // Prevents the "Push Avalanche" from hitting the database
    private final Cache<String, Boolean> pushDeduplicator = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.SECONDS) // Ignore same user for 15 seconds
            .build();

    public GmailWebhookController(GmailWebhookService gmailService, GoogleNotificationDecoder decoder) {
        this.gmailService = gmailService;
        this.decoder = decoder;
    }

    @PostMapping("/gmail/push")
    public ResponseEntity<Void> handleGmailPush(@RequestBody Map<String, Object> body) {
    	System.out.println("--------------");
    	System.out.println(body);
    	System.out.println("--------------");
        String email = decoder.extractEmail(body);
        System.out.println("--------------");
    	System.out.println(email);
    	System.out.println("--------------");
        
        if (email != null) {
            // Check if we already handled a push for this user in the last 5 seconds
            if (pushDeduplicator.getIfPresent(email) == null) {
                pushDeduplicator.put(email, true);
                log.info("Processing valid Gmail Push for: {}", email);
                
                // Hand off to @Async service
                gmailService.processHistorySync(email);
            } else {
                log.debug("Discarding redundant push notification for: {}", email);
            }
        }
        
        return ResponseEntity.ok().build();
    }
}