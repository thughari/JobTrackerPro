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
            Act as a strict Global Data Extraction System for a Professional Job Tracker.
            
            ### TASK
            Analyze the email content provided. Your goal is to determine if the email represents a specific, personal step in a hiring process.

            ### VALID CATEGORIES
            1. Official Application Confirmations (from an ATS like Workday, Greenhouse, Linkedin, etc.).
            2. Personal Interview Invitations or Schedule Updates.
            3. Job Offers or Rejection notices.
            4. Recruiter Outreach for a specific role (Direct LinkedIn messages or emails).
            5. Walk-In Drive Invitations for specific positions.
            6. User-sent replies to recruiters or companies regarding a job.

            ### CRITICAL EXCLUSION RULES (Return `null` for these)
            Return `null` if the email is any of the following:
            - Coding Contests or Challenges (e.g., LeetCode Weekly Contest, CodeChef).
            - Hackathon Registrations or Updates (e.g., Hack2Skill, Devpost) UNLESS they explicitly mention a job interview/offer.
            - Marketing, Newsletters, or Course Recommendations (e.g., Udemy, Coursera).
            - General Job Alerts/Digest emails (e.g., "10 new jobs for you").
            - Commercial receipts, social media notifications, or personal spam.

            ### EMAIL CONTENT
            FROM: %s
            SUBJECT: %s
            BODY: %s

            ### EXTRACTION RULES
            1. **COMPANY**: Identify the hiring company. Remove "Team", "Careers", or "Recruitment" from the name (e.g., "Google" not "Google Careers").
            2. **ROLE**: Extract the specific job title (e.g., "Software Engineer"). 
               - If it's a Walk-In drive with multiple roles, pick the most technical one.
               - Default to "Software Engineer" if vague.
            3. **STATUS**: Map strictly to one of these:
               - "Applied": Application confirmations, walk-in invites, or recruiter outreach.
               - "Shortlisted": Coding test invites (HackerRank/Codility), assignment requests, or "next steps" emails.
               - "Interview Scheduled": Real-time interview invites (Phone, Video, On-site).
               - "Offer Received": Official job offers.
               - "Rejected": Rejection emails.
            4. **NOTES**: A concise 1-sentence summary in English (e.g., "Received technical coding assessment").
            5. **LOCATION**: Extract City/Country. Default to "Remote" if not found.
            6. **URL**: Hunt for the primary call-to-action link (Apply, View Job, Test Link). 
               - Return ONLY the raw URL string.
               - If no link is found, return null.
            7. **SALARY**: Extract numbers if present, else 0.0.

            ### MULTILINGUAL RULE
            Process any language, but the output JSON values (Company, Role, Location, Notes) MUST be in English.

            ### OUTPUT FORMAT
            Return ONLY raw JSON. No markdown, no conversational filler.
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