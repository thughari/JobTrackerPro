package com.thughari.jobtrackerpro.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private int jwtExpirationMs;

    @Value("${app.jwt.refresh-secret}")
    private String refreshJwtSecret;

    @Value("${app.jwt.refresh-expiration-ms}")
    private int refreshJwtExpirationMs;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    private Key getRefreshSigningKey() {
        return Keys.hmacShaKeyFor(refreshJwtSecret.getBytes());
    }

    public String generateToken(String email) {
        return generateAccessToken(email);
    }

    public String generateAccessToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .claim("env", activeProfile)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .claim("env", activeProfile)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + refreshJwtExpirationMs))
                .signWith(getRefreshSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateToken(String authToken) {
        return validateAccessToken(authToken);
    }

    public boolean validateAccessToken(String authToken) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                    .parseClaimsJws(authToken).getBody();
            return isTokenProfileValid(claims);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String refreshToken) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(getRefreshSigningKey()).build()
                    .parseClaimsJws(refreshToken).getBody();
            return isTokenProfileValid(claims);
        } catch (Exception e) {
            return false;
        }
    }

    public String getEmailFromRefreshToken(String refreshToken) {
        return Jwts.parserBuilder().setSigningKey(getRefreshSigningKey()).build()
                .parseClaimsJws(refreshToken).getBody().getSubject();
    }

    private boolean isTokenProfileValid(Claims claims) {
        String tokenProfile = claims.get("env", String.class);
        return tokenProfile != null && tokenProfile.equals(activeProfile);
    }
}
