package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.AuthRequest;
import com.thughari.jobtrackerpro.dto.AuthResponse;
import com.thughari.jobtrackerpro.dto.ChangePasswordRequest;
import com.thughari.jobtrackerpro.dto.UserProfileResponse;
import com.thughari.jobtrackerpro.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerUser_returnsOkWhenServiceSucceeds() {
        AuthRequest request = new AuthRequest();
        AuthResponse response = new AuthResponse("token");
        when(authService.registerUser(request)).thenReturn(response);

        var result = authController.registerUser(request);

        assertEquals(HttpStatusCode.valueOf(200), result.getStatusCode());
        assertEquals(response, result.getBody());
    }

    @Test
    void loginUser_returnsBadRequestOnIllegalArgument() {
        AuthRequest request = new AuthRequest();
        when(authService.loginUser(request)).thenThrow(new IllegalArgumentException("bad creds"));

        var result = authController.loginUser(request);

        assertEquals(HttpStatusCode.valueOf(400), result.getStatusCode());
        assertEquals("bad creds", result.getBody());
    }

    @Test
    void getCurrentUser_readsEmailFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("USER@EXAMPLE.COM", null));

        UserProfileResponse profile = new UserProfileResponse();
        profile.setEmail("user@example.com");
        when(authService.getCurrentUser("user@example.com")).thenReturn(profile);

        var result = authController.getCurrentUser();

        assertEquals(HttpStatusCode.valueOf(200), result.getStatusCode());
        assertEquals("user@example.com", result.getBody().getEmail());
    }

    @Test
    void changePassword_returnsBadRequestOnValidationError() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user@example.com", null));

        ChangePasswordRequest request = new ChangePasswordRequest();
        doThrow(new IllegalArgumentException("Incorrect current password"))
                .when(authService).changePassword("user@example.com", request);

        var result = authController.changePassword(request);

        assertEquals(HttpStatusCode.valueOf(400), result.getStatusCode());
        assertEquals("Incorrect current password", result.getBody());
    }

    @Test
    void forgotPassword_alwaysReturnsOk() {
        var result = authController.forgotPassword("missing@example.com");

        assertEquals(HttpStatusCode.valueOf(200), result.getStatusCode());
        verify(authService).forgotPassword("missing@example.com");
    }
}
