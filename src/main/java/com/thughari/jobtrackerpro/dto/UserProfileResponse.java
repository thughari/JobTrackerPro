package com.thughari.jobtrackerpro.dto;

import lombok.Data;
import java.util.UUID;

import com.thughari.jobtrackerpro.entity.AuthProvider;

@Data
public class UserProfileResponse {
    private UUID id;
    private String name;
    private String email;
    private String imageUrl;
    private String provider;
    private boolean hasPassword;
}