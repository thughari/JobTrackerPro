package com.thughari.jobtrackerpro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class InterviewQuestionsResponse {
    private List<InterviewQuestionDTO> questions;
    private Integer currentIndex;
    private Integer totalQuestions;
}
