package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.*;
import com.thughari.jobtrackerpro.entity.Job;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.repo.JobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

    @Transactional(readOnly = true)
    @Cacheable(value = "jobList", key = "#email")
    public List<JobDTO> getAllJobs(String email) {
        return jobRepository.findByUserEmailOrderByUpdatedAtDesc(email)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "jobDashboard", key = "#email")
    public DashboardResponse getDashboardData(String email) {
        List<Job> jobs = jobRepository.findByUserEmailOrderByUpdatedAtDesc(email);
        
        DashboardResponse response = new DashboardResponse();

        long total = jobs.size();
        long active = jobs.stream().filter(j -> !j.getStatus().equals("Rejected") && !j.getStatus().equals("Offer Received")).count();
        long interviews = jobs.stream().filter(j -> j.getStatus().equals("Interview Scheduled") || j.getStage() >= 3).count();
        long offers = jobs.stream().filter(j -> j.getStatus().equals("Offer Received")).count();
        
        response.setStats(new DashboardStatsDTO(total, active, interviews, offers));

        Map<String, Long> statusMap = jobs.stream()
            .collect(Collectors.groupingBy(Job::getStatus, Collectors.counting()));
        response.setStatusChart(mapToChartData(statusMap));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yy");
        Map<String, Long> monthMap = jobs.stream()
            .sorted(Comparator.comparing(Job::getAppliedDate))
            .collect(Collectors.groupingBy(
                job -> job.getAppliedDate().format(formatter),
                LinkedHashMap::new, 
                Collectors.counting()
            ));
        response.setMonthlyChart(mapToChartData(monthMap));

        long interviewCount = jobs.stream().filter(j -> j.getStage() >= 3).count();
        response.setInterviewChart(List.of(
            new ChartData("Interviewed", interviewCount),
            new ChartData("Not Interviewed", total > 0 ? total - interviewCount : 0)
        ));

        return response;
    }

    @CacheEvict(value = {"jobList", "jobDashboard"}, key = "#email")
    public JobDTO createJob(JobDTO dto, String email) {
        Job job = convertToEntity(dto);
        job.setUserEmail(email);
        LocalDateTime now = LocalDateTime.now();
        if (job.getAppliedDate() == null) job.setAppliedDate(now);
        job.setUpdatedAt(now); 
        if (job.getStage() == null) job.setStage(1);
        if (job.getStageStatus() == null) job.setStageStatus("active");
        return convertToDto(jobRepository.save(job));
    }

    @CacheEvict(value = {"jobList", "jobDashboard"}, key = "#email")
    public JobDTO updateJob(UUID id, JobDTO dto, String email) {
        Job existingJob = jobRepository.findById(id)
                .filter(job -> job.getUserEmail().equals(email))
                .orElseThrow(() -> new ResourceNotFoundException("Job not found or unauthorized"));
        
        LocalDateTime originalAppliedDate = existingJob.getAppliedDate();
        
        BeanUtils.copyProperties(dto, existingJob, "id", "userEmail", "appliedDate", "updatedAt");
        existingJob.setAppliedDate(originalAppliedDate);
        existingJob.setUpdatedAt(LocalDateTime.now());
        return convertToDto(jobRepository.save(existingJob));
    }

    @CacheEvict(value = {"jobList", "jobDashboard"}, key = "#email")
    public void deleteJob(UUID id, String email) {
        jobRepository.findById(id)
                .filter(job -> job.getUserEmail().equals(email))
                .ifPresent(jobRepository::delete);
    }

    @CacheEvict(value = {"jobList", "jobDashboard"}, key = "#userEmail")
    public void createOrUpdateJob(JobDTO incomingJob, String userEmail) {
        List<Job> userJobs = jobRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail);

        Job existingJob = findBestMatch(userJobs, incomingJob);

        if (existingJob != null) {
            log.info("Found existing job for company: {}. Updating status.", existingJob.getCompany());
            updateExistingJobFromEmail(existingJob, incomingJob);
        } else {
            log.info("No match found. Creating new job for: {}", incomingJob.getCompany());
            Job job = convertToEntity(incomingJob);
            job.setUserEmail(userEmail);
            jobRepository.save(job);
        }
    }
    

    private Job findBestMatch(List<Job> existingJobs, JobDTO incoming) {
        if (incoming.getCompany() == null) return null;
        
        String incomingCompany = incoming.getCompany().toLowerCase().trim();
        String incomingRole = incoming.getRole() != null ? incoming.getRole() : "";

        List<Job> companyMatches = existingJobs.stream()
                .filter(job -> {
                    String dbCompany = job.getCompany().toLowerCase().trim();
                    return dbCompany.contains(incomingCompany) || incomingCompany.contains(dbCompany);
                })
                .collect(Collectors.toList());

        if (companyMatches.isEmpty()) return null;

        List<Job> activeMatches = companyMatches.stream()
                .filter(j -> !j.getStatus().equals("Rejected") && !j.getStatus().equals("Offer Received"))
                .collect(Collectors.toList());

        if (activeMatches.isEmpty()) return null;

        if (activeMatches.size() > 1) {
            return activeMatches.stream()
                    .max((j1, j2) -> {
                        double sim1 = calculateSimilarity(j1.getRole(), incomingRole);
                        double sim2 = calculateSimilarity(j2.getRole(), incomingRole);
                        return Double.compare(sim1, sim2);
                    })
                    .filter(bestMatch -> calculateSimilarity(bestMatch.getRole(), incomingRole) > 0.3)
                    .orElse(activeMatches.get(0));
        }

        return activeMatches.get(0);
    }

    private void updateExistingJobFromEmail(Job existingJob, JobDTO incoming) {
        existingJob.setStatus(incoming.getStatus());
        existingJob.setStage(incoming.getStage());
        existingJob.setStageStatus(incoming.getStageStatus());
        
        String newNote = "\n[" + LocalDateTime.now().format(fmt) + "] Update via Email: " + incoming.getNotes();
        String currentNotes = existingJob.getNotes() != null ? existingJob.getNotes() : "";
        existingJob.setNotes(currentNotes + newNote);

        if ((existingJob.getUrl() == null || existingJob.getUrl().isEmpty()) && incoming.getUrl() != null) {
            existingJob.setUrl(incoming.getUrl());
        }
        
        existingJob.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(existingJob);
    }
    

    private double calculateSimilarity(String role1, String role2) {
        if (role1 == null || role2 == null) return 0.0;

        Set<String> set1 = tokenize(role1);
        Set<String> set2 = tokenize(role2);

        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    private Set<String> tokenize(String text) {
        String[] words = text.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+");
        Set<String> uniqueWords = new HashSet<>();
        for (String w : words) {
            if (!w.isEmpty() && w.length() > 1) { 
                uniqueWords.add(w);
            }
        }
        return uniqueWords;
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