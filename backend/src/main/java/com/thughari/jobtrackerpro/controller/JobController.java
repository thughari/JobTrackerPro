package com.thughari.jobtrackerpro.controller;

import com.thughari.jobtrackerpro.dto.DashboardResponse;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.service.JobService;

import org.springframework.data.domain.Page;
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
    public ResponseEntity<Page<JobDTO>> getAllJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "All Statuses") String status) {
        
        String email = getAuthenticatedEmail();
        
        return ResponseEntity.ok(jobService.getAllJobsPaged(email, page, size, sort, dir, search, status));
    }


    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        String email = getAuthenticatedEmail();
        return ResponseEntity.ok(jobService.getDashboardData(email));
    }

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
        return ((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).toLowerCase();
    }
}