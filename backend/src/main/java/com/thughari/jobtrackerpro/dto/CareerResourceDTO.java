package com.thughari.jobtrackerpro.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CareerResourceDTO {
    private UUID id;
    private String title;
    private String url;
    private String category;
    private String description;
    private String submittedByName;
    private LocalDateTime createdAt;
}
