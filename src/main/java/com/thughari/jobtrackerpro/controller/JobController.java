package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.DashboardResponse;
import com.thughari.jobtrackerpro.dto.DashboardStatsDTO;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.dto.JobDataResponse;
import com.thughari.jobtrackerpro.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }
    
    @GetMapping
    public ResponseEntity<List<JobDTO>> getAllJobs() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(jobService.getUserJobs(email));
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getStats() {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(jobService.getStats(email));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(jobService.getDashboardData(email));
    }
    
//    @GetMapping
//    public ResponseEntity<JobDataResponse> getJobData() {
//    	String email = getAuthenticatedEmail();
//        return ResponseEntity.ok(jobService.getFullJobData(email));
//    }

    @PostMapping
    public ResponseEntity<JobDTO> createJob(@RequestBody JobDTO jobDTO) {
    	String email = getAuthenticatedEmail();
        return ResponseEntity.ok(jobService.createJob(jobDTO, email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobDTO> updateJob(@PathVariable UUID id, @RequestBody JobDTO jobDTO) {
    	String email = getAuthenticatedEmail();
        return ResponseEntity.ok(jobService.updateJob(id, jobDTO, email));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
    	String email = getAuthenticatedEmail();
        jobService.deleteJob(id, email);
        return ResponseEntity.noContent().build();
    }
    
    private String getAuthenticatedEmail() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    
}