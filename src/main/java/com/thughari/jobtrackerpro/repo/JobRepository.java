package com.thughari.jobtrackerpro.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.thughari.jobtrackerpro.dto.DashboardStatsDTO;
import com.thughari.jobtrackerpro.entity.Job;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

	List<Job> findByUserEmailOrderByDateDesc(String userEmail);

	@Query("""
			    SELECT new com.thughari.jobtrackerpro.dto.DashboardStatsDTO(
			        COUNT(j),
			        SUM(CASE WHEN j.status NOT IN ('Rejected', 'Offer Received') THEN 1 ELSE 0 END),
			        SUM(CASE WHEN j.status = 'Interview Scheduled' OR j.stage >= 3 THEN 1 ELSE 0 END),
			        SUM(CASE WHEN j.status = 'Offer Received' THEN 1 ELSE 0 END)
			    )
			    FROM Job j
			    WHERE j.userEmail = :email
			""")
	DashboardStatsDTO getStatsByEmail(@Param("email") String email);

}