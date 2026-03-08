package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import com.thughari.jobtrackerpro.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class IngestionService {

    private final GeminiService geminiService;
    private final JobService jobService;
    private final UserRepository userRepository;

    public IngestionService(GeminiService geminiService, JobService jobService, UserRepository userRepository) {
        this.geminiService = geminiService;
        this.jobService = jobService;
        this.userRepository = userRepository;
    }

    @Async("taskExecutor")
    @Transactional
    public void handleManualForward(String from, String subject, String body, String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user == null) return;

        if (Boolean.TRUE.equals(user.getGmailConnected())) {
            log.info("Discarding forwarded email for {}: Direct Sync is active.", userEmail);
            return;
        }

        log.info("Forwarding email to Gemini AI for user: {}", userEmail);
        JobDTO job = geminiService.extractJobFromEmail(from, subject, body);

        if (job != null) {
            jobService.createOrUpdateJob(job, userEmail);
            log.info("Successfully ingested forwarded job: {} for {}", job.getCompany(), userEmail);
        }
    }
}