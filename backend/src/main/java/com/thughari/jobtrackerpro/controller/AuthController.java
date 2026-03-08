package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.AuthRequest;
import com.thughari.jobtrackerpro.dto.AuthResponse;
import com.thughari.jobtrackerpro.dto.AuthTokens;
import com.thughari.jobtrackerpro.dto.ChangePasswordRequest;
import com.thughari.jobtrackerpro.dto.ResetPasswordRequest;
import com.thughari.jobtrackerpro.dto.UserProfileResponse;
import com.thughari.jobtrackerpro.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
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
    public ResponseEntity<?> registerUser(@RequestBody AuthRequest request) {
        authService.registerUser(request);
        return ResponseEntity.ok(Map.of("message", "Registration successful. Please check your email to verify your account."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody AuthRequest request, HttpServletResponse response) {
        AuthTokens tokens = authService.loginUser(request);
        attachRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token, HttpServletResponse response) {
        AuthTokens tokens = authService.verifyUser(token);
        attachRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestParam String email) {
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok(Map.of("message", "A new verification link has been sent."));
    }


    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                                          HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).body("Missing refresh token");
        }

        AuthTokens tokens = authService.refreshAccessToken(refreshToken);
        attachRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(new AuthResponse(tokens.accessToken()));
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
        authService.changePassword(getAuthenticatedEmail(), request);
        return ResponseEntity.ok().body(Map.of("message", "Password set successfully."));
    }

	@PostMapping("/forgot-password")
	public ResponseEntity<?> forgotPassword(@RequestParam String email) {
		authService.forgotPassword(email);
		return ResponseEntity.ok("If that email exists, a reset link has been sent.");
	}

	@PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
    }

	private String getAuthenticatedEmail() {
		return ((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).toLowerCase();
	}

	private void attachRefreshCookie(HttpServletResponse response, String refreshToken) {
		response.addHeader("Set-Cookie", buildRefreshCookie(refreshToken, "/", refreshExpirationMs / 1000).toString());
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
