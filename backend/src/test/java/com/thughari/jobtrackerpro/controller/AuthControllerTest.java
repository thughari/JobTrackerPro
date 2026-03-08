package com.thughari.jobtrackerpro.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thughari.jobtrackerpro.dto.AuthRequest;
import com.thughari.jobtrackerpro.dto.AuthTokens;
import com.thughari.jobtrackerpro.dto.UserProfileResponse;
import com.thughari.jobtrackerpro.exception.GlobalExceptionHandler;
import com.thughari.jobtrackerpro.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerUser_returnsOkWhenServiceSucceeds() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("test@example.com");
        request.setName("Hari");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("verify")));

        verify(authService).registerUser(any(AuthRequest.class));
    }

    @Test
    void loginUser_returnsUnauthorized_WhenEmailNotVerified() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("unverified@example.com");

        when(authService.loginUser(any(AuthRequest.class)))
                .thenThrow(new IllegalStateException("verify email"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()) 
                .andExpect(jsonPath("$.message").value("verify email"));
    }

    @Test
    void verifyEmail_returnsOkAndSetsCookie() throws Exception {
        AuthTokens mockTokens = new AuthTokens("access-token", "refresh-token");
        
        when(authService.verifyUser("some-token")).thenReturn(mockTokens);

        mockMvc.perform(get("/api/auth/verify-email").param("token", "some-token"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.token").value("access-token"));

        verify(authService).verifyUser("some-token");
    }

    @Test
    void getCurrentUser_readsEmailFromSecurityContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.com", null)
        );

        UserProfileResponse profile = new UserProfileResponse();
        profile.setEmail("user@example.com");
        when(authService.getCurrentUser("user@example.com")).thenReturn(profile);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }
}