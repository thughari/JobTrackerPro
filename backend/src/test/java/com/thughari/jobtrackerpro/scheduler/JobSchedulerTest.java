package com.thughari.jobtrackerpro.scheduler;

import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.PasswordResetTokenRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.repo.VerificationTokenRepository;
import com.thughari.jobtrackerpro.service.GmailIntegrationService;
import com.thughari.jobtrackerpro.service.JobService;
import com.thughari.jobtrackerpro.service.UserDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobSchedulerTest {

    @Mock private JobService jobService;
    @Mock private UserRepository userRepository;
    @Mock private GmailIntegrationService gmailIntegrationService;
    @Mock private UserDeletionService userDeletionService;
    @Mock private PasswordResetTokenRepository passwordTokenRepo;
    @Mock private VerificationTokenRepository verificationTokenRepo;

    @InjectMocks
    private JobScheduler scheduler;

    private final String VALID_SECRET = "test-secret";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "expectedCronSecret", VALID_SECRET);
    }

    @Test
    void runDailyMaintenance_UnauthorizedWhenSecretIsMissingOrInvalid() {
        assertThrows(ResponseStatusException.class, () -> scheduler.runDailyMaintenance(null));
        assertThrows(ResponseStatusException.class, () -> scheduler.runDailyMaintenance("wrong-secret"));
    }

    @Test
    void runDailyMaintenance_ExecutesAllJobsSequentially() {
        // Setup users for Gmail watch renewal
        User user1 = new User();
        user1.setEmail("user1@test.com");
        when(userRepository.findByGmailConnectedTrue()).thenReturn(List.of(user1));

        // Act
        scheduler.runDailyMaintenance(VALID_SECRET);

        // Assert Step 1: Stale Job Cleanup
        verify(jobService, times(1)).cleanupStaleApplications();

        // Assert Step 2: Gmail Sync
        verify(gmailIntegrationService, times(1)).renewWatch(user1);

        // Assert Step 3: System Cleanup
        verify(passwordTokenRepo, times(1)).deleteAllExpired(any());
        verify(verificationTokenRepo, times(1)).deleteAllExpired(any());
        verify(userRepository, times(1)).deleteUnverifiedUsers(any());

        // Assert Step 4: Scheduled Deletions
        verify(userRepository, times(1)).findAllByPendingDeletionTrueAndDeletionRequestedAtBefore(any());
    }

    @Test
    void runDailyMaintenance_HandlesPartialFailuresAcrossJobs() {
        // Verification that an exception in one step doesn't crash the entire maintenance run
        
        // Step 1 throws error
        doThrow(new RuntimeException("DB Timeout")).when(jobService).cleanupStaleApplications();
        
        // Step 2 mock setup
        User user1 = new User();
        user1.setEmail("fail@test.com");
        User user2 = new User();
        user2.setEmail("success@test.com");
        when(userRepository.findByGmailConnectedTrue()).thenReturn(List.of(user1, user2));
        doThrow(new RuntimeException("Token Revoked")).when(gmailIntegrationService).renewWatch(user1);

        // Act
        scheduler.runDailyMaintenance(VALID_SECRET);

        // Assert: Step 1 executed and failed
        verify(jobService).cleanupStaleApplications();
        
        // Assert: Step 2 still executed, and even though user1 failed, user2 MUST still be processed
        verify(gmailIntegrationService).renewWatch(user1);
        verify(gmailIntegrationService).renewWatch(user2);
        
        // Assert: Subsequent steps still run
        verify(passwordTokenRepo, times(1)).deleteAllExpired(any());
    }
}