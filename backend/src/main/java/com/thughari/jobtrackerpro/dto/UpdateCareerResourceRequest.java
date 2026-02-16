package com.thughari.jobtrackerpro.dto;

import lombok.Data;

@Data
public class UpdateCareerResourceRequest {
    private String title;
    private String url;
    private String category;
    private String description;
}
