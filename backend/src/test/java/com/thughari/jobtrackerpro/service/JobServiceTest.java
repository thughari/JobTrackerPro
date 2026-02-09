package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.DashboardResponse;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.entity.Job;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.repo.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    private static final String EMAIL = "user@example.com";

    private Job baseJob(String company, String role, String status, int stage) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setUserEmail(EMAIL);
        job.setCompany(company);
        job.setRole(role);
        job.setStatus(status);
        job.setStage(stage);
        job.setStageStatus("active");
        job.setAppliedDate(LocalDateTime.of(2025, 1, 10, 10, 0));
        job.setUpdatedAt(LocalDateTime.of(2025, 1, 12, 10, 0));
        job.setLocation("Remote");
        return job;
    }


    @Test
    void createJob_setsDefaultsAndEmail() {
        JobDTO dto = new JobDTO();
        dto.setCompany("Acme");
        dto.setRole("Engineer");
        dto.setStatus("Applied");

        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobDTO created = jobService.createJob(dto, EMAIL);

        assertEquals("Acme", created.getCompany());

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        Job saved = captor.getValue();

        assertEquals(EMAIL, saved.getUserEmail());
        assertEquals("Remote", saved.getLocation());
        assertEquals(1, saved.getStage());
        assertEquals("active", saved.getStageStatus());
        assertNotNull(saved.getAppliedDate());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void updateJob_preservesImmutableFieldsAndUpdatesMutableOnes() {
        UUID id = UUID.randomUUID();
        LocalDateTime appliedDate = LocalDateTime.of(2025, 1, 1, 9, 0);

        Job existing = baseJob("OldCo", "Developer", "Applied", 1);
        existing.setId(id);
        existing.setAppliedDate(appliedDate);

        when(jobRepository.findById(id)).thenReturn(Optional.of(existing));

        JobDTO update = new JobDTO();
        update.setCompany("NewCo");
        update.setRole("Senior Developer");
        update.setAppliedDate(LocalDateTime.of(2026, 1, 1, 0, 0));

        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobDTO result = jobService.updateJob(id, update, EMAIL);

        assertEquals("NewCo", result.getCompany());
        assertEquals("Senior Developer", result.getRole());
        assertEquals(appliedDate, result.getAppliedDate());

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        Job saved = captor.getValue();
        assertEquals(appliedDate, saved.getAppliedDate());
        assertEquals(EMAIL, saved.getUserEmail());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void updateJob_throwsWhenUnauthorized() {
        UUID id = UUID.randomUUID();
        Job existing = baseJob("Acme", "Engineer", "Applied", 1);
        existing.setUserEmail("other@example.com");

        when(jobRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThrows(ResourceNotFoundException.class, () -> jobService.updateJob(id, new JobDTO(), EMAIL));
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void createOrUpdateJob_updatesBestActiveMatch() {
        Job matchA = baseJob("Acme", "Backend Engineer", "Applied", 2);
        matchA.setNotes("Existing note");
        matchA.setUrl("https://existing.example.com");

        Job matchB = baseJob("Acme Corp", "Data Analyst", "Applied", 2);

        when(jobRepository.findByUserEmailOrderByUpdatedAtDesc(EMAIL)).thenReturn(List.of(matchA, matchB));

        JobDTO incoming = new JobDTO();
        incoming.setCompany("Acme");
        incoming.setRole("Senior Backend Engineer");
        incoming.setStatus("Interview Scheduled");
        incoming.setStage(3);
        incoming.setStageStatus("active");
        incoming.setNotes("Recruiter email update");
        incoming.setUrl("https://new-link.example.com");

        jobService.createOrUpdateJob(incoming, EMAIL);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(captor.capture());
        Job saved = captor.getValue();

        assertEquals("Interview Scheduled", saved.getStatus());
        assertEquals(3, saved.getStage());
        assertTrue(saved.getNotes().contains("Recruiter email update"));
        assertEquals("https://existing.example.com", saved.getUrl());
    }

    @Test
    void getDashboardData_calculatesSummaryAndCharts() {
        Job applied = baseJob("A", "Engineer", "Applied", 1);
        applied.setAppliedDate(LocalDateTime.of(2025, 1, 5, 9, 0));

        Job interview = baseJob("B", "Engineer", "Interview Scheduled", 3);
        interview.setAppliedDate(LocalDateTime.of(2025, 2, 5, 9, 0));

        Job offer = baseJob("C", "Engineer", "Offer Received", 4);
        offer.setAppliedDate(LocalDateTime.of(2025, 2, 20, 9, 0));

        when(jobRepository.findByUserEmailOrderByUpdatedAtDesc(EMAIL)).thenReturn(List.of(applied, interview, offer));

        DashboardResponse response = jobService.getDashboardData(EMAIL);

        assertEquals(3, response.getStats().getTotalApplications());
        assertEquals(2, response.getStats().getInterviews());
        assertEquals(1, response.getStats().getActiveInterviews());
        assertEquals(1, response.getStats().getOffers());
        assertEquals(2, response.getMonthlyChart().size());
        assertEquals(2, response.getInterviewChart().size());
    }
}
