package com.thughari.jobtrackerpro.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CareerResourceDTO {
    private UUID id;
    private String title;
    private String url;
    private boolean fileUpload;
    private String fileName;
    private Long fileSizeBytes;
    private String category;
    private String description;
    private String submittedByName;
    private String submittedByEmail;
    private LocalDateTime createdAt;
}
