package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.AuthRequest;
import com.thughari.jobtrackerpro.dto.ChangePasswordRequest;
import com.thughari.jobtrackerpro.dto.ResetPasswordRequest;
import com.thughari.jobtrackerpro.dto.UserProfileResponse;
import com.thughari.jobtrackerpro.service.AuthService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody AuthRequest request) {
        try {
            return ResponseEntity.ok(authService.registerUser(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody AuthRequest request) {
        try {
            return ResponseEntity.ok(authService.loginUser(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(authService.getCurrentUser(email));
    }

    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProfile(
            @RequestParam String name,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false) MultipartFile file
    ) {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(authService.updateProfileAtomic(email, name, imageUrl, file));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            String email = getAuthenticatedEmail();
            authService.changePassword(email, request);
            return ResponseEntity.ok().body("Password set successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        try {
            authService.forgotPassword(email);
            return ResponseEntity.ok("If that email exists, a reset link has been sent.");
        } catch (Exception e) {
            return ResponseEntity.ok("If that email exists, a reset link has been sent.");
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok("Password reset successfully. Please login.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private String getAuthenticatedEmail() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}