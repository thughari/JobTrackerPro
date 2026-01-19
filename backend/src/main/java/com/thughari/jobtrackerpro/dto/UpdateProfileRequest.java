package com.thughari.jobtrackerpro.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String name;
    private String imageUrl;
}