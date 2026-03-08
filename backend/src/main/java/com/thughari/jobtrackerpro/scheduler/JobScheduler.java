package com.thughari.jobtrackerpro.scheduler;

import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.PasswordResetTokenRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.repo.VerificationTokenRepository;
import com.thughari.jobtrackerpro.service.GmailIntegrationService;
import com.thughari.jobtrackerpro.service.JobService;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class JobScheduler {

    private final JobService jobService;
    private final UserRepository userRepository;
    private final GmailIntegrationService gmailIntegrationService;
    
    private final PasswordResetTokenRepository passwordTokenRepo;
    private final VerificationTokenRepository verificationTokenRepo;

    // Clean Coding: Single constructor injection
    public JobScheduler(JobService jobService, 
                        UserRepository userRepository, 
                        GmailIntegrationService gmailIntegrationService,
                        PasswordResetTokenRepository passwordTokenRepo,
                        VerificationTokenRepository verificationTokenRepo) {
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.gmailIntegrationService = gmailIntegrationService;
        this.passwordTokenRepo = passwordTokenRepo;
        this.verificationTokenRepo = verificationTokenRepo;
    }

    /**
     * Daily Maintenance: Rejects stale applications (>60 days).
     * Runs at Midnight UTC.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runStaleJobCleanup() {
        log.info("Maintenance: Starting stale job cleanup...");
        try {
            jobService.cleanupStaleApplications();
            log.info("Maintenance: Stale job cleanup completed.");
        } catch (Exception e) {
            log.error("Maintenance Error: Stale job cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * Gmail Security: Renews the 7-day watch lease every 5 days.
     * High Performance: Processes users in parallel threads.
     */
    @Scheduled(cron = "0 0 0 */5 * *") 
    public void renewGmailWatches() {
        log.info("Gmail Sync: Starting bulk watch renewal...");
        
        List<User> users = userRepository.findByGmailConnectedTrue();
        
        if (users.isEmpty()) {
            log.info("Gmail Sync: No connected users found for renewal.");
            return;
        }

        users.parallelStream().forEach(user -> {
            try {
                gmailIntegrationService.renewWatch(user);
            } catch (Exception e) {
                log.error("Gmail Sync Error: Renewal failed for {}: {}", user.getEmail(), e.getMessage());
            }
        });

        log.info("Gmail Sync: Finished bulk watch renewal for {} users.", users.size());
    }
    
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void runSystemCleanup() {
        log.info("Starting system-wide security cleanup...");
        LocalDateTime now = LocalDateTime.now();
        
        passwordTokenRepo.deleteAllExpired(now);
        
        verificationTokenRepo.deleteAllExpired(now);

        userRepository.deleteUnverifiedUsers(now.minusDays(3));

        log.info("System cleanup completed. Database pruned of expired security entries.");
    }
}