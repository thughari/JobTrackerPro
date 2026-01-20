package com.thughari.jobtrackerpro.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class JobDTO {
    private UUID id;
    private String company;
    private String role;
    private String location;
    private LocalDateTime date;
    private String status;
    private Integer stage;
    private String stageStatus;
    private Double salaryMin;
    private Double salaryMax;
    private String url;
    private String notes;
}