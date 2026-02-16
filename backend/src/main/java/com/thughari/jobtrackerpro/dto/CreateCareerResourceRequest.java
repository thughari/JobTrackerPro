package com.thughari.jobtrackerpro.dto;

import lombok.Data;

@Data
public class CreateCareerResourceRequest {
    private String title;
    private String url;
    private String category;
    private String description;
}
