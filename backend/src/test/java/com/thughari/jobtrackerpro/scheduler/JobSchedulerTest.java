package com.thughari.jobtrackerpro.scheduler;

import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.service.GmailIntegrationService;
import com.thughari.jobtrackerpro.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobSchedulerTest {

    @Mock private JobService jobService;
    @Mock private UserRepository userRepository;
    @Mock private GmailIntegrationService gmailIntegrationService;

    @InjectMocks
    private JobScheduler scheduler;

    @Test
    void runStaleJobCleanup_InvokesService() {
        scheduler.runStaleJobCleanup();
        verify(jobService, times(1)).cleanupStaleApplications();
    }

    @Test
    void runStaleJobCleanup_HandlesServiceException() {
        // Verification that an exception in the service doesn't propagate and crash the scheduler thread
        doThrow(new RuntimeException("DB Timeout")).when(jobService).cleanupStaleApplications();
        
        scheduler.runStaleJobCleanup();

        verify(jobService).cleanupStaleApplications();
    }

    @Test
    void renewGmailWatches_ProcessesAllConnectedUsers() {
        // Setup: Mocking connected users
        User user1 = new User();
        user1.setEmail("user1@test.com");
        User user2 = new User();
        user2.setEmail("user2@test.com");

        when(userRepository.findByGmailConnectedTrue()).thenReturn(List.of(user1, user2));

        // Act
        scheduler.renewGmailWatches();

        // Assert: High Performance check
        // Verify that the integration service was called for every user returned by the repo
        verify(gmailIntegrationService, times(1)).renewWatch(user1);
        verify(gmailIntegrationService, times(1)).renewWatch(user2);
    }

    @Test
    void renewGmailWatches_HandlesPartialFailures() {
        // Setup: One user succeeds, one fails
        User user1 = new User();
        user1.setEmail("fail@test.com");
        User user2 = new User();
        user2.setEmail("success@test.com");

        when(userRepository.findByGmailConnectedTrue()).thenReturn(List.of(user1, user2));
        
        // Mocking an error for the first user
        doThrow(new RuntimeException("Token Revoked")).when(gmailIntegrationService).renewWatch(user1);

        // Act
        scheduler.renewGmailWatches();

        // Assert: Robustness check
        // Even though user1 failed, user2 MUST still be processed (Fault Tolerance)
        verify(gmailIntegrationService).renewWatch(user1);
        verify(gmailIntegrationService).renewWatch(user2);
    }

    @Test
    void renewGmailWatches_SkipsIfNoUsersConnected() {
        when(userRepository.findByGmailConnectedTrue()).thenReturn(List.of());

        scheduler.renewGmailWatches();

        verify(gmailIntegrationService, never()).renewWatch(any());
    }
}