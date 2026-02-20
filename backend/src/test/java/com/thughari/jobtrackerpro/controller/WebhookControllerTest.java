package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.service.EmailService;
import com.thughari.jobtrackerpro.service.JobService;
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

    @Mock private JobService jobService;
    @Mock private UserRepository userRepository;
    @Mock private GeminiService geminiService;
    @Mock private EmailService emailService;

    @InjectMocks
    private WebhookController webhookController;

    @Test
    void returnsInvalidPayloadWhenHeadersMissing() {
        var response = webhookController.handleInboundEmail(Map.of("plain", "x"));
        assertEquals("Invalid Payload", response.getBody());
    }

    @Test
    void handlesGoogleForwardingVerification() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("from", "mailer-daemon@google.com");
        headers.put("subject", "Forwarding Confirmation");

        String plain = "user@example.com has requested\nConfirmation code: 123456";

        Map<String, Object> payload = new HashMap<>();
        payload.put("headers", headers);
        payload.put("plain", plain);

        var response = webhookController.handleInboundEmail(payload);

        assertEquals("Verification Forwarded", response.getBody());
        verify(emailService).sendForwardingHelper("user@example.com", "123456", null);
    }

    @Test
    void ignoresSystemEmails() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("from", "foo@bar.com");
        headers.put("subject", "Please verify your address");

        Map<String, Object> payload = new HashMap<>();
        payload.put("headers", headers);
        payload.put("plain", "any body");

        var response = webhookController.handleInboundEmail(payload);

        assertEquals("Ignored System Email", response.getBody());
        verifyNoInteractions(geminiService, jobService);
    }

    @Test
    void processesKnownUserAndCreatesJob() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("from", "Recruiter <hr@example.com>");
        headers.put("subject", "Application update");

        Map<String, Object> payload = new HashMap<>();
        payload.put("headers", headers);
        payload.put("plain", "hello");

        User user = new User();
        user.setEmail("Candidate@Example.com");
        when(userRepository.findByEmail("hr@example.com")).thenReturn(Optional.of(user));

        JobDTO jobDTO = new JobDTO();
        jobDTO.setCompany("Acme");
        when(geminiService.extractJobFromEmail(anyString(), anyString(), anyString())).thenReturn(jobDTO);

        var response = webhookController.handleInboundEmail(payload);

        assertEquals("Processed", response.getBody());
        verify(jobService).createOrUpdateJob(jobDTO, "candidate@example.com");
    }

    @Test
    void returnsSkippedWhenGeminiReturnsNull() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("from", "hr@example.com");
        headers.put("subject", "Update");
        Map<String, Object> payload = new HashMap<>();
        payload.put("headers", headers);
        payload.put("plain", "content");

        User user = new User();
        user.setEmail("u@example.com");
        when(userRepository.findByEmail("hr@example.com")).thenReturn(Optional.of(user));
        when(geminiService.extractJobFromEmail(anyString(), anyString(), anyString())).thenReturn(null);

        var response = webhookController.handleInboundEmail(payload);
        assertEquals("Skipped", response.getBody());
    }
}
