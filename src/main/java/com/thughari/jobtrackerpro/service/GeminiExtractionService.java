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

import java.time.LocalDate;
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
            log.error("AI Extraction failed", e);
            return createFallback(subject);
        }
    }

    private String buildPrompt(String from, String subject, String body) {
        String safeBody = body.length() > 4000 ? body.substring(0, 4000) : body;

        return """
            Act as a strict Data Extraction System. Extract job application metadata from the email below into a JSON object.
            
            CONTEXT: This email could be a NEW application OR an UPDATE (Interview invite, Rejection) for an existing one.

            ### EMAIL CONTENT
            FROM: %s
            SUBJECT: %s
            BODY: %s

            ### EXTRACTION RULES
            1. **COMPANY**: Identify the hiring company.
            2. **ROLE**: Extract job title.
            3. **STATUS**: Determine the NEW status implied by this email:
               - "Applied" (Confirmation of receipt)
               - "Shortlisted" (Screening, HR review)
               - "Interview Scheduled" (Invites, Scheduling requests)
               - "Offer Received" (Congratulations, Offer letters)
               - "Rejected" (Unfortunately, Not moving forward)
            4. **NOTES**: Summarize the update (e.g. "Received rejection email", "Invited to technical interview").
            5. **LOCATION**: Extract job location if mentioned, else set as "Remote".
            6. **URL**: If a job posting URL is present, extract it; else check for the careers page url; else check for any company related URL.
            7. **SALARY**: If salary details are mentioned, extract minimum and maximum; else set both as 0.0.

            ### OUTPUT FORMAT (JSON ONLY)
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
            """.formatted(from, subject, safeBody);
    }

    private JobDTO parseGeminiResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String contentText = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            contentText = contentText.replaceAll("```json", "").replaceAll("```", "").trim();

            JobDTO job = objectMapper.readValue(contentText, JobDTO.class);
            
            job.setDate(LocalDate.now());
            job.setStage(mapStatusToStage(job.getStatus()));
            job.setStageStatus(job.getStatus().equalsIgnoreCase("Rejected") ? "failed" : "active");
            
            if(job.getCompany() != null) {
                job.setCompany(job.getCompany().replace("Careers", "").trim());
            }

            return job;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI JSON", e);
        }
    }

    private JobDTO createFallback(String subject) {
        JobDTO fallback = new JobDTO();
        fallback.setCompany("Unknown Company");
        fallback.setRole(subject);
        fallback.setStatus("Applied");
        fallback.setDate(LocalDate.now());
        fallback.setStage(1);
        fallback.setStageStatus("active");
        return fallback;
    }

    private Integer mapStatusToStage(String status) {
        if (status.contains("Offer")) return 4;
        if (status.contains("Interview")) return 3;
        if (status.contains("Shortlisted")) return 2;
        return 1;
    }
}