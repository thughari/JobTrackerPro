package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.DeletionWarning;
import com.thughari.jobtrackerpro.service.UserDeletionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserDeletionController {

    private final UserDeletionService userDeletionService;

    public UserDeletionController(UserDeletionService userDeletionService) {
        this.userDeletionService = userDeletionService;
    }

    /**
     * Request account deletion
     * Account will be permanently deleted after 3 days unless cancelled
     */
    @PostMapping("/request-deletion")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> requestDeletion() {
        String email = getAuthenticatedEmail();
        userDeletionService.requestDeletion(email);
        return ResponseEntity.ok().body(
            java.util.Map.of(
                "message", "Deletion request submitted. Your account will be permanently deleted in 3 days.",
                "daysRemaining", 3
            )
        );
    }

    /**
     * Check if user has a pending deletion request
     */
    @GetMapping("/deletion-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeletionWarning> checkDeletionStatus() {
        String email = getAuthenticatedEmail();
        DeletionWarning warning = userDeletionService.checkPendingDeletion(email);
        return ResponseEntity.ok(warning);
    }

    /**
     * Cancel pending deletion
     */
    @PostMapping("/cancel-deletion")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> cancelDeletion() {
        String email = getAuthenticatedEmail();
        userDeletionService.cancelDeletion(email);
        return ResponseEntity.ok().body(
            java.util.Map.of(
                "message", "Deletion request cancelled successfully."
            )
        );
    }

    private String getAuthenticatedEmail() {
        return ((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).toLowerCase();
    }
}

