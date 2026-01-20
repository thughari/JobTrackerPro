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
        String safeBody = (body != null) ? (body.length() > 4000 ? body.substring(0, 4000) : body) : "";

        return """
            Act as a strict Data Extraction System.
            
            ### TASK
            Analyze the email below. Determine if it is a **Job Application Update** (e.g., Application Received, Interview Invite, Rejection, Offer).
            
            **CRITICAL RULE:** 
            If this email is SPAM, a Newsletter, a Receipt, or NOT related to a specific job application, return the JSON literal: `null`
            
            ### EMAIL CONTENT
            FROM: %s
            SUBJECT: %s
            BODY: %s

            ### EXTRACTION RULES (If it IS a job email)
            1. **COMPANY**: Identify the hiring company. Remove text like "Careers", "Talent Acquisition".
            2. **ROLE**: Extract the specific job title.
            3. **STATUS**: Map to one of these exact statuses:
               - "Applied" (Default/Receipt confirmation)
               - "Shortlisted" (Assessment invite, HR screen)
               - "Interview Scheduled" (Technical/Manager interview invites)
               - "Offer Received" (Offer letters)
               - "Rejected" (Rejection emails)
            4. **NOTES**: A 1-sentence summary of the update.
            5. **LOCATION**: Extract City/Country if found, otherwise "Remote".
            6. **URL**: Extract the "View Application" or "Job Posting" link if present.
            7. **SALARY**: Extract numbers if present (e.g. 120k), else 0.0.

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
            
            job.setDate(LocalDateTime.now());
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