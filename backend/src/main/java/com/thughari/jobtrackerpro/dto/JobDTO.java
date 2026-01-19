package com.thughari.jobtrackerpro.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class JobDTO {
    private UUID id;
    private String company;
    private String role;
    private String location;
    private LocalDate date;
    private String status;
    private Integer stage;
    private String stageStatus;
    private Double salaryMin;
    private Double salaryMax;
    private String url;
    private String notes;
}