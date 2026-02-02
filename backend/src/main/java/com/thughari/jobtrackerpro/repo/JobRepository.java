package com.thughari.jobtrackerpro.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thughari.jobtrackerpro.dto.DashboardStatsDTO;
import com.thughari.jobtrackerpro.entity.Job;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

	List<Job> findByUserEmailOrderByUpdatedAtDesc(String userEmail);
	
	@Query("SELECT j FROM Job j WHERE j.userEmail = :email " +
	           "AND (:status IS NULL OR j.status = :status) " +
	           "AND (LOWER(j.company) LIKE LOWER(CONCAT('%', :search, '%')) " +
	           "OR LOWER(j.role) LIKE LOWER(CONCAT('%', :search, '%')) " +
	           "OR LOWER(j.location) LIKE LOWER(CONCAT('%', :search, '%')))")
	    Page<Job> findWithFilters(
	        @Param("email") String email, 
	        @Param("search") String search, 
	        @Param("status") String status, 
	        Pageable pageable
	    );

	@Query("""
			   SELECT new com.thughari.jobtrackerpro.dto.DashboardStatsDTO(
			       COUNT(j), 
			       SUM(CASE WHEN j.status NOT IN ('Rejected', 'Offer Received') THEN 1L ELSE 0L END),
			       SUM(CASE WHEN j.status = 'Interview Scheduled' OR j.stage >= 3 THEN 1L ELSE 0L END),
			       SUM(CASE WHEN j.status = 'Interview Scheduled' THEN 1L ELSE 0L END),
			       SUM(CASE WHEN j.status = 'Offer Received' THEN 1L ELSE 0L END)
			   )
			   FROM Job j
			   WHERE j.userEmail = :email
			""")
	DashboardStatsDTO getStatsByEmail(@Param("email") String email);

}