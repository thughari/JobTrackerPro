package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.AuthRequest;
import com.thughari.jobtrackerpro.dto.AuthTokens;
import com.thughari.jobtrackerpro.dto.ChangePasswordRequest;
import com.thughari.jobtrackerpro.dto.UserProfileResponse;
import com.thughari.jobtrackerpro.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private HttpServletResponse httpServletResponse;

    @InjectMocks
    private AuthController authController;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerUser_returnsOkWhenServiceSucceeds() {
        ReflectionTestUtils.setField(authController, "refreshExpirationMs", 1000L);
        ReflectionTestUtils.setField(authController, "refreshCookieSecure", false);

        AuthRequest request = new AuthRequest();
        when(authService.registerUser(request)).thenReturn(new AuthTokens("token", "refresh"));

        var result = authController.registerUser(request, httpServletResponse);

        assertEquals(HttpStatusCode.valueOf(200), result.getStatusCode());
    }

    @Test
    void loginUser_returnsBadRequestOnIllegalArgument() {
        AuthRequest request = new AuthRequest();
        when(authService.loginUser(request)).thenThrow(new IllegalArgumentException("bad creds"));

        var result = authController.loginUser(request, httpServletResponse);

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
