package com.thughari.jobtrackerpro.scheduler;

import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.PasswordResetTokenRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.repo.VerificationTokenRepository;
import com.thughari.jobtrackerpro.service.GmailIntegrationService;
import com.thughari.jobtrackerpro.service.JobService;
import com.thughari.jobtrackerpro.service.UserDeletionService;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/cron")
@Slf4j
public class JobScheduler {

    private final JobService jobService;
    private final UserRepository userRepository;
    private final GmailIntegrationService gmailIntegrationService;
    
    private final UserDeletionService userDeletionService;
    
    private final PasswordResetTokenRepository passwordTokenRepo;
    private final VerificationTokenRepository verificationTokenRepo;
    
    @Value("${app.cron.secret}")
    private String expectedCronSecret;

    public JobScheduler(JobService jobService, 
                        UserRepository userRepository, 
                        GmailIntegrationService gmailIntegrationService,
                        PasswordResetTokenRepository passwordTokenRepo,
                        VerificationTokenRepository verificationTokenRepo,
                        UserDeletionService userDeletionService) {
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.gmailIntegrationService = gmailIntegrationService;
        this.passwordTokenRepo = passwordTokenRepo;
        this.verificationTokenRepo = verificationTokenRepo;
        this.userDeletionService = userDeletionService;
    }

    /**
     * Daily Maintenance Endpoint: Replaces all internal @Scheduled jobs.
     * Validates the X-Cron-Secret header and executes jobs sequentially.
     */
    @PostMapping("/daily-maintenance")
    @Transactional
    public ResponseEntity<String> runDailyMaintenance(@RequestHeader(value = "X-Cron-Secret", required = false) String cronSecret) {
        if (cronSecret == null || !cronSecret.equals(expectedCronSecret)) {
            log.warn("Unauthorized access attempt to daily-maintenance cron endpoint");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid cron secret");
        }

        log.info("Starting Daily Maintenance cron jobs...");

        try {
            runStaleJobCleanup();
            renewGmailWatches();
            runSystemCleanup();
            processScheduledDeletions();
            
            log.info("Daily Maintenance cron jobs completed successfully.");
            return ResponseEntity.ok("Maintenance completed successfully.");
        } catch (Exception e) {
            log.error("Error during daily maintenance execution: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Maintenance failed");
        }
    }

    private void runStaleJobCleanup() {
        log.info("Maintenance Step 1: Starting stale job cleanup...");
        try {
            jobService.cleanupStaleApplications();
            log.info("Maintenance Step 1: Stale job cleanup completed.");
        } catch (Exception e) {
            log.error("Maintenance Step 1 Error: Stale job cleanup failed: {}", e.getMessage());
        }
    }

    private void renewGmailWatches() {
        log.info("Maintenance Step 2: Starting bulk watch renewal...");
        
        List<User> users = userRepository.findByGmailConnectedTrue();
        
        if (users.isEmpty()) {
            log.info("Maintenance Step 2: No connected users found for renewal.");
            return;
        }

        users.parallelStream().forEach(user -> {
            try {
                gmailIntegrationService.renewWatch(user);
            } catch (Exception e) {
                log.error("Maintenance Step 2 Error: Renewal failed for {}: {}", user.getEmail(), e.getMessage());
            }
        });

        log.info("Maintenance Step 2: Finished bulk watch renewal for {} users.", users.size());
    }
    
    private void runSystemCleanup() {
        log.info("Maintenance Step 3: Starting system-wide security cleanup...");
        LocalDateTime now = LocalDateTime.now();
        
        passwordTokenRepo.deleteAllExpired(now);
        verificationTokenRepo.deleteAllExpired(now);
        userRepository.deleteUnverifiedUsers(now.minusDays(3));

        log.info("Maintenance Step 3: System cleanup completed. Database pruned of expired security entries.");
    }
    
    private void processScheduledDeletions() {
        log.info("Maintenance Step 4: Starting scheduled user deletion cleanup...");
        
        List<User> usersToDelete = userRepository.findAllByPendingDeletionTrueAndDeletionRequestedAtBefore(
            LocalDateTime.now().minusDays(3)
        );

        for (User user : usersToDelete) {
            try {
                userDeletionService.deleteUserCompletely(user.getEmail());
            } catch (Exception e) {
                log.error("Maintenance Step 4 Error: Failed to delete user: {}", user.getEmail(), e);
            }
        }

        log.info("Maintenance Step 4: Scheduled deletion cleanup completed. Deleted {} users.", usersToDelete.size());
    }
}