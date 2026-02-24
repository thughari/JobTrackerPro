package com.thughari.jobtrackerpro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.interfaces.GeminiService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(name = "app.gemini.enabled", havingValue = "true")
public class GeminiExtractionService implements GeminiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    public GeminiExtractionService() {
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public JobDTO extractJobFromEmail(String from, String subject, String body) {
        String prompt = buildPrompt(from, subject, body);

        try {
        	Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                        Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", prompt))
                        )
                    )
                );

            String response = restClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseGeminiResponse(response);

        } catch (Exception e) {
            log.error("AI Extraction failed or timed out", e);
            return null; 
        }
    }
    
    @Override
    public List<JobDTO> extractJobsFromBatch(List<EmailBatchItem> items) {
        if (items == null || items.isEmpty()) return List.of();

        String prompt = buildBatchPrompt(items);

        try {
            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))
                )
            );

            String response = restClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseBulkGeminiResponse(response);

        } catch (Exception e) {
            log.error("Bulk AI Extraction failed", e);
            return List.of();
        }
    }
    
    private String buildBatchPrompt(List<EmailBatchItem> items) {
        StringBuilder emailListBuilder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            EmailBatchItem item = items.get(i);
            String safeBody = (item.body() != null) ? (item.body().length() > 6000 ? item.body().substring(0, 6000) : item.body()) : "";
            
            emailListBuilder.append("""
                --- EMAIL INDEX: %d ---
                FROM: %s
                SUBJECT: %s
                BODY: %s
                -----------------------
                """.formatted(i, item.from(), item.subject(), safeBody));
        }

        return """
            Act as a strict Global Data Extraction System for a Job Application Tracker.

            ### TASK
            Analyze the list of emails provided below. Determine whether each email is related to a job opportunity, hiring process, or recruiter communication.
            If the email mentions a company and a job role in a hiring context, it MUST be treated as job-related.
            Only exclude an email from the output array if it is clearly commercial spam, a receipt, or completely unrelated to jobs/careers.

            **CRITICAL RULE:** Exclude strictly commercial spam, receipts, or unrelated content.
            
            ### LIST OF EMAILS TO ANALYZE
            %s

            ### EXTRACTION RULES (Apply to each email index)
            1. **COMPANY**: Identify the hiring company. 
                - If multiple companies are mentioned, select the one most directly related to the hiring context.
                - Fallback: Extraction from the sender's email domain (e.g. careers@stripe.com -> Stripe).
                - Ignore generic providers (gmail, yahoo, etc.).
                - Default: "Unknown Company".

            2. **ROLE**: Extract specific job title. Default to "Software Engineer" if no role is clear.
            3. **STATUS**: Map to: "Applied", "Shortlisted", "Interview Scheduled", "Offer Received", or "Rejected".
            4. **NOTES**: A 1-sentence summary.
            5. **LOCATION**: Extract City/Country if found, otherwise default to "Remote".

            6. **URL**: Hunt for the link using this strict priority:
                - Priority 1: Direct Job View link (look for long URLs containing "/jobs/view/" or "/comm/jobs/").
                - Priority 2: Primary call-to-action (Apply, View Job, Check status) or links containing "lever.co", "greenhouse.io", "myworkday".
                - Priority 3: Company Careers page URL.
                - Priority 4: Main company website URL.
                - Extraction: Return the full raw URL string (if between <> brackets, remove the brackets).

            7. **SALARY**: Extract salary numbers if present, otherwise 0.0.

            ### MULTILINGUAL RULE
            Translate all extracted values into English.

            ### OUTPUT FORMAT
            Return ONLY a raw JSON array of objects (no markdown, no explanations). 
            Return an empty array [] if no job-related emails are found.
            
            Example Output:
            [
              {
                "company": "String",
                "role": "String",
                "location": "String",
                "status": "String",
                "url": "String",
                "salaryMin": 0.0,
                "salaryMax": 0.0,
                "notes": "String"
              }
            ]
            """.formatted(emailListBuilder.toString());
    }

    private List<JobDTO> parseBulkGeminiResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            
            if (candidates.isMissingNode() || candidates.isEmpty()) return List.of();

            String contentText = candidates.get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            // Clean up any potential markdown garbage if the AI ignores the prompt instructions
            contentText = contentText.replaceAll("```json", "").replaceAll("```", "").trim();

            if (contentText.equals("[]") || contentText.equalsIgnoreCase("null")) {
                return List.of();
            }

            // Map the JSON array directly to a List of DTOs
            List<JobDTO> jobs = objectMapper.readValue(contentText, new TypeReference<List<JobDTO>>() {});
            
            LocalDateTime now = LocalDateTime.now();
            jobs.forEach(job -> {
                job.setAppliedDate(now); 
                job.setUpdatedAt(now);
                job.setStage(mapStatusToStage(job.getStatus()));
                
                if ("Rejected".equalsIgnoreCase(job.getStatus())) {
                    job.setStageStatus("failed");
                } else if ("Offer Received".equalsIgnoreCase(job.getStatus())) {
                    job.setStageStatus("passed");
                } else {
                    job.setStageStatus("active");
                }
            });
            
            return jobs;

        } catch (Exception e) {
            log.error("Failed to parse Bulk AI response: {}", rawResponse);
            return List.of();
        }
    }

    private String buildPrompt(String from, String subject, String body) {
        String safeBody = (body != null) ? (body.length() > 8000 ? body.substring(0, 8000 ) : body) : "";

        return """
            Act as a strict Global Data Extraction System for a Job Application Tracker.

            ### TASK
            Analyze the email content below.
            Determine whether the email is related to a specific job opportunity, hiring process, or recruiter communication.

            If the email mentions a company and a job role in a hiring context, it MUST be treated as job-related.
            Only return `null` if the email is clearly commercial spam, a receipt, or completely unrelated to jobs or careers.

            Valid categories include:
            1. Application Confirmations (ATS).
            2. Interview Invites.
            3. Offers/Rejections.
            4. **Recruiter Outreach / Walk-In Drive Invitations**.
            5. **User sent emails** (e.g. user replying to a recruiter about a role).
            
            **CRITICAL RULE:** 
            Only return `null` if the email is strictly commercial spam (selling products), receipts, or completely unrelated to careers.
            
            ### EMAIL CONTENT
            FROM: %s
            SUBJECT: %s
            BODY: %s

            ### EXTRACTION RULES
            1. **COMPANY**: Identify the hiring company. 
                - If multiple companies are mentioned, select the one most directly related to the role, interview, assessment, offer, or rejection.
                - If no company name is explicitly mentioned in the email content:
                    - Attempt a fallback extraction from the sender's email domain.
                    - Derive the company name from the domain (for example, careers@stripe.com -> Stripe).
                    - Ignore generic email providers such as gmail.com, yahoo.com, outlook.com, and similar.
                - If both content-based and domain-based extraction fail:
                    - Never return null
                    - Return "Unknown Company" as the default value.

            2. **ROLE**: Extract the specific job title. 
                - If it is a Walk-In drive listing multiple roles, pick the one most relevant to "Java" or "Software Engineer", or default to "Software Engineer".
                - Default to "Software Engineer" only if no role is clear.
            3. **STATUS**: Map to one of these exact statuses:
                - "Applied" (Use this for Walk-in invites, Recruiter outreach, or Sent emails)
                - "Shortlisted" (Use this for 'Next steps', Coding Tests, Exams, or HackerRank invites or similar)
                - "Interview Scheduled" (for any interview invites)
                - "Offer Received"
                - "Rejected"
            4. **NOTES**: A 1-sentence summary (e.g., "Walk-in drive invitation", "Replied to recruiter").
            5. **LOCATION**: Extract City/Country if found, otherwise default to "Remote" but never make it null.
                - If not found, default to "Remote".

            6. **URL**: Hunt for the primary call-to-action link. 
                - Look for URLs immediately following words like "Apply", "View Job", "Click here", or "Check status".
                - If multiple links exist, prioritize ones containing "careers", "jobs", "apply", or "lever.co", "greenhouse.io", "myworkday".
                - Return the full raw URL string.
                - If no URL is found, return the company's website mentioned in the email.
            7. **SALARY**: Extract salary numbers if present.
                - Otherwise return 0.0 for both salaryMin and salaryMax.

            ### MULTILINGUAL RULE
            If the email is in Dutch, French, or any other language, you MUST process it normally but provide the JSON output values in English so the user can understand their dashboard.

            ### OUTPUT FORMAT
            Return ONLY raw JSON (no markdown blocks, no explanations):
            {
              "company": "String",
              "role": "String",
              "location": "String",
              "status": "String",
              "url": "String",
              "salaryMin": 0.0,
              "salaryMax": 0.0,
              "notes": "String"
            }
            OR just: null
            """.formatted(from, subject, safeBody);
    }

    private JobDTO parseGeminiResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            
            if (candidates.isMissingNode() || candidates.isEmpty()) {
                return null;
            }

            String contentText = candidates.get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            contentText = contentText.replaceAll("```json", "").replaceAll("```", "").trim();

            if (contentText.equalsIgnoreCase("null")) {
                log.info("AI determined this email is NOT a job application.");
                return null;
            }

            JobDTO job = objectMapper.readValue(contentText, JobDTO.class);
            
            LocalDateTime now = LocalDateTime.now();
            job.setAppliedDate(now); 
            job.setUpdatedAt(now);
            
            job.setStage(mapStatusToStage(job.getStatus()));
            
            if ("Rejected".equalsIgnoreCase(job.getStatus())) {
                job.setStageStatus("failed");
            } else if ("Offer Received".equalsIgnoreCase(job.getStatus())) {
                job.setStageStatus("passed");
            } else {
                job.setStageStatus("active");
            }
            
            return job;

        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", rawResponse);
            return null;
        }
    }

    private Integer mapStatusToStage(String status) {
        if (status == null) return 1;
        if (status.contains("Offer")) return 4;
        if (status.contains("Interview")) return 3;
        if (status.contains("Shortlisted") || status.contains("exam") || status.contains("test") || status.contains("hackerrank")) return 2;

        return 1;
    }
}