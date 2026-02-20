package com.thughari.jobtrackerpro.scheduler;

import com.thughari.jobtrackerpro.service.JobService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class JobSchedulerTest {

    @Test
    void runStaleJobCleanupInvokesService() {
        JobService jobService = mock(JobService.class);
        JobScheduler scheduler = new JobScheduler(jobService);

        scheduler.runStaleJobCleanup();

        verify(jobService).cleanupStaleApplications();
    }

    @Test
    void runStaleJobCleanupHandlesServiceException() {
        JobService jobService = mock(JobService.class);
        doThrow(new RuntimeException("boom")).when(jobService).cleanupStaleApplications();
        JobScheduler scheduler = new JobScheduler(jobService);

        scheduler.runStaleJobCleanup();

        verify(jobService).cleanupStaleApplications();
    }
}
