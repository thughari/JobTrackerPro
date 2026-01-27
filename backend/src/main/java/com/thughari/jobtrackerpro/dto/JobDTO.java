package com.thughari.jobtrackerpro.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class JobDTO {
    private UUID id;
    private String company;
    private String role;
    private String location;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime appliedDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime updatedAt;
    private String status;
    private Integer stage;
    private String stageStatus;
    private Double salaryMin;
    private Double salaryMax;
    private String url;
    private String notes;
}