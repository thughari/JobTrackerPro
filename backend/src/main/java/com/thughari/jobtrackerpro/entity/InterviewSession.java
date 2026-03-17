package com.thughari.jobtrackerpro.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "interview_sessions", indexes = {
        @Index(name = "idx_interview_sessions_user_updated", columnList = "userEmail, updatedAt DESC")
})
public class InterviewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID jobId;

    private String jobCompany;
    private String jobRole;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID resumeId;

    @Column(nullable = false)
    private String status;

    private Integer currentQuestionIndex;
    private Integer totalQuestions;
    private Double overallScore;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String questionsJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String answersJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String weakAreasJson;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String improvementSuggestionsJson;

    private String resumeFileName;
    private String resumeProcessingStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
