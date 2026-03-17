package com.thughari.jobtrackerpro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thughari.jobtrackerpro.dto.*;
import com.thughari.jobtrackerpro.entity.InterviewSession;
import com.thughari.jobtrackerpro.entity.Job;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.repo.InterviewSessionRepository;
import com.thughari.jobtrackerpro.repo.JobRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
@Slf4j
public class InterviewService {
    private final JobRepository jobRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InterviewService(JobRepository jobRepository, InterviewSessionRepository interviewSessionRepository, JdbcTemplate jdbcTemplate) {
        this.jobRepository = jobRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public InterviewSessionStartResponse startSession(UUID jobId, String userEmail) {
        Job job = jobRepository.findById(jobId)
                .filter(j -> userEmail.equalsIgnoreCase(j.getUserEmail()))
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        List<String> questions = List.of(
                "Tell me about yourself and why this role at " + safe(job.getCompany()) + " is a good fit.",
                "Describe a project where you solved a hard engineering problem relevant to " + safe(job.getRole()) + ".",
                "How do you prioritize tasks when requirements change quickly?",
                "Explain a conflict with a teammate and how you resolved it.",
                "What would your 30-60-90 day plan look like in this role?"
        );

        InterviewSession session = new InterviewSession();
        session.setUserEmail(userEmail);
        session.setJobId(jobId);
        session.setJobCompany(job.getCompany());
        session.setJobRole(job.getRole());
        session.setResumeId(resolveOrCreateResumeId(userEmail));
        session.setStatus("IN_PROGRESS");
        session.setCurrentQuestionIndex(0);
        session.setTotalQuestions(questions.size());
        session.setOverallScore(0.0);
        session.setResumeProcessingStatus("NOT_UPLOADED");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setQuestionsJson(writeValue(questions));
        session.setAnswersJson(writeValue(new ArrayList<AnswerEvaluation>()));
        session.setWeakAreasJson(writeValue(new ArrayList<String>()));
        session.setImprovementSuggestionsJson(writeValue(new ArrayList<String>()));

        interviewSessionRepository.save(session);
        return new InterviewSessionStartResponse(session.getId(), session.getStatus(), "Interview prep session started");
    }

    @Transactional(readOnly = true)
    public InterviewQuestionsResponse getQuestions(UUID sessionId, String userEmail) {
        InterviewSession session = getSession(sessionId, userEmail);
        List<String> questions = readList(session.getQuestionsJson(), new TypeReference<>() {});
        List<InterviewQuestionDTO> payload = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            payload.add(new InterviewQuestionDTO(i, questions.get(i)));
        }
        return new InterviewQuestionsResponse(payload, session.getCurrentQuestionIndex(), session.getTotalQuestions());
    }

    public InterviewAnswerResponse submitAnswer(UUID sessionId, String userEmail, InterviewAnswerRequest request) {
        InterviewSession session = getSession(sessionId, userEmail);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new IllegalStateException("Interview session is already completed");
        }

        List<String> questions = readList(session.getQuestionsJson(), new TypeReference<>() {});
        if (request.getQuestionIndex() == null || request.getQuestionIndex() < 0 || request.getQuestionIndex() >= questions.size()) {
            throw new IllegalArgumentException("Invalid question index");
        }

        String answerText = request.getAnswer() == null ? "" : request.getAnswer().trim();
        int score = evaluateScore(answerText);
        String feedback = score >= 8 ? "Strong answer with clear structure." : score >= 6 ? "Solid base, add more measurable impact." : "Needs stronger examples and role alignment.";
        String gap = score >= 8 ? "Add one concise metric for even more impact." : "Include STAR structure, concrete ownership, and outcomes.";

        List<AnswerEvaluation> answers = readList(session.getAnswersJson(), new TypeReference<>() {});
        answers.removeIf(a -> Objects.equals(a.getQuestionIndex(), request.getQuestionIndex()));
        answers.add(new AnswerEvaluation(request.getQuestionIndex(), answerText, score, feedback, gap));
        answers.sort(Comparator.comparing(AnswerEvaluation::getQuestionIndex));

        session.setAnswersJson(writeValue(answers));
        int nextIndex = Math.min(request.getQuestionIndex() + 1, questions.size());
        session.setCurrentQuestionIndex(nextIndex);

        if (answers.size() >= questions.size()) {
            session.setStatus("COMPLETED");
            double avg = answers.stream().mapToInt(AnswerEvaluation::getScore).average().orElse(0);
            session.setOverallScore(avg);
            session.setWeakAreasJson(writeValue(buildWeakAreas(answers)));
            session.setImprovementSuggestionsJson(writeValue(buildSuggestions(answers)));
        }

        session.setUpdatedAt(LocalDateTime.now());
        interviewSessionRepository.save(session);

