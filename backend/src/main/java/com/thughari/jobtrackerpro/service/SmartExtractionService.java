package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import com.thughari.jobtrackerpro.util.TemplateParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Primary
@Slf4j
public class SmartExtractionService implements GeminiService {

    private final GeminiService activeService;

    public SmartExtractionService(List<GeminiService> services) {
        // Find the actual delegate service (GeminiExtractionService or MockGeminiService)
        this.activeService = services.stream()
                .filter(s -> s != this)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active GeminiService implementation found!"));
        log.info("SmartExtractionService initialized. Delegating non-template extractions to: {}", 
                 this.activeService.getClass().getSimpleName());
    }

    @Override
    public JobDTO extractJobFromEmail(String from, String subject, String body) {
        // log.info("[DEBUG] SmartExtractionService - From: '{}', Subject: '{}'", from, subject);
        // log.info("[DEBUG] Body: {}", body);

        try {
            JobDTO manualJob = TemplateParser.parse(from, subject, body);
            if (manualJob != null) {
                // log.info("Successfully matched and manually extracted job for company: {}", manualJob.getCompany());
                return manualJob;
            }
        } catch (Exception e) {
            log.warn("Manual parsing error, falling back to delegation: {}", e.getMessage());
        }

        log.info("Bypassing manual templates. Delegating extraction to: {}", activeService.getClass().getSimpleName());
        return activeService.extractJobFromEmail(from, subject, body);
    }

    @Override
    public List<JobDTO> extractJobsFromBatch(List<EmailBatchItem> items) {
        if (items == null || items.isEmpty()) return List.of();

        List<JobDTO> results = new ArrayList<>();
        List<EmailBatchItem> remainingItems = new ArrayList<>();
        List<Integer> originalIndexes = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            EmailBatchItem item = items.get(i);
            // log.info("[DEBUG] SmartExtractionService batch item {} - From: '{}', Subject: '{}'", i, item.from(), item.subject());
            // log.info("[DEBUG] Body: {}", item.body());

            JobDTO parsed = null;
            try {
                parsed = TemplateParser.parse(item.from(), item.subject(), item.body());
            } catch (Exception e) {
                log.warn("Manual parsing error: {}", e.getMessage());
            }

            if (parsed != null) {
                parsed.setInputIndex(i);
                // log.info("Successfully matched and manually extracted batch item {} for company: {}", i, parsed.getCompany());
                results.add(parsed);
            } else {
                remainingItems.add(item);
                originalIndexes.add(i);
            }
        }

        if (!remainingItems.isEmpty()) {
            log.info("Bypassing manual templates for {}/{} items. Delegating to: {}", 
                     remainingItems.size(), items.size(), activeService.getClass().getSimpleName());
            List<JobDTO> delegatedJobs = activeService.extractJobsFromBatch(remainingItems);
            
            for (JobDTO job : delegatedJobs) {
                if (job.getInputIndex() != null && job.getInputIndex() >= 0 && job.getInputIndex() < originalIndexes.size()) {
                    int originalIdx = originalIndexes.get(job.getInputIndex());
                    job.setInputIndex(originalIdx);
                }
                results.add(job);
            }
        }

        return results;
    }
}
