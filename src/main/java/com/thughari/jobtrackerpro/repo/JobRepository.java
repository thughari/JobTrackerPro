package com.thughari.jobtrackerpro.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.thughari.jobtrackerpro.entity.Job;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
	List<Job> findByUserEmailOrderByDateDesc(String userEmail);
}