package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.*;
import com.thughari.jobtrackerpro.service.InterviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/start/{jobId}")
    public ResponseEntity<InterviewSessionStartResponse> startInterview(@PathVariable UUID jobId) {
        return ResponseEntity.ok(interviewService.startSession(jobId, getAuthenticatedEmail()));
    }

    @GetMapping("/{sessionId}/questions")
    public ResponseEntity<InterviewQuestionsResponse> getQuestions(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(interviewService.getQuestions(sessionId, getAuthenticatedEmail()));
    }

    @PostMapping("/{sessionId}/answers")
    public ResponseEntity<InterviewAnswerResponse> submitAnswer(@PathVariable UUID sessionId, @RequestBody InterviewAnswerRequest request) {
        return ResponseEntity.ok(interviewService.submitAnswer(sessionId, getAuthenticatedEmail(), request));
    }

    @GetMapping("/{sessionId}/report")
    public ResponseEntity<InterviewReportResponse> getReport(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(interviewService.getReport(sessionId, getAuthenticatedEmail()));
    }

    @PostMapping("/{sessionId}/resume")
    public ResponseEntity<Map<String, String>> uploadResume(@PathVariable UUID sessionId, @RequestParam("file") MultipartFile file) {
        interviewService.uploadResume(sessionId, getAuthenticatedEmail(), file);
        return ResponseEntity.ok(Map.of("status", "processed"));
    }

    private String getAuthenticatedEmail() {
        return ((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).toLowerCase();
    }
}
