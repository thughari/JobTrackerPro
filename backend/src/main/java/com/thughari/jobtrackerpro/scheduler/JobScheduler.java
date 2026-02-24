package com.thughari.jobtrackerpro.scheduler;

import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.service.GmailIntegrationService;
import com.thughari.jobtrackerpro.service.JobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class JobScheduler {

    private final JobService jobService;
    private final UserRepository userRepository;
    private final GmailIntegrationService gmailIntegrationService;

    // Clean Coding: Single constructor injection
    public JobScheduler(JobService jobService, 
                        UserRepository userRepository, 
                        GmailIntegrationService gmailIntegrationService) {
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.gmailIntegrationService = gmailIntegrationService;
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

        // High Performance: Use parallelStream to renew multiple users concurrently
        // This prevents a single slow Google API response from blocking the entire task.
        users.parallelStream().forEach(user -> {
            try {
                gmailIntegrationService.renewWatch(user);
            } catch (Exception e) {
                log.error("Gmail Sync Error: Renewal failed for {}: {}", user.getEmail(), e.getMessage());
            }
        });

        log.info("Gmail Sync: Finished bulk watch renewal for {} users.", users.size());
    }
}