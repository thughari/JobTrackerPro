package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.AuthRequest;
import com.thughari.jobtrackerpro.dto.ChangePasswordRequest;
import com.thughari.jobtrackerpro.dto.UserProfileResponse;
import com.thughari.jobtrackerpro.service.AuthService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

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

    @GetMapping("/profile/image/{id}")
    public ResponseEntity<byte[]> getProfileImage(@PathVariable UUID id) {
        try {
            byte[] image = authService.getProfileImage(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(image);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
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

    private String getAuthenticatedEmail() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}