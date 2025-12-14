package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.ChartData;
import com.thughari.jobtrackerpro.dto.DashboardResponse;
import com.thughari.jobtrackerpro.dto.DashboardStatsDTO;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.dto.JobDataResponse;
import com.thughari.jobtrackerpro.entity.Job;
import com.thughari.jobtrackerpro.repo.JobRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    @Cacheable(value = "jobData", key = "#email")
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
        // Just the list, no heavy math
        return jobRepository.findByUserEmailOrderByDateDesc(email)
                .stream().map(this::convertToDto).collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    // @Cacheable(value = "jobs", key = "#email") // Optional: Cache the list
    public List<JobDTO> getAllJobs(String email) {
        return jobRepository.findByUserEmailOrderByDateDesc(email)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public DashboardResponse getDashboardData(String email) {
        List<Job> jobs = jobRepository.findByUserEmailOrderByDateDesc(email);
        DashboardResponse response = new DashboardResponse();

        // A. Summary Stats
        long total = jobs.size();
        long active = jobs.stream().filter(j -> !j.getStatus().equals("Rejected") && !j.getStatus().equals("Offer Received")).count();
        long interviews = jobs.stream().filter(j -> j.getStatus().equals("Interview Scheduled") || j.getStage() >= 3).count();
        long offers = jobs.stream().filter(j -> j.getStatus().equals("Offer Received")).count();
        response.setStats(new DashboardStatsDTO(total, active, interviews, offers));

        // B. Status Chart
        Map<String, Long> statusMap = jobs.stream()
            .collect(Collectors.groupingBy(Job::getStatus, Collectors.counting()));
        response.setStatusChart(mapToChartData(statusMap));

        // C. Monthly Chart (Chronological)
        // Note: Using LinkedHashMap to keep insertion order if you sort beforehand
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yy");
        Map<String, Long> monthMap = jobs.stream()
            .sorted(java.util.Comparator.comparing(Job::getDate)) // Sort by date first
            .collect(Collectors.groupingBy(
                job -> job.getDate().format(formatter),
                LinkedHashMap::new, 
                Collectors.counting()
            ));
        response.setMonthlyChart(mapToChartData(monthMap));

        // D. Interview Progress
        long interviewCount = jobs.stream().filter(j -> j.getStage() >= 3).count();
        response.setInterviewChart(List.of(
            new ChartData("Interviewed", interviewCount),
            new ChartData("Not Interviewed", total > 0 ? total - interviewCount : 0)
        ));

        return response;
    }
    
    @Transactional(readOnly = true)
    // @Cacheable(value = "jobStats", key = "#email") // Optional: Cache the stats
    public DashboardStatsDTO getStats(String email) {
        DashboardStatsDTO stats = jobRepository.getStatsByEmail(email);
        
        // Handle case where user has 0 jobs (SUM returns null in SQL)
        if (stats.getTotalApplications() == 0) {
            return new DashboardStatsDTO(0, 0, 0, 0);
        }
        return stats;
    }

    @CacheEvict(value = "jobData", key = "#email")
    public JobDTO createJob(JobDTO dto, String email) {
        Job job = convertToEntity(dto);
        job.setUserEmail(email);
        return convertToDto(jobRepository.save(job));
    }

    @CacheEvict(value = "jobData", key = "#email")
    public JobDTO updateJob(UUID id, JobDTO dto, String email) {
        Job existingJob = jobRepository.findById(id)
                .filter(job -> job.getUserEmail().equals(email))
                .orElseThrow(() -> new RuntimeException("Job not found or unauthorized"));

        BeanUtils.copyProperties(dto, existingJob, "id", "userEmail");
        return convertToDto(jobRepository.save(existingJob));
    }

    @CacheEvict(value = "jobData", key = "#email")
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
    
    private List<ChartData> mapToChartData(Map<String, Long> map) {
        return map.entrySet().stream()
            .map(e -> new ChartData(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }
    
}