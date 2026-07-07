package com.thughari.jobtrackerpro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.interfaces.AiExtractionService;
import com.thughari.jobtrackerpro.exception.AiQuotaExceededException;
import com.thughari.jobtrackerpro.util.UrlParser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(name = "openrouter.api.enabled", havingValue = "true")
public class OpenRouterExtractionService implements AiExtractionService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Value("${openrouter.api.url}")
    private String apiUrl;

    @Value("${openrouter.api.model:meta-llama/llama-3.1-8b-instruct}")
    private String apiModel;

    public OpenRouterExtractionService() {
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public JobDTO extractJobFromEmail(String from, String subject, String body) {
        String prompt = buildPrompt(from, subject, body);

        try {
            Map<String, Object> requestBody = Map.of(
                "model", apiModel,
                "messages", List.of(
                    Map.of(
                        "role", "user",
                        "content", prompt
                    )
                ),
                "max_tokens", 800
            );

            String response = restClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseOpenAiResponse(response);

        } catch (HttpClientErrorException e) {
            log.error("OpenRouter API error: {}", e.getResponseBodyAsString());
            throw new AiQuotaExceededException("OPENROUTER_API_ERROR", e);
        } catch (Exception e) {
            log.error("OpenRouter AI Extraction failed or timed out", e);
            return null;
        }
    }

    @Override
    public List<JobDTO> extractJobsFromBatch(List<EmailBatchItem> items) {
        if (items == null || items.isEmpty()) return List.of();

        String prompt = buildBatchPrompt(items);

        try {
            Map<String, Object> requestBody = Map.of(
                "model", apiModel,
                "messages", List.of(
                    Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 2500
            );

            String response = restClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseBulkOpenAiResponse(response);

        } catch (HttpClientErrorException e) {
            log.error("OpenRouter API error: {}", e.getResponseBodyAsString());
            throw new AiQuotaExceededException("OPENROUTER_API_ERROR", e);
        } catch (Exception e) {
            log.error("OpenRouter Bulk AI Extraction failed", e);
            return List.of();
        }
    }

    private JobDTO parseOpenAiResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choices = root.path("choices");

            if (choices.isMissingNode() || choices.isEmpty()) {
                return null;
            }

            String contentText = choices.get(0).path("message").path("content").asText();
            contentText = contentText.replaceAll("```json", "").replaceAll("```", "").trim();

            if (contentText.equalsIgnoreCase("null")) {
                log.info("OpenRouter AI determined this email is NOT a job application.");
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
            log.error("Failed to parse OpenRouter AI response: {}", rawResponse);
            return null;
        }
    }

    private List<JobDTO> parseBulkOpenAiResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choices = root.path("choices");

            if (choices.isMissingNode() || choices.isEmpty()) return List.of();

            String contentText = choices.get(0).path("message").path("content").asText();

            // Extract just the JSON array between first '[' and last ']'
            int start = contentText.indexOf('[');
            int end = contentText.lastIndexOf(']');
            if (start == -1 || end == -1 || end <= start) {
                log.warn("OpenRouter bulk response has no JSON array: {}", contentText.substring(0, Math.min(200, contentText.length())));
                return List.of();
            }
            contentText = contentText.substring(start, end + 1);

            // Sanitize malformed JSON from LLM (e.g. missing opening quotes on keys)
            contentText = contentText.replaceAll(",(?:\\s*)([a-zA-Z_]+)\"\\s*:", ",\"$1\":");
            contentText = contentText.replaceAll("\\{(?:\\s*)([a-zA-Z_]+)\"\\s*:", "{\"$1\":");

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
            log.error("Failed to parse OpenRouter Bulk AI response: {}", rawResponse);
            return List.of();
        }
    }

    private String buildPrompt(String from, String subject, String body) {
        String safeBody = (body != null) ? (body.length() > 1200 ? body.substring(0, 1200) : body) : "";

        return """
            Act as a strict ATS Data Extractor.
            TASK: Analyze email. Return JSON job object if job-related. Else return null. Skip spam/receipts.
            
            RULES:
            1. COMPANY: Hiring company. Fallback to email domain. Ignore generic domains. Default: "Unknown Company".
            2. ROLE: Job title. Default: "Software Engineer".
            3. STATUS: "Applied", "Shortlisted", "Interview Scheduled", "Offer Received", or "Rejected".
            4. NOTES: 1-sentence summary.
            5. LOCATION: City/Country. Default: "Remote".
            6. URL: Best call-to-action link.
            7. SALARY: Extract if present, else 0.0.
            
            MULTILINGUAL: Output values in English.
            
            EMAIL:
            From: %s
            Subject: %s
            Body: %s
            
            OUTPUT: Raw JSON (no markdown) or null.
            """.formatted(from, subject, safeBody);
    }

    private String buildBatchPrompt(List<EmailBatchItem> items) {
        StringBuilder emailListBuilder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            EmailBatchItem item = items.get(i);
            String rawBody = item.body() == null ? "" : item.body();

            String trimmed = UrlParser.trimNoise(rawBody);
            String safeBody = trimmed.length() > 1200 ? trimmed.substring(0, 1200) : trimmed;
            List<String> urls = UrlParser.extractAndCleanUrls(rawBody);

            emailListBuilder.append("""
            		[EMAIL %d]
            		From: %s
            		Reply-To: %s
            		Subject: %s
            		Body: %s
            		URLs:
            		%s
            		""".formatted(
                    i, item.from(), item.replyTo(), item.subject(), safeBody, buildUrlIndexList(urls)
            ));
        }

        return """
        Act as a strict ATS Data Extractor.
        TASK: Analyze emails. Return JSON array of job objects for job-related emails only. Skip spam/receipts.
        
        RULES:
        1. COMPANY: Hiring company. Fallback to email domain. Ignore generic domains. Default: "Unknown Company".
        2. ROLE: Job title. Default: "Software Engineer".
        3. STATUS: "Applied", "Shortlisted", "Interview Scheduled", "Offer Received", or "Rejected".
        4. NOTES: 1-sentence summary.
        5. LOCATION: City/Country. Default: "Remote".
        6. URL: Best call-to-action link from URLs list, else company website.
        7. SALARY: Extract if present, else 0.0.
        8. INPUT INDEX: Must match [EMAIL X] index.
        
        MULTILINGUAL: Output values in English.
        
        EMAILS:
        %s
        
        OUTPUT FORMAT: Raw JSON array only (no markdown).
        """.formatted(emailListBuilder.toString());
    }

    private String buildUrlIndexList(List<String> urls) {
        if (urls.isEmpty()) return "None\\n";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < urls.size(); i++) {
            sb.append(i).append(": ").append(urls.get(i)).append("\\n");
        }
        return sb.toString();
    }

    private Integer mapStatusToStage(String status) {
        if (status == null) return 1;
        if (status.contains("Offer")) return 4;
        if (status.contains("Interview")) return 3;
        if (status.contains("Shortlisted") || status.contains("exam") || status.contains("test") || status.contains("hackerrank")) return 2;

        return 1;
    }
}
