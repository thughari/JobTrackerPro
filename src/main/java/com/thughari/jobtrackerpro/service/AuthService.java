package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.*;
import com.thughari.jobtrackerpro.entity.AuthProvider;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.security.JwtUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    
    @Value("${app.base-url}")
    private String baseUrl;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    public AuthResponse registerUser(AuthRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setProvider(AuthProvider.LOCAL);
        userRepository.save(user);

        String token = jwtUtils.generateToken(user.getEmail());
        return new AuthResponse(token);
    }

    public AuthResponse loginUser(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid password");
        }

        String token = jwtUtils.generateToken(user.getEmail());
        return new AuthResponse(token);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .map(this::mapToProfileResponse)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional(readOnly = true)
    public byte[] getProfileImage(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getProfileImage() == null) {
            throw new IllegalArgumentException("No image found for user");
        }
        return user.getProfileImage();
    }

    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(request.getName());
        
        String newImageUrl = request.getImageUrl();
        
        boolean isKeepingInternalImage = newImageUrl != null && 
                                         newImageUrl.contains("/api/auth/profile/image/" + user.getId());

        if (isKeepingInternalImage) {
            user.setImageUrl(newImageUrl);
        } else {
            user.setImageUrl(newImageUrl);
            user.setProfileImage(null); 
        }
        

        userRepository.save(user);
        return mapToProfileResponse(user);
    }

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
    
    
    public UserProfileResponse updateProfileAtomic(String email, String name, String imageUrl, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(name);

        try {
            if (file != null && !file.isEmpty()) {
                user.setProfileImage(file.getBytes());
                user.setImageUrl(baseUrl + "/api/auth/profile/image/" + user.getId());
            } 
            else if (imageUrl != null && !imageUrl.isEmpty()) {
                if (!imageUrl.contains("/api/auth/profile/image/")) {
                    user.setProfileImage(null);
                }
                user.setImageUrl(imageUrl);
            }
            else {
                //TODO: Only clear if we explicitly sent empty string and no file
            }
        } catch (IOException e) {
            throw new RuntimeException("Error processing file", e);
        }

        userRepository.save(user);
        return mapToProfileResponse(user);
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setImageUrl(user.getImageUrl());
        response.setProvider(user.getProvider().toString());
        response.setHasPassword(user.getPassword() != null && !user.getPassword().isEmpty());
        return response;
    }
}