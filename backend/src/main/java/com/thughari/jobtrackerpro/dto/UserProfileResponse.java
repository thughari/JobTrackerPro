package com.thughari.jobtrackerpro.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UserProfileResponse {
    private UUID id;
    private String name;
    private String email;
    private String imageUrl;
    private String provider;
    private boolean hasPassword;
    private boolean gmailConnected;
    private boolean gmailSyncInProgress;
    private boolean enabled;
}