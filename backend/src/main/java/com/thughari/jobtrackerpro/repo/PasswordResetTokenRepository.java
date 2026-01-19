package com.thughari.jobtrackerpro.repo;

import com.thughari.jobtrackerpro.entity.PasswordResetToken;
import com.thughari.jobtrackerpro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUser(User user);
	Optional<PasswordResetToken> findByUser(User user);
}