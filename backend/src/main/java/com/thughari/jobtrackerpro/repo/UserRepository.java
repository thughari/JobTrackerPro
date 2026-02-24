package com.thughari.jobtrackerpro.repo;

import com.thughari.jobtrackerpro.entity.User;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
	
	@Cacheable(value = "userEntities", key = "#email")
	Optional<User> findByEmail(String email);
	
	@Modifying
	@Query("UPDATE User u SET u.gmailSyncInProgress = true WHERE u.email = :email AND u.gmailSyncInProgress = false")
	int claimSyncLock(@Param("email") String email);
	
	@Modifying
	@Query("UPDATE User u SET u.gmailSyncInProgress = false WHERE u.email = :email")
	void releaseSyncLock(@Param("email") String email);

	List<User> findByGmailConnectedTrue();
}