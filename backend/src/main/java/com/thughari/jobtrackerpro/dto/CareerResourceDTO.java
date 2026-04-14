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
    private String resourceType;
    private String originalFileName;
    private Long fileSizeBytes;
    private boolean ownedByCurrentUser;
    private String submittedByName;
    private LocalDateTime createdAt;
    private String location;
    private String company;
    private LocalDateTime eventDate;
    private String listingType;
}
