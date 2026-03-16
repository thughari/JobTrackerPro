package com.thughari.jobtrackerpro.repo;

import com.thughari.jobtrackerpro.entity.User;

import jakarta.transaction.Transactional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
	
	@Cacheable(value = "userEntities", key = "#email")
	Optional<User> findByEmail(String email);
	
	@Modifying
	@Transactional
	@Query("UPDATE User u SET u.gmailSyncInProgress = true, u.gmailSyncStartedAt = :now WHERE u.email = :email AND (u.gmailSyncInProgress = false OR u.gmailSyncStartedAt < :expiry)")
	int claimSyncLock(@Param("email") String email, @Param("now") LocalDateTime now, @Param("expiry") LocalDateTime expiry);
	
	@Modifying
	@Transactional
	@Query("UPDATE User u SET u.gmailSyncInProgress = false WHERE u.email = :email")
	void releaseSyncLock(@Param("email") String email);

	List<User> findByGmailConnectedTrue();
	
	@Modifying
    @Query("DELETE FROM User u WHERE u.enabled = false AND u.provider = 'LOCAL' AND u.createdAt < :cutoff")
    void deleteUnverifiedUsers(@Param("cutoff") LocalDateTime cutoff);
	
	@Query("SELECT u FROM User u WHERE u.pendingDeletion = true AND u.deletionRequestedAt < :cutoffDate")
	List<User> findAllByPendingDeletionTrueAndDeletionRequestedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
}