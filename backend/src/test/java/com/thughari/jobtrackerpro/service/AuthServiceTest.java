package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.AuthRequest;
import com.thughari.jobtrackerpro.dto.ChangePasswordRequest;
import com.thughari.jobtrackerpro.entity.AuthProvider;
import com.thughari.jobtrackerpro.entity.PasswordResetToken;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.entity.VerificationToken;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.repo.PasswordResetTokenRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.repo.VerificationTokenRepository;
import com.thughari.jobtrackerpro.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private VerificationTokenRepository verificationTokenRepository; // Added
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private CacheManager cacheManager; // Added

    @InjectMocks
    private AuthService authService;

    @Test
    void registerUser_createsDisabledUserAndSendsEmail() {
        AuthRequest request = new AuthRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("secret");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded");

        // Act - No variable assignment because return is void
        authService.registerUser(request);

        // Assert - Verify interactions for high performance / atomic flow
        verify(userRepository).saveAndFlush(any(User.class));
        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail(eq("test@example.com"), any(String.class));
    }

    @Test
    void loginUser_throwsWhenUserNotEnabled() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setEnabled(false); // User exists but not verified
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        AuthRequest request = new AuthRequest();
        request.setEmail("test@example.com");
        request.setPassword("password");

        assertThrows(IllegalStateException.class, () -> authService.loginUser(request));
    }

    @Test
    void verifyEmail_enablesUserAndClearsToken() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setEnabled(false);
        
        VerificationToken token = new VerificationToken();
        token.setToken("valid-token");
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusHours(1));

        when(verificationTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        authService.verifyUser("valid-token");

        assertTrue(user.getEnabled());
        verify(userRepository).saveAndFlush(user);
        verify(verificationTokenRepository).delete(token);
    }

    @Test
    void forgotPassword_createsTokenAndSendsEmail() {
        User user = new User();
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUser(user)).thenReturn(Optional.empty());

        authService.forgotPassword("test@example.com");

        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendResetEmail(eq("test@example.com"), any(String.class));
    }

    @Test
    void resetPassword_rejectsExpiredToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("abc");
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1));

        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class, () -> authService.resetPassword("abc", "newpassword"));
        verify(tokenRepository).delete(token);
    }
}