        return new InterviewAnswerResponse(score, feedback, gap, nextIndex, "COMPLETED".equals(session.getStatus()));
    }

    @Transactional(readOnly = true)
    public InterviewReportResponse getReport(UUID sessionId, String userEmail) {
        InterviewSession session = getSession(sessionId, userEmail);
        List<AnswerEvaluation> answers = readList(session.getAnswersJson(), new TypeReference<>() {});
        int overall = (int) Math.round(answers.stream().mapToInt(AnswerEvaluation::getScore).average().orElse(0));
        return new InterviewReportResponse(
                overall,
                answers.size(),
                session.getTotalQuestions(),
                readList(session.getWeakAreasJson(), new TypeReference<>() {}),
                readList(session.getImprovementSuggestionsJson(), new TypeReference<>() {})
        );
    }

    public void uploadResume(UUID sessionId, String userEmail, MultipartFile file) {
        InterviewSession session = getSession(sessionId, userEmail);
        session.setResumeFileName(file.getOriginalFilename());
        session.setResumeProcessingStatus("PROCESSING");
        session.setUpdatedAt(LocalDateTime.now());
        interviewSessionRepository.save(session);

        session.setResumeProcessingStatus("PROCESSED");
        session.setUpdatedAt(LocalDateTime.now());
        interviewSessionRepository.save(session);
    }


    private UUID resolveOrCreateResumeId(String userEmail) {
        List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT column_name, is_nullable, data_type, column_default FROM information_schema.columns WHERE table_schema='public' AND table_name='user_resumes'"
        );

        UUID existingId = findAnyExistingResumeId(cols);
        if (existingId != null) {
            return existingId;
        }

        UUID newResumeId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        return insertDynamicMockResume(userEmail, newResumeId, now, cols);
    }

    private UUID findAnyExistingResumeId(List<Map<String, Object>> cols) {
        try {
            boolean hasUpdatedAt = cols.stream().anyMatch(c -> "updated_at".equalsIgnoreCase(String.valueOf(c.get("column_name"))));
            String sql = hasUpdatedAt
                    ? "SELECT id FROM user_resumes ORDER BY updated_at DESC LIMIT 1"
                    : "SELECT id FROM user_resumes LIMIT 1";

            List<UUID> existing = jdbcTemplate.query(sql, (rs, rowNum) -> (UUID) rs.getObject("id"));
            return existing.isEmpty() ? null : existing.get(0);
        } catch (Exception ex) {
            log.warn("Could not query existing user_resumes row. Will attempt mock insert. Reason: {}", ex.getMessage());
            return null;
        }
    }

    private UUID insertDynamicMockResume(String userEmail, UUID resumeId, LocalDateTime now, List<Map<String, Object>> cols) {
        Map<String, Object> values = new LinkedHashMap<>();
        Set<String> colNames = cols.stream().map(c -> String.valueOf(c.get("column_name"))).collect(java.util.stream.Collectors.toSet());

        if (colNames.contains("id")) values.put("id", resumeId);
        if (colNames.contains("user_email")) values.put("user_email", userEmail);
        if (colNames.contains("email")) values.put("email", userEmail);
        if (colNames.contains("created_at")) values.put("created_at", now);
        if (colNames.contains("updated_at")) values.put("updated_at", now);
        if (colNames.contains("extracted_text")) values.put("extracted_text", "Local mock resume for interview prep");

        for (Map<String, Object> col : cols) {
            String name = String.valueOf(col.get("column_name"));
            String nullable = String.valueOf(col.get("is_nullable"));
            String dataType = String.valueOf(col.get("data_type"));
            String defaultVal = col.get("column_default") == null ? null : String.valueOf(col.get("column_default"));

            if (values.containsKey(name) || !"NO".equalsIgnoreCase(nullable) || (defaultVal != null && !defaultVal.isBlank())) {
                continue;
            }

            values.put(name, defaultForType(dataType, now));
        }

        if (values.isEmpty()) {
            throw new IllegalStateException("user_resumes schema has no writable columns");
        }

        String columnsSql = String.join(", ", values.keySet());
        String placeholders = String.join(", ", Collections.nCopies(values.size(), "?"));
        String sql = "INSERT INTO user_resumes (" + columnsSql + ") VALUES (" + placeholders + ")";
        jdbcTemplate.update(sql, values.values().toArray());

        return resumeId;
    }

    private Object defaultForType(String dataType, LocalDateTime now) {
        String dt = dataType == null ? "" : dataType.toLowerCase();

        return switch (dt) {
            case "uuid" -> UUID.randomUUID();
            case "boolean" -> false;
            case "smallint", "integer", "bigint" -> 0;
            case "numeric", "real", "double precision" -> 0.0;
            case "json", "jsonb" -> "{}";
            case "date" -> now.toLocalDate();
            case "timestamp without time zone", "timestamp with time zone" -> now;
            default -> "mock";
        };
    }

    private InterviewSession getSession(UUID sessionId, String userEmail) {
        return interviewSessionRepository.findByIdAndUserEmail(sessionId, userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found"));
    }

    private int evaluateScore(String answer) {
        int length = answer.length();
        int score = 4;
        if (length > 80) score++;
        if (length > 180) score++;
        if (answer.matches(".*\\d+.*")) score++;
        if (answer.toLowerCase().contains("impact") || answer.toLowerCase().contains("result")) score++;
        if (answer.toLowerCase().contains("i ") && answer.toLowerCase().contains("team")) score++;
        return Math.min(score, 10);
    }

    private List<String> buildWeakAreas(List<AnswerEvaluation> answers) {
        if (answers.stream().allMatch(a -> a.getScore() >= 7)) {
            return List.of("Depth of metrics in examples");
        }
        return answers.stream().filter(a -> a.getScore() < 7)
                .map(a -> "Question " + (a.getQuestionIndex() + 1) + ": clarity and measurable impact")
                .toList();
    }

    private List<String> buildSuggestions(List<AnswerEvaluation> answers) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("Use STAR structure (Situation, Task, Action, Result) for each answer.");
        if (answers.stream().anyMatch(a -> a.getScore() < 7)) {
            suggestions.add("Add at least one number or KPI in every response.");
        }
        suggestions.add("Keep responses concise: 60-90 seconds with a clear takeaway.");
        return suggestions;
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize interview session payload", e);
        }
    }

    private <T> T readList(String value, TypeReference<T> typeRef) {
        try {
            if (value == null || value.isBlank()) {
                return objectMapper.readValue("[]", typeRef);
            }
            return objectMapper.readValue(value, typeRef);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse interview session payload", e);
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "this company" : value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnswerEvaluation {
        private Integer questionIndex;
        private String answer;
        private Integer score;
        private String feedback;
        private String improvementGap;
    }
}
