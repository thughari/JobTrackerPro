package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.DashboardResponse;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.service.JobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobControllerIntegrationTest {

    @Mock
    private JobService jobService;

    @InjectMocks
    private JobController jobController;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(email, null));
    }

    @Test
    void getAllJobs_returnsPagedResponseForAuthenticatedUser() {
        authenticate("USER@Example.com");

        JobDTO job = new JobDTO();
        job.setId(UUID.randomUUID());
        job.setCompany("Acme");

        Page<JobDTO> page = new PageImpl<>(List.of(job));
        when(jobService.getAllJobsPaged("user@example.com", 0, 10, "updatedAt", "desc", "", "All Statuses"))
                .thenReturn(page);

        var response = jobController.getAllJobs(0, 10, "updatedAt", "desc", "", "All Statuses");

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        verify(jobService).getAllJobsPaged("user@example.com", 0, 10, "updatedAt", "desc", "", "All Statuses");
    }

    @Test
    void getDashboard_returnsDashboardPayload() {
        authenticate("user@example.com");

        DashboardResponse dashboardResponse = new DashboardResponse();
        when(jobService.getDashboardData("user@example.com")).thenReturn(dashboardResponse);

        var response = jobController.getDashboard();

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(dashboardResponse, response.getBody());
    }

    @Test
    void deleteJob_returnsNoContent() {
        authenticate("user@example.com");
        UUID id = UUID.randomUUID();

        var response = jobController.deleteJob(id);

        assertEquals(HttpStatusCode.valueOf(204), response.getStatusCode());
        verify(jobService).deleteJob(id, "user@example.com");
    }
}
