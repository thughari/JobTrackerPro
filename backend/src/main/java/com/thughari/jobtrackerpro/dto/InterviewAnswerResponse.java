package com.thughari.jobtrackerpro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InterviewAnswerResponse {
    private Integer score;
    private String feedback;
    private String improvementGap;
    private Integer nextQuestionIndex;
    private boolean completed;
}
