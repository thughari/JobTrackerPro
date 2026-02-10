package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.AuthRequest;
import com.thughari.jobtrackerpro.dto.ChangePasswordRequest;
import com.thughari.jobtrackerpro.entity.AuthProvider;
import com.thughari.jobtrackerpro.entity.PasswordResetToken;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.interfaces.StorageService;
import com.thughari.jobtrackerpro.repo.PasswordResetTokenRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @Mock private StorageService storageService;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerUser_createsUserAndReturnsToken() {
        AuthRequest request = new AuthRequest();
        request.setName("Test User");
        request.setEmail("test@example.com");
        request.setPassword("secret");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded");
        when(jwtUtils.generateAccessToken("test@example.com")).thenReturn("jwt");
        when(jwtUtils.generateRefreshToken("test@example.com")).thenReturn("refresh-jwt");

        var response = authService.registerUser(request);

        assertEquals("jwt", response.accessToken());
        assertEquals("refresh-jwt", response.refreshToken());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void loginUser_throwsWhenPasswordMismatch() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("encoded");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        AuthRequest request = new AuthRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrong");

        assertThrows(IllegalArgumentException.class, () -> authService.loginUser(request));
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
    void forgotPassword_throwsWhenMissingUser() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.forgotPassword("missing@example.com"));
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

    @Test
    void changePassword_updatesWhenCurrentMatches() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("oldEncoded");
        user.setProvider(AuthProvider.LOCAL);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("newpass");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "oldEncoded")).thenReturn(true);
        when(passwordEncoder.matches("newpass", "oldEncoded")).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("newEncoded");

        authService.changePassword("test@example.com", request);

        verify(userRepository).save(user);
        assertEquals("newEncoded", user.getPassword());
    }
}
