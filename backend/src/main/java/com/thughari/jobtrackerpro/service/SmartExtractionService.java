package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.interfaces.AiExtractionService;
import com.thughari.jobtrackerpro.exception.AiQuotaExceededException;
import com.thughari.jobtrackerpro.util.TemplateParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Primary
@Slf4j
public class SmartExtractionService implements AiExtractionService {

    private final List<AiExtractionService> providers = new ArrayList<>();

    public SmartExtractionService(List<AiExtractionService> services) {
        // Order the providers: Gemini -> Groq -> OpenRouter -> Mock
        // We filter out 'this' instance to avoid infinite recursion
        services.stream().filter(s -> s instanceof GeminiExtractionService).findFirst().ifPresent(providers::add);
        services.stream().filter(s -> s instanceof GroqExtractionService).findFirst().ifPresent(providers::add);
        services.stream().filter(s -> s instanceof OpenRouterExtractionService).findFirst().ifPresent(providers::add);
        services.stream().filter(s -> s.getClass().getSimpleName().contains("Mock")).findFirst().ifPresent(providers::add);
        
        log.info("SmartExtractionService initialized with fallback chain: {}", 
                 providers.stream().map(p -> p.getClass().getSimpleName()).toList());
    }

    @Override
    public JobDTO extractJobFromEmail(String from, String subject, String body) {
        try {
            JobDTO manualJob = TemplateParser.parse(from, subject, body);
            if (manualJob != null) {
                return manualJob;
            }
        } catch (Exception e) {
            log.warn("Manual parsing error, falling back to AI delegation: {}", e.getMessage());
        }

        for (int i = 0; i < providers.size(); i++) {
            AiExtractionService provider = providers.get(i);
            try {
                log.info("Attempting extraction with provider: {}", provider.getClass().getSimpleName());
                return provider.extractJobFromEmail(from, subject, body);
            } catch (AiQuotaExceededException e) {
                log.warn("Quota exceeded for provider {}: {}", provider.getClass().getSimpleName(), e.getMessage());
                if (i < providers.size() - 1) {
                    log.info("Falling back to next provider...");
                } else {
                    log.error("All AI providers exhausted their quotas!");
                    throw e; // No more fallbacks
                }
            }
        }
        return null;
    }

    @Override
    public List<JobDTO> extractJobsFromBatch(List<EmailBatchItem> items) {
        if (items == null || items.isEmpty()) return List.of();

        List<JobDTO> results = new ArrayList<>();
        List<EmailBatchItem> remainingItems = new ArrayList<>();
        List<Integer> originalIndexes = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            EmailBatchItem item = items.get(i);
            JobDTO parsed = null;
            try {
                parsed = TemplateParser.parse(item.from(), item.subject(), item.body());
            } catch (Exception e) {
                log.warn("Manual parsing error: {}", e.getMessage());
            }

            if (parsed != null) {
                parsed.setInputIndex(i);
                results.add(parsed);
            } else {
                remainingItems.add(item);
                originalIndexes.add(i);
            }
        }

        if (!remainingItems.isEmpty()) {
            List<JobDTO> delegatedJobs = new ArrayList<>();
            for (int i = 0; i < providers.size(); i++) {
                AiExtractionService provider = providers.get(i);
                try {
                    log.info("Attempting batch extraction with provider: {}", provider.getClass().getSimpleName());
                    delegatedJobs = provider.extractJobsFromBatch(remainingItems);
                    break; // Success, stop trying other providers
                } catch (AiQuotaExceededException e) {
                    log.warn("Quota exceeded for provider {}: {}", provider.getClass().getSimpleName(), e.getMessage());
                    if (i < providers.size() - 1) {
                        log.info("Falling back to next provider...");
                    } else {
                        log.error("All AI providers exhausted their quotas!");
                        throw e;
                    }
                }
            }
            
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
