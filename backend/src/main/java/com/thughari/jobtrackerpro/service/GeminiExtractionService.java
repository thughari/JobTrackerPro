package com.thughari.jobtrackerpro.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                - If multiple companies are mentioned, choose the one most relevant to the job opportunity.
                - If no comapny name mentioned, extract it from the sender's email domain. but avoid generic domains like gmail.com, yahoo.com. and never return null. instead use "Unknown Company".
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