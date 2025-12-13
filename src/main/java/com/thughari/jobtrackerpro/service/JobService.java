package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.DashboardStatsDTO;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.dto.JobDataResponse;
import com.thughari.jobtrackerpro.entity.Job;
import com.thughari.jobtrackerpro.repo.JobRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }
    
    @Transactional(readOnly = true)
    public JobDataResponse getFullJobData(String email) {
        List<Job> jobEntities = jobRepository.findByUserEmailOrderByDateDesc(email);

        List<JobDTO> jobDtos = jobEntities.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        long total = jobEntities.size();
        
        long active = jobEntities.stream()
                .filter(j -> !j.getStatus().equals("Rejected") && !j.getStatus().equals("Offer Received"))
                .count();

        long interviews = jobEntities.stream()
                .filter(j -> j.getStatus().equals("Interview Scheduled") || j.getStage() >= 3)
                .count();

        long offers = jobEntities.stream()
                .filter(j -> j.getStatus().equals("Offer Received"))
                .count();

        DashboardStatsDTO stats = new DashboardStatsDTO(total, active, interviews, offers);

        return new JobDataResponse(jobDtos, stats);
    }

    @Transactional(readOnly = true)
    public List<JobDTO> getUserJobs(String email) {
        return jobRepository.findByUserEmailOrderByDateDesc(email)
                .stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public JobDTO createJob(JobDTO dto, String email) {
        Job job = convertToEntity(dto);
        job.setUserEmail(email);
        return convertToDto(jobRepository.save(job));
    }

    public JobDTO updateJob(UUID id, JobDTO dto, String email) {
        Job existingJob = jobRepository.findById(id)
                .filter(job -> job.getUserEmail().equals(email))
                .orElseThrow(() -> new RuntimeException("Job not found or unauthorized"));

        BeanUtils.copyProperties(dto, existingJob, "id", "userEmail");
        return convertToDto(jobRepository.save(existingJob));
    }

    public void deleteJob(UUID id, String email) {
        jobRepository.findById(id)
                .filter(job -> job.getUserEmail().equals(email))
                .ifPresent(jobRepository::delete);
    }

    private JobDTO convertToDto(Job job) {
        JobDTO dto = new JobDTO();
        BeanUtils.copyProperties(job, dto);
        return dto;
    }

    private Job convertToEntity(JobDTO dto) {
        Job job = new Job();
        BeanUtils.copyProperties(dto, job);
        return job;
    }
}