package com.thughari.jobtrackerpro.dto;

import lombok.Data;

@Data
public class CreateCareerResourceRequest {
    private String title;
    private String url;
    private String category;
    private String description;
    private String location;
    private String company;
    private String eventDate; // Use String for flexibility in parsing if needed, or LocalDateTime
    private String listingType;
}
