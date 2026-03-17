package com.thughari.jobtrackerpro.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class InterviewSessionStartResponse {
    private UUID sessionId;
    private String status;
    private String message;
}
