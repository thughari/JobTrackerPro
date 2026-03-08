package com.thughari.jobtrackerpro.util;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Slf4j
public class GoogleOidcVerifier {

    private final GoogleIdTokenVerifier verifier;
    private final String expectedServiceAccount;

    public GoogleOidcVerifier(
            @Value("${app.security.webhook-audience}") String expectedAudience,
            @Value("${app.security.google-pubsub-service-account}") String serviceAccount) {
        
        this.expectedServiceAccount = serviceAccount;
        
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(expectedAudience))
                .setIssuer("https://accounts.google.com")
                .build();
    }

    public boolean verify(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Webhook rejected: Missing or malformed Authorization header.");
            return false;
        }

        String idTokenString = authHeader.substring(7);

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            
            if (idToken != null) {
                Payload payload = idToken.getPayload();
                
                boolean isAuthorizedSender = payload.getEmail().equals(expectedServiceAccount);
                
                boolean isEmailVerified = payload.getEmailVerified();

                if (isAuthorizedSender && isEmailVerified) {
                    log.debug("OIDC Verified: Request from {}", expectedServiceAccount);
                    return true;
                } else {
                    log.warn("Security Alert: OIDC Token valid but sender {} is unauthorized.", payload.getEmail());
                }
            } else {
                log.warn("Webhook rejected: Invalid ID Token signature or expired.");
            }
        } catch (Exception e) {
            log.error("OIDC Verification Engine Error: {}", e.getMessage());
        }
        return false;
    }
}