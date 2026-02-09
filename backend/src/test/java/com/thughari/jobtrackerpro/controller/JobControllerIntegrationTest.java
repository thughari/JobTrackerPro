package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.DashboardResponse;
import com.thughari.jobtrackerpro.dto.DashboardStatsDTO;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
@AutoConfigureMockMvc(addFilters = false)
class JobControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    private void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(email, null)
        );
    }

    @Test
    void getAllJobs_returnsPagedResponseForAuthenticatedUser() throws Exception {
        authenticate("USER@Example.com");

        JobDTO job = new JobDTO();
        job.setId(UUID.randomUUID());
        job.setCompany("Acme");
        job.setRole("Engineer");

        when(jobService.getAllJobsPaged(eq("user@example.com"), eq(0), eq(10), eq("updatedAt"), eq("desc"), eq(""), eq("All Statuses")))
                .thenReturn(new PageImpl<>(List.of(job)));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].company").value("Acme"))
                .andExpect(jsonPath("$.content[0].role").value("Engineer"));

        verify(jobService).getAllJobsPaged("user@example.com", 0, 10, "updatedAt", "desc", "", "All Statuses");
    }

    @Test
    void getDashboard_returnsDashboardPayload() throws Exception {
        authenticate("user@example.com");

        DashboardResponse response = new DashboardResponse();
        response.setStats(new DashboardStatsDTO(5, 3, 2, 1, 1));

        when(jobService.getDashboardData("user@example.com")).thenReturn(response);

        mockMvc.perform(get("/api/jobs/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalApplications").value(5))
                .andExpect(jsonPath("$.stats.activePipeline").value(3));
    }

    @Test
    void deleteJob_returnsNoContent() throws Exception {
        authenticate("user@example.com");
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/jobs/{id}", id))
                .andExpect(status().isNoContent());

        verify(jobService).deleteJob(id, "user@example.com");
    }
}
