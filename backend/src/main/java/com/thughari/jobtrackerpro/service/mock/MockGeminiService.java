package com.thughari.jobtrackerpro.service.mock;

import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * This is a mock service for Gemini AI extraction for applications
 */

@Service
@ConditionalOnProperty(name = "app.gemini.enabled", havingValue = "false", matchIfMissing = true)
public class MockGeminiService implements GeminiService {
	
	private static final Pattern COMPANY_PATTERN = Pattern.compile("(?:to|at)\\s+([A-Z][A-Za-z0-9\\s]+)");

	@Override
    public JobDTO extractJobFromEmail(String from, String subject, String body) {
        if (isTrashEmail(subject, body)) return null;

        JobDTO mockJob = new JobDTO();
        
        String company = extractCompanyFromSubject(subject);
        if (company == null && from != null) {
            company = extractCompanyFromDomain(from);
        }

        mockJob.setCompany(company != null ? capitalize(company) : "Target Company");
        mockJob.setRole(subject != null ? subject : "Software Professional");
        mockJob.setLocation("Remote");
        mockJob.setStatus(determineStatus(subject));
        
        mockJob.setStage(1);
        mockJob.setStageStatus("active");
        mockJob.setSalaryMin(0.0);
        mockJob.setSalaryMax(0.0);
        mockJob.setUrl(""); 
        mockJob.setNotes("Ingested via Smarter Mock Service.");
        
        LocalDateTime now = LocalDateTime.now();
        mockJob.setAppliedDate(now);
        mockJob.setUpdatedAt(now);
        
        return mockJob;
    }
	
	@Override
	public List<JobDTO> extractJobsFromBatch(List<EmailBatchItem> items) {
	    if (items == null) return List.of();
	    
	    return items.stream()
	            .<JobDTO>map(item -> 
	                extractJobFromEmail(item.from(), item.subject(), item.body())
	            )
	            .filter(Objects::nonNull)
	            .toList();
	}
	
	private boolean isTrashEmail(String subject, String body) {
        if (subject == null) return true;
        String s = subject.toLowerCase();
        return s.contains("security alert") || 
               s.contains("sign-in") || 
               s.contains("verify your email") ||
               s.contains("password changed");
    }
	
	private String extractCompanyFromSubject(String subject) {
        if (subject == null) return null;
        Matcher matcher = COMPANY_PATTERN.matcher(subject);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

	private String extractCompanyFromDomain(String from) {
	    try {
	        String domain = from.split("@")[1].split("\\.")[0];
	        List<String> atsProviders = List.of("myworkday", "greenhouse", "lever", "smartrecruiters", "icims");
	        if (atsProviders.contains(domain.toLowerCase())) return null;
	        return domain;
	    } catch (Exception e) {
	        return null;
	    }
	}

	private String determineStatus(String subject) {
	    String s = subject.toLowerCase();
	    if (s.contains("interview") || s.contains("invitation")) return "Interview Scheduled";
	    if (s.contains("assessment") || s.contains("challenge")) return "Shortlisted";
	    return "Applied";
	}

}