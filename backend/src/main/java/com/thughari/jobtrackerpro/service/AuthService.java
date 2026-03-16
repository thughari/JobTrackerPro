package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.*;
import com.thughari.jobtrackerpro.entity.AuthProvider;
import com.thughari.jobtrackerpro.entity.PasswordResetToken;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.entity.VerificationToken;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.exception.UserAlreadyExistsException;
import com.thughari.jobtrackerpro.exception.UserNotFoundException;
import com.thughari.jobtrackerpro.interfaces.StorageService;
import com.thughari.jobtrackerpro.repo.PasswordResetTokenRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.repo.VerificationTokenRepository;
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
    private final UserDeletionService userDeletionService;
    
    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${cloudflare.r2.public-url.avatars}")
    private String avatarPublicUrl;
    
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    
    private final VerificationTokenRepository verificationTokenRepository;
    
    private final EmailService emailService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, 
    		JwtUtils jwtUtils, StorageService storageService, 
    		PasswordResetTokenRepository passwordResetTokenRepository, EmailService emailService, CacheManager cacheManager, VerificationTokenRepository verificationTokenRepository, UserDeletionService userDeletionService) {
    	this.userRepository = userRepository;
    	this.passwordEncoder = passwordEncoder;
    	this.jwtUtils = jwtUtils;
    	this.storageService = storageService;
    	this.passwordResetTokenRepository = passwordResetTokenRepository;
    	this.emailService = emailService;
    	this.cacheManager = cacheManager;
    	this.verificationTokenRepository = verificationTokenRepository;
    	this.userDeletionService = userDeletionService;
    }

    public void registerUser(AuthRequest request) {
    	validateUsername(request.getName());
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
        	throw new UserAlreadyExistsException("Email already in use");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setProvider(AuthProvider.LOCAL);
        user.setEnabled(false);
        userRepository.saveAndFlush(user);
        
        String token = UUID.randomUUID().toString();
        VerificationToken vToken = new VerificationToken();
        vToken.setToken(token);
        vToken.setUser(user);
        vToken.setExpiryDate(LocalDateTime.now().plusHours(24));
        verificationTokenRepository.save(vToken);
        
        emailService.sendVerificationEmail(user.getEmail(), token);

        log.info("User registered. Verification email sent to: {}", user.getEmail());
    }

    public AuthTokens loginUser(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Login failed! User not found"));
        
        if (!user.getEnabled()) {
            throw new IllegalStateException("Please verify your email before logging in.");
        }

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

        PasswordResetToken tokenEntity = passwordResetTokenRepository.findByUser(user)
                .orElse(new PasswordResetToken());

        tokenEntity.setUser(user);
        tokenEntity.setToken(UUID.randomUUID().toString());
        tokenEntity.setExpiryDate(LocalDateTime.now().plusMinutes(15));

        passwordResetTokenRepository.save(tokenEntity);

        emailService.sendResetEmail(user.getEmail(), tokenEntity.getToken());
    }

    public void resetPassword(String token, String newPassword) {
    	if (newPassword == null || newPassword.trim().isEmpty()) {
    		throw new IllegalArgumentException("Password cannot be empty");
    	}
    	if (newPassword.length() < 6) {
    		throw new IllegalArgumentException("Password must be at least 6 characters long");
    	}

    	PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
    			.orElseThrow(() -> new IllegalArgumentException("Invalid token"));

    	if (resetToken.isExpired()) {
    		passwordResetTokenRepository.delete(resetToken);
    		throw new IllegalArgumentException("Token has expired");
    	}

    	User user = resetToken.getUser();
    	user.setPassword(passwordEncoder.encode(newPassword));
    	userRepository.save(user);
    	
    	evictAllUserCaches(user.getEmail());

    	passwordResetTokenRepository.delete(resetToken);
    }
    
    @Transactional
    public AuthTokens verifyUser(String token) {
        VerificationToken vToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification link."));

        if (vToken.isExpired()) {
        	verificationTokenRepository.delete(vToken);
            throw new IllegalArgumentException("Verification link expired.");
        }

        User user = vToken.getUser();
        user.setEnabled(true);
        userRepository.saveAndFlush(user);
        
        evictAllUserCaches(user.getEmail());
        
        AuthTokens tokens = generateAuthTokens(user.getEmail());
        
        verificationTokenRepository.delete(vToken);
        
        return tokens;
    }
    
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (Boolean.TRUE.equals(user.getEnabled())) {
            throw new IllegalStateException("Account is already verified. Please log in.");
        }

        verificationTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();
        VerificationToken vToken = new VerificationToken();
        vToken.setToken(token);
        vToken.setUser(user);
        vToken.setExpiryDate(LocalDateTime.now().plusHours(24));
        verificationTokenRepository.save(vToken);

        emailService.sendVerificationEmail(user.getEmail(), token);
        
        log.info("Verification email resent to: {}", user.getEmail());
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
    
    @Caching(evict = {
            @CacheEvict(value = "users", key = "#email"),
            @CacheEvict(value = "userEntities", key = "#email")
    })
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
     * OAuth User Sync
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
                    newUser.setEnabled(true);
                    return newUser;
                });

        boolean needsUpdate = false;
        
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            user.setEnabled(true);
            needsUpdate = true;
            log.info("User {} auto-verified via {} login.", email, provider);
        }

        if (user.getName() == null || !user.getName().equals(name)) {
            user.setName(name);
            needsUpdate = true;
        }

        if (user.getImageUrl() == null || (!user.getImageUrl().contains("r2") && !user.getImageUrl().equals(imageUrl))) {
            if (imageUrl != null && !imageUrl.isBlank()) {
                try {
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
    	if (email == null) return;
    	String normalizedEmail = email.toLowerCase();
    	
    	Cache users = cacheManager.getCache("users");
        Cache userEntities = cacheManager.getCache("userEntities");
        
        if (users != null) users.evict(normalizedEmail);
        if (userEntities != null) userEntities.evict(normalizedEmail);
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
        response.setEnabled(Boolean.TRUE.equals(user.getEnabled()));
        
        // Set deletion warning info
        if (Boolean.TRUE.equals(user.getPendingDeletion())) {
            DeletionWarning warning = userDeletionService.checkPendingDeletion(user.getEmail());
            response.setPendingDeletion(true);
            response.setDaysUntilDeletion(warning.daysRemaining);
        } else {
            response.setPendingDeletion(false);
            response.setDaysUntilDeletion(0);
        }
        
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
