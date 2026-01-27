package com.thughari.jobtrackerpro.service.mock;

import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/*
 * This is a mock service for Gemini AI extraction for applications
 */

@Service
@ConditionalOnProperty(name = "app.gemini.enabled", havingValue = "false", matchIfMissing = true)
public class MockGeminiService implements GeminiService {

    @Override
    public JobDTO extractJobFromEmail(String from, String subject, String body) {
        JobDTO mockJob = new JobDTO();
        
        String company = (from != null && from.contains("@")) ? 
                         from.split("@")[1].split("\\.")[0] : "Mock Company";
        
        mockJob.setCompany(company);
        mockJob.setRole(subject != null ? subject : "Software Engineer");
        mockJob.setLocation("Remote (Mock)");
        mockJob.setStatus("Applied");
        mockJob.setStage(1);
        mockJob.setStageStatus("active");
        mockJob.setSalaryMin(50000.0);
        mockJob.setSalaryMax(80000.0);
        mockJob.setUrl("https://example.com/mock-job");
        mockJob.setNotes("Ingested via Mock Gemini Service. No API key was used.");
        
        LocalDateTime now = LocalDateTime.now();
        mockJob.setAppliedDate(now);
        mockJob.setUpdatedAt(now);
        
        return mockJob;
    }

}