package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.AuthRequest;
import com.thughari.jobtrackerpro.dto.AuthResponse;
import com.thughari.jobtrackerpro.dto.AuthTokens;
import com.thughari.jobtrackerpro.dto.ChangePasswordRequest;
import com.thughari.jobtrackerpro.dto.ResetPasswordRequest;
import com.thughari.jobtrackerpro.dto.UserProfileResponse;
import com.thughari.jobtrackerpro.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.jwt.refresh-cookie-secure}")
    private boolean refreshCookieSecure;

    @Value("${app.jwt.refresh-cookie-same-site:Lax}")
    private String refreshCookieSameSite;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody AuthRequest request, HttpServletResponse response) {
        try {
            AuthTokens tokens = authService.registerUser(request);
            attachRefreshCookie(response, tokens.refreshToken());
            return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody AuthRequest request, HttpServletResponse response) {
        try {
            AuthTokens tokens = authService.loginUser(request);
            attachRefreshCookie(response, tokens.refreshToken());
            return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                                          HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).body("Missing refresh token");
        }

        try {
            AuthTokens tokens = authService.refreshAccessToken(refreshToken);
            attachRefreshCookie(response, tokens.refreshToken());
            return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
        } catch (IllegalArgumentException e) {
            clearRefreshCookie(response);
            return ResponseEntity.status(401).body("Invalid refresh token");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        clearRefreshCookie(response);
        return ResponseEntity.ok().build();
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
        return ((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).toLowerCase();
    }

    private void attachRefreshCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader("Set-Cookie", buildRefreshCookie(refreshToken, "/", refreshExpirationMs / 1000).toString());
        // Clear legacy cookie written by older builds to prevent duplicate refresh_token cookies.
        response.addHeader("Set-Cookie", buildRefreshCookie("", "/api/auth", 0).toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildRefreshCookie("", "/", 0).toString());
        response.addHeader("Set-Cookie", buildRefreshCookie("", "/api/auth", 0).toString());
    }

    private ResponseCookie buildRefreshCookie(String value, String path, long maxAgeSeconds) {
        return ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .path(path)
                .sameSite(refreshCookieSameSite)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
