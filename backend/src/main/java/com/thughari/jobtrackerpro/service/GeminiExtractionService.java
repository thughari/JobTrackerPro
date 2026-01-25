package com.thughari.jobtrackerpro.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thughari.jobtrackerpro.dto.JobDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiExtractionService {

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

    public JobDTO extractJobFromEmail(String from, String subject, String body) {
        String prompt = buildPrompt(from, subject, body);

        try {
            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                	Map.of("role", "user"),
                    Map.of("parts", List.of(
                        Map.of("text", prompt)
                    ))
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
            Act as a strict Global Data Extraction System.
            
            ### TASK
            Analyze the email. It may be in any language (Dutch, German, etc.). 
            Identify if it relates to a Job Application, Interview, or Recruiter Outreach.
            Analyze the email below. Determine if it is related to a Job Opportunity.
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
            2. **ROLE**: Extract the specific job title. 
               - If it is a Walk-In drive listing multiple roles, pick the one most relevant to "Java" or "Software Engineer", or default to "Software Engineer".
            3. **STATUS**: Map to one of these exact statuses:
               - "Applied" (Use this for Walk-in invites, Recruiter outreach, or Sent emails)
               - "Shortlisted"
               - "Interview Scheduled"
               - "Offer Received"
               - "Rejected"
            4. **NOTES**: A 1-sentence summary (e.g., "Walk-in drive invitation", "Replied to recruiter").
            5. **LOCATION**: Extract City/Country if found, otherwise default to "Remote" but never make it null.
            6. **URL**: Hunt for the primary call-to-action link. 
               - Look for URLs immediately following words like "Apply", "View Job", "Click here", or "Check status".
               - If multiple links exist, prioritize ones containing "careers", "jobs", "apply", or "lever.co", "greenhouse.io", "myworkday".
               - Return the full raw URL string.
               - If no URL is found, return the company's website mentioned in the email.
            7. **SALARY**: Extract numbers if present (e.g. 120k), else 0.0.

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
        if (status.contains("Shortlisted")) return 2;
        return 1;
    }
}