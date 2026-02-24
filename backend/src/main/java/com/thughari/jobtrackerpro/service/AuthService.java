package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.*;
import com.thughari.jobtrackerpro.entity.AuthProvider;
import com.thughari.jobtrackerpro.entity.PasswordResetToken;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.exception.UserAlreadyExistsException;
import com.thughari.jobtrackerpro.exception.UserNotFoundException;
import com.thughari.jobtrackerpro.interfaces.StorageService;
import com.thughari.jobtrackerpro.repo.PasswordResetTokenRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.security.JwtUtils;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final StorageService storageService;
    private final CacheManager cacheManager;
    
    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${cloudflare.r2.public-url.avatars}")
    private String avatarPublicUrl;
    
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, 
    		JwtUtils jwtUtils, StorageService storageService, 
    		PasswordResetTokenRepository tokenRepository, EmailService emailService, CacheManager cacheManager) {
    	this.userRepository = userRepository;
    	this.passwordEncoder = passwordEncoder;
    	this.jwtUtils = jwtUtils;
    	this.storageService = storageService;
    	this.tokenRepository = tokenRepository;
    	this.emailService = emailService;
    	this.cacheManager = cacheManager;
    }

    public AuthTokens registerUser(AuthRequest request) {
    	validateUsername(request.getName());
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
        	throw new UserAlreadyExistsException("Email already in use");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setProvider(AuthProvider.LOCAL);
        userRepository.save(user);

        return generateAuthTokens(user.getEmail());
    }

    public AuthTokens loginUser(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Login failed! User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Login failed! Invalid password");
        }

        return generateAuthTokens(user.getEmail());
    }

    public AuthTokens refreshAccessToken(String refreshToken) {
        if (!jwtUtils.validateRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String email = jwtUtils.getEmailFromRefreshToken(refreshToken);
        if (userRepository.findByEmail(email).isEmpty()) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        return generateAuthTokens(email);
    }
    
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PasswordResetToken tokenEntity = tokenRepository.findByUser(user)
                .orElse(new PasswordResetToken());

        tokenEntity.setUser(user);
        tokenEntity.setToken(UUID.randomUUID().toString());
        tokenEntity.setExpiryDate(LocalDateTime.now().plusMinutes(15));

        tokenRepository.save(tokenEntity);

        emailService.sendResetEmail(user.getEmail(), tokenEntity.getToken());
    }

    public void resetPassword(String token, String newPassword) {
    	if (newPassword == null || newPassword.trim().isEmpty()) {
    		throw new IllegalArgumentException("Password cannot be empty");
    	}
    	if (newPassword.length() < 6) {
    		throw new IllegalArgumentException("Password must be at least 6 characters long");
    	}

    	PasswordResetToken resetToken = tokenRepository.findByToken(token)
    			.orElseThrow(() -> new IllegalArgumentException("Invalid token"));

    	if (resetToken.isExpired()) {
    		tokenRepository.delete(resetToken);
    		throw new IllegalArgumentException("Token has expired");
    	}

    	User user = resetToken.getUser();
    	user.setPassword(passwordEncoder.encode(newPassword));
    	userRepository.save(user);
    	
    	evictAllUserCaches(user.getEmail());

    	tokenRepository.delete(resetToken);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#email")
    public UserProfileResponse getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .map(this::mapToProfileResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @Caching(evict = {
            @CacheEvict(value = "users", key = "#email"),
            @CacheEvict(value = "userEntities", key = "#email")
        })
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            if (request.getCurrentPassword() == null) {
                throw new IllegalArgumentException("Current password is required");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Incorrect current password");
            }
            if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
                throw new IllegalArgumentException("New password cannot be the same as the old password");
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
    
    @CacheEvict(value = "users", key = "#email")
    public UserProfileResponse updateProfileAtomic(String email, String name, String imageUrl, MultipartFile file) {
    	validateUsername(name);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        String oldImageUrl = user.getImageUrl();
        String newR2Url = null;

        user.setName(name);

        if (file != null && !file.isEmpty()) {
            newR2Url = storageService.uploadFile(file, user.getId().toString());
        }
        else if (imageUrl != null && !imageUrl.isEmpty()) {
        	
        	if (imageUrl.startsWith(avatarPublicUrl) || imageUrl.contains(baseUrl)) {
        		newR2Url = imageUrl;
           } else {
        	   newR2Url = storageService.uploadFromUrl(imageUrl, user.getId().toString());
           }
        }
        if (newR2Url != null) {
            if (oldImageUrl != null && !oldImageUrl.equals(newR2Url)) {
                storageService.deleteFile(oldImageUrl);
            }
            user.setImageUrl(newR2Url);
        }

        userRepository.save(user);
        return mapToProfileResponse(user);
    }
    

    /**
     * High Performance OAuth User Sync
     * Consolidates find, create, and profile update into one DB trip.
     */
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#email"),
            @CacheEvict(value = "userEntities", key = "#email")
        })
    public User processOAuthUser(String email, String name, String imageUrl, String provider) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email.toLowerCase());
                    newUser.setProvider(AuthProvider.valueOf(provider.toUpperCase()));
                    newUser.setGmailConnected(false);
                    return newUser;
                });

        boolean needsUpdate = false;

        // Only update if data actually changed to avoid redundant SQL UPDATE statements
        if (user.getName() == null || !user.getName().equals(name)) {
            user.setName(name);
            needsUpdate = true;
        }

        // High Performance: We check if the image is already a JobTrackerPro/R2 URL 
        // to avoid re-uploading social images every single login.
        if (user.getImageUrl() == null || (!user.getImageUrl().contains("r2") && !user.getImageUrl().equals(imageUrl))) {
            if (imageUrl != null && !imageUrl.isBlank()) {
                try {
                    // Offload image sync to storage service
                    String synchronizedUrl = storageService.uploadFromUrl(imageUrl, 
                        user.getId() != null ? user.getId().toString() : UUID.randomUUID().toString());
                    user.setImageUrl(synchronizedUrl);
                    needsUpdate = true;
                } catch (Exception e) {
                    log.error("Social image sync failed: {}", e.getMessage());
                }
            }
        }

        if (user.getId() == null || needsUpdate) {
            return userRepository.saveAndFlush(user);
        }
        
        return user;
    }
    
    private void evictAllUserCaches(String email) {
        Cache userCache = cacheManager.getCache("users");
        Cache entityCache = cacheManager.getCache("userEntities");
        if (userCache != null) userCache.evict(email);
        if (entityCache != null) entityCache.evict(email);
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setImageUrl(user.getImageUrl());
        response.setProvider(user.getProvider().toString());
        response.setHasPassword(user.getPassword() != null && !user.getPassword().isEmpty());
        response.setGmailConnected(Boolean.TRUE.equals(user.getGmailConnected()));
        response.setGmailSyncInProgress(Boolean.TRUE.equals(user.getGmailSyncInProgress()));
        return response;
    }

    private AuthTokens generateAuthTokens(String email) {
        String accessToken = jwtUtils.generateAccessToken(email);
        String refreshToken = jwtUtils.generateRefreshToken(email);
        return new AuthTokens(accessToken, refreshToken);
    }
    
    private void validateUsername(String name) {
        if (name == null) return;
        String normalized = name.toLowerCase().replaceAll("\\s+", "");
        if (normalized.contains("jobtrackerpro") || normalized.equals("admin") || normalized.equals("system")) {
            throw new IllegalArgumentException("This name is reserved and cannot be used.");
        }
    }
}
