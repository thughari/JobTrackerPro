package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.DeletionWarning;
import com.thughari.jobtrackerpro.entity.CareerResource;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.interfaces.StorageService;
import com.thughari.jobtrackerpro.repo.CareerResourceRepository;
import com.thughari.jobtrackerpro.repo.JobRepository;
import com.thughari.jobtrackerpro.repo.PasswordResetTokenRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.repo.VerificationTokenRepository;
import com.thughari.jobtrackerpro.util.CacheEvictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@Slf4j
public class UserDeletionService {

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final GmailIntegrationService gmailIntegrationService;
    private final CareerResourceRepository careerResourceRepository;
    private final StorageService storageService;
    private final CacheEvictService cacheEvictService;
    private final CacheManager cacheManager;

    public UserDeletionService(UserRepository userRepository, JobRepository jobRepository,
                             PasswordResetTokenRepository passwordResetTokenRepository,
                             VerificationTokenRepository verificationTokenRepository,
                             GmailIntegrationService gmailIntegrationService,
                             CareerResourceRepository careerResourceRepository,
                             StorageService storageService,
                             CacheEvictService cacheEvictService,
                             CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.gmailIntegrationService = gmailIntegrationService;
        this.careerResourceRepository = careerResourceRepository;
        this.storageService = storageService;
        this.cacheEvictService = cacheEvictService;
        this.cacheManager = cacheManager;
    }

    /**
     * Request account and data deletion after 3 days
     */
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#email"),
        @CacheEvict(value = "userEntities", key = "#email")
    })
    public void requestDeletion(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPendingDeletion(true);
        user.setDeletionRequestedAt(LocalDateTime.now());
        userRepository.saveAndFlush(user);

        log.info("Deletion requested for user: {}. Will be deleted in 3 days.", email);
    }

    /**
     * Cancel deletion if user logs in during grace period
     */
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#email"),
        @CacheEvict(value = "userEntities", key = "#email")
    })
    public void cancelDeletion(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (Boolean.TRUE.equals(user.getPendingDeletion())) {
            user.setPendingDeletion(false);
            user.setDeletionRequestedAt(null);
            userRepository.saveAndFlush(user);
            log.info("Deletion cancelled for user: {}", email);
        }
    }

    /**
     * Check if user has pending deletion and return days remaining
     */
    public DeletionWarning checkPendingDeletion(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (Boolean.FALSE.equals(user.getPendingDeletion()) || user.getDeletionRequestedAt() == null) {
            return new DeletionWarning(false, 0);
        }

        LocalDateTime deletionDate = user.getDeletionRequestedAt().plusDays(3);
        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), deletionDate);
        
        return new DeletionWarning(true, Math.max(0, daysRemaining));
    }

    /**
     * Permanently delete user and all associated data
     * Ensures complete cleanup across all systems
     */
    @Transactional
    @Async
    public void deleteUserCompletely(String email) {
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        try {
            // Disconnect Gmail if connected
            if (Boolean.TRUE.equals(user.getGmailConnected())) {
                try {
                    gmailIntegrationService.disconnectGmail(email);
                } catch (Exception e) {
                    log.warn("Failed to disconnect Gmail for user: {}", email, e);
                }
            }

            // Delete user's profile picture from Cloudflare R2
            if (user.getImageUrl() != null && !user.getImageUrl().isEmpty()) {
                try {
                    // Only delete if it's a managed file (contains r2.dev or our API domain)
                    if (user.getImageUrl().contains("r2.dev") || user.getImageUrl().contains("api/storage")) {
                        storageService.deleteFile(user.getImageUrl());
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete profile picture for user: {}", email, e);
                }
            }

            // Delete all career resources and their files
            List<CareerResource> userResources = careerResourceRepository.findAllBySubmittedByEmailOrderByCreatedAtDesc(email);
            for (CareerResource resource : userResources) {
                try {
                    // Delete the file if it's a FILE type resource
                    if ("FILE".equals(resource.getResourceType()) && resource.getUrl() != null) {
                        storageService.deleteFile(resource.getUrl());
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete career resource file: {} for user: {}", resource.getUrl(), email, e);
                }
            }
            // Delete career resource records
            careerResourceRepository.deleteAll(userResources);

            // Delete all jobs
            jobRepository.deleteByUserEmail(email);

            // Delete verification tokens
            verificationTokenRepository.deleteByUser(user);

            // Delete password reset tokens
            passwordResetTokenRepository.deleteByUser(user);

            // Delete user record
            userRepository.delete(user);
            userRepository.flush();

            // Evict all caches for this user
            cacheEvictService.evictAllForUser(email);

            clearUserCaching(email);

            log.info("User completely deleted: {} - All associated data removed", email);

        } catch (Exception e) {
            log.error("Error during user deletion for: {}", email, e);
            throw e;
        }
    }

    /**
     * Clear all caching entries for a user
     */
    private void clearUserCaching(String email) {
        try {
            if (cacheManager != null) {
                cacheManager.getCacheNames().parallelStream()
                    .forEach(cacheName -> {
                        try {
                            var cache = cacheManager.getCache(cacheName);
                            if (cache != null) {
                                cache.evictIfPresent(email);
                            }
                        } catch (Exception e) {
                            log.debug("Could not evict cache: {}", cacheName);
                        }
                    });
            }
        } catch (Exception e) {
            log.warn("Error clearing caches for user: {}", email, e);
        }
    }
}
