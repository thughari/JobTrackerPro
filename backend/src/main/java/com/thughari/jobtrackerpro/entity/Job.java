package com.thughari.jobtrackerpro.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;


@Data
@Entity
@Table(name = "jobs", indexes = {
		@Index(name = "idx_jobs_user_email_updated_at", columnList = "userEmail, updatedAt DESC"),
		@Index(name = "idx_jobs_user_status", columnList = "userEmail, status")
})
public class Job {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(columnDefinition = "uuid")		//@Column(columnDefinition = "VARCHAR(36)") -- for mySQL
	private UUID id;

	@Column(nullable = false)
	private String userEmail; 

	private String company;
	private String role;
	private String location;
	private LocalDateTime appliedDate;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	private String status;
	private Integer stage;
	private String stageStatus;

	private Double salaryMin;
	private Double salaryMax;

	@Column(length = 2048)
	private String url;

	@Column(columnDefinition = "TEXT")
	private String notes;
}