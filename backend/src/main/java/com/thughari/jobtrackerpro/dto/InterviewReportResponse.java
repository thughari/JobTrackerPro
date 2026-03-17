package com.thughari.jobtrackerpro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class InterviewReportResponse {
    private Integer overallScore;
    private Integer answeredQuestions;
    private Integer totalQuestions;
    private List<String> weakAreas;
    private List<String> improvementSuggestions;
}
