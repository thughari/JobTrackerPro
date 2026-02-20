package com.thughari.jobtrackerpro.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtils, "refreshJwtSecret", "abcdefghijklmnopqrstuvwxyz123456");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 60_000);
        ReflectionTestUtils.setField(jwtUtils, "refreshJwtExpirationMs", 120_000L);
        ReflectionTestUtils.setField(jwtUtils, "activeProfile", "test");
    }

    @Test
    void accessTokenRoundTrip() {
        String token = jwtUtils.generateAccessToken("user@example.com");
        assertTrue(jwtUtils.validateAccessToken(token));
        assertEquals("user@example.com", jwtUtils.getEmailFromToken(token));
    }

    @Test
    void refreshTokenRoundTrip() {
        String refreshToken = jwtUtils.generateRefreshToken("user@example.com");
        assertTrue(jwtUtils.validateRefreshToken(refreshToken));
        assertEquals("user@example.com", jwtUtils.getEmailFromRefreshToken(refreshToken));
    }

    @Test
    void rejectsInvalidToken() {
        assertFalse(jwtUtils.validateAccessToken("not-a-token"));
        assertFalse(jwtUtils.validateRefreshToken("not-a-token"));
    }

    @Test
    void generateTokenDelegatesToAccessToken() {
        String token = jwtUtils.generateToken("mail@example.com");
        assertTrue(jwtUtils.validateToken(token));
        assertEquals("mail@example.com", jwtUtils.getEmailFromToken(token));
    }
}
