package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.*;
import com.thughari.jobtrackerpro.entity.AuthProvider;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.exception.UserAlreadyExistsException;
import com.thughari.jobtrackerpro.exception.UserNotFoundException;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.security.JwtUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final StorageService storageService;
    
    @Value("${app.base-url}")
    private String baseUrl;
    
    @Value("${CLOUDFLARE_PUBLIC_URL}")
    private String cloudFlarePublicUrl;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtils jwtUtils, StorageService storageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.storageService = storageService;
    }

    public AuthResponse registerUser(AuthRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
        	throw new UserAlreadyExistsException("Email already in use");
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
                .orElseThrow(() -> new ResourceNotFoundException("Login failed! User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Login failed! Invalid password");
        }

        String token = jwtUtils.generateToken(user.getEmail());
        return new AuthResponse(token);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .map(this::mapToProfileResponse)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
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
        
        String oldImageUrl = user.getImageUrl();
        String newR2Url = null;

        user.setName(name);

        if (file != null && !file.isEmpty()) {
            newR2Url = storageService.uploadFile(file, user.getId().toString());
        }
        else if (imageUrl != null && !imageUrl.isEmpty()) {
        	
        	if (imageUrl.startsWith(cloudFlarePublicUrl) || imageUrl.contains(baseUrl)) {
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