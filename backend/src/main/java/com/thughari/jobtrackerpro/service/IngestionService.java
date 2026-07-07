package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.interfaces.AiExtractionService;
import com.thughari.jobtrackerpro.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class IngestionService {

    private final AiExtractionService aiService;
    private final JobService jobService;
    private final UserRepository userRepository;

    public IngestionService(AiExtractionService aiService, JobService jobService, UserRepository userRepository) {
        this.aiService = aiService;
        this.jobService = jobService;
        this.userRepository = userRepository;
    }

    @Transactional
    public void handleManualForward(String from, String subject, String body, String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user == null) return;

        if (Boolean.TRUE.equals(user.getGmailConnected())) {
            log.warn("Discarding forwarded email for {}: Direct Sync is active.", userEmail);
            return;
        }

        log.info("Forwarding email to AI for user: {}", userEmail);
        JobDTO job = aiService.extractJobFromEmail(from, subject, body);

        if (job != null) {
            jobService.createOrUpdateJob(job, userEmail);
        }
    }
}