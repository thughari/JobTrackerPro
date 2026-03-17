package com.thughari.jobtrackerpro.repo;

import com.thughari.jobtrackerpro.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    Optional<InterviewSession> findByIdAndUserEmail(UUID id, String userEmail);
}
