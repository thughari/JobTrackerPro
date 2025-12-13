package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.DashboardStatsDTO;
import com.thughari.jobtrackerpro.entity.Job;
import com.thughari.jobtrackerpro.repo.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class DashboardService {

	private final JobRepository jobRepository;
	private final Executor executor;

	public DashboardService(JobRepository jobRepository, @Qualifier("dashboardExecutor") Executor executor) {
		this.jobRepository = jobRepository;
		this.executor = executor;
	}

	public DashboardStatsDTO getStats(String email) {
		List<Job> jobs = jobRepository.findByUserEmailOrderByDateDesc(email);


		CompletableFuture<Long> activeFuture = CompletableFuture.supplyAsync(() -> 
		jobs.stream().filter(j -> !j.getStatus().equals("Rejected") && !j.getStatus().equals("Offer Received")).count(),
		executor
				);

		CompletableFuture<Long> interviewFuture = CompletableFuture.supplyAsync(() -> 
		jobs.stream().filter(j -> j.getStatus().equals("Interview Scheduled") || j.getStage() >= 3).count(),
		executor
				);

		CompletableFuture<Long> offerFuture = CompletableFuture.supplyAsync(() -> 
		jobs.stream().filter(j -> j.getStatus().equals("Offer Received")).count(),
		executor
				);

		CompletableFuture.allOf(activeFuture, interviewFuture, offerFuture).join();

		try {
			return new DashboardStatsDTO(
					jobs.size(),
					activeFuture.get(),
					interviewFuture.get(),
					offerFuture.get()
					);
		} catch (Exception e) {
			throw new RuntimeException("Error calculating stats", e);
		}
	}
}