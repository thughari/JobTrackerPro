package com.thughari.jobtrackerpro.repo;

import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    
    Optional<VerificationToken> findByToken(String token);

    void deleteByUser(User user);
    @Modifying
    @Query("DELETE FROM VerificationToken v WHERE v.expiryDate < :now")
    void deleteAllExpired(@Param("now") LocalDateTime now);
    
}