package com.thughari.jobtrackerpro.scheduler;

import com.thughari.jobtrackerpro.service.JobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This scheduler runs a maintenance task every day at midnight to process stale 
 * job applications that haven't been updated in over 90 days.
 * 
 * Instead of physical deletion, the task updates these applications to a 'Rejected' 
 * status and adds a system note, helping keep the user dashboard relevant while 
 * preserving historical data.
 */

@Component
@Slf4j
public class JobScheduler {

    private final JobService jobService;

    public JobScheduler(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Runs every day at midnight UTC (5:30 AM IST).
     * Format: second, minute, hour, day of month, month, day(s) of week
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void runStaleJobCleanup() {
        log.info("Starting scheduled cleanup of stale applications...");
        try {
            jobService.cleanupStaleApplications();
            log.info("Scheduled cleanup completed successfully.");
        } catch (Exception e) {
            log.error("Error during scheduled cleanup: {}", e.getMessage());
        }
    }
}