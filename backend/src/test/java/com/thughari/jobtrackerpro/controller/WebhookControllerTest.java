package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.service.EmailService;
import com.thughari.jobtrackerpro.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private IngestionService ingestionService;

    @InjectMocks
    private WebhookController webhookController;

    @Test
    void returnsInvalidPayloadWhenHeadersMissing() {
        var result = webhookController.handleInboundEmail(Map.of());
        assertEquals("Invalid", result.getBody());
    }
    
    @Test
    void handlesGoogleForwardingVerification() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("from", "mailer-daemon@google.com");
        headers.put("subject", "Forwarding Confirmation");

        String plain = "haribabu@gmail.com has requested\nConfirmation code: 123456";

        Map<String, Object> payload = new HashMap<>();
        payload.put("headers", headers);
        payload.put("plain", plain);

        var response = webhookController.handleInboundEmail(payload);

        assertEquals("Verification Processed", response.getBody());
        verify(emailService).sendForwardingHelper(eq("haribabu@gmail.com"), eq("123456"), any());
    }

    @Test
    void ignoresSystemEmails() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("subject", "Please verify your email address");

        Map<String, Object> payload = new HashMap<>();
        payload.put("headers", headers);

        var response = webhookController.handleInboundEmail(payload);

        assertEquals("Ignored", response.getBody());
        verifyNoInteractions(ingestionService);
    }

    @Test
    void processesKnownUserAndHandoffToAsyncIngestion() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("from", "Recruiter <hr@acme.com>");
        headers.put("to", "haribabu@gmail.com");
        headers.put("subject", "We liked your profile");

        Map<String, Object> payload = new HashMap<>();
        payload.put("headers", headers);
        payload.put("plain", "Interview invite body");

        User user = new User();
        user.setEmail("haribabu@gmail.com");

        when(userRepository.findByEmail("hr@acme.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("haribabu@gmail.com")).thenReturn(Optional.of(user));

        var response = webhookController.handleInboundEmail(payload);

        assertEquals("Accepted for processing", response.getBody());

        verify(ingestionService).handleManualForward(
            eq("Recruiter <hr@acme.com>"), 
            eq("We liked your profile"), 
            anyString(), 
            eq("haribabu@gmail.com")
        );
    }
}