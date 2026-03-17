package com.thughari.jobtrackerpro.dto;

import lombok.Data;

@Data
public class InterviewAnswerRequest {
    private Integer questionIndex;
    private String answer;
}
