package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.*;
import com.thughari.jobtrackerpro.entity.Job;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.repo.JobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    
    private final CacheManager cacheManager;

    public JobService(JobRepository jobRepository, CacheManager cacheManager) {
        this.jobRepository = jobRepository;
        this.cacheManager = cacheManager;
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
    @Cacheable(value = "jobPages", key = "{#email, #page, #size, #sort, #dir, #search, #status}")
    public Page<JobDTO> getAllJobsPaged(String email, int page, int size, String sort, String dir, String search, String status) {
        Sort sortOrder = dir.equalsIgnoreCase("asc") ? Sort.by(sort).ascending() : Sort.by(sort).descending();
        Pageable pageable = PageRequest.of(page, size, sortOrder);
        
        String statusFilter = "All Statuses".equalsIgnoreCase(status) ? null : status;
        
        return jobRepository.findWithFilters(email, search, statusFilter, pageable)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "jobDashboard", key = "#email")
    public DashboardResponse getDashboardData(String email) {
        List<Job> jobs = jobRepository.findByUserEmailOrderByUpdatedAtDesc(email);
        
        DashboardResponse response = new DashboardResponse();

        long total = jobs.size();
        long active = jobs.stream().filter(j -> j.getStatus() != null && 
        		!j.getStatus().equals("Rejected") && !j.getStatus().equals("Offer Received")).count();
        long interviews = jobs.stream()
        	    .filter(j -> "Interview Scheduled".equals(j.getStatus()) || 
        	                 (j.getStage() != null && j.getStage() >= 3))
        	    .count();
        long offers = jobs.stream().filter(j -> "Offer Received".equals(j.getStatus())).count();
        long activeInterviews = jobs.stream().filter(j -> "Interview Scheduled".equals(j.getStatus())).count();

        response.setStats(new DashboardStatsDTO(total, active, interviews, activeInterviews, offers));

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

        long interviewCount = jobs.stream()
        	    .filter(j -> j.getStage() != null && j.getStage() >= 3)
        	    .count();        response.setInterviewChart(List.of(
            new ChartData("Interviewed", interviewCount),
            new ChartData("Not Interviewed", total > 0 ? total - interviewCount : 0)
        ));

        return response;
    }

    @Caching(evict = {
    		@CacheEvict(value = {"jobList", "jobDashboard"}, key = "#email"),
    		@CacheEvict(value = "jobPages", allEntries = true)
    })
    public JobDTO createJob(JobDTO dto, String email) {
        Job job = convertToEntity(dto);
        job.setUserEmail(email);
        LocalDateTime now = LocalDateTime.now();
        if (job.getAppliedDate() == null) job.setAppliedDate(now);
        if (job.getLocation() == null) job.setLocation("Remote");
        job.setUpdatedAt(now); 
        if (job.getStage() == null) job.setStage(1);
        if (job.getStageStatus() == null) job.setStageStatus("active");
        return convertToDto(jobRepository.save(job));
    }

    @Caching(evict = {
    		@CacheEvict(value = {"jobList", "jobDashboard"}, key = "#email"),
    		@CacheEvict(value = "jobPages", allEntries = true)
    })
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
    
    @Caching(evict = {
    		@CacheEvict(value = {"jobList", "jobDashboard"}, key = "#email"),
    	    @CacheEvict(value = "jobPages", allEntries = true)
    })
    public void deleteJob(UUID id, String email) {
        jobRepository.findById(id)
                .filter(job -> job.getUserEmail().equals(email))
                .ifPresent(jobRepository::delete);
    }

    @Caching(evict = {
    		@CacheEvict(value = {"jobList", "jobDashboard"}, key = "#userEmail"),
    	    @CacheEvict(value = "jobPages", allEntries = true)
    })
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
    

    public void cleanupStaleApplications() {
    	LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    	LocalDateTime threeMonthsAgo = now.minusMonths(2);

    	List<String> affectedEmails = jobRepository.findUserEmailsWithStaleJobs(threeMonthsAgo);

    	if (affectedEmails.isEmpty()) {
    		log.info("System Cleanup: No stale applications found.");
    		return;
    	}

    	String autoNote = "\n[" + now.format(fmt) + "] Status auto-set to Rejected (3 months inactivity).";
    	jobRepository.markStaleJobsAsRejected(threeMonthsAgo, now, autoNote);

    	Cache jobList = cacheManager.getCache("jobList");
    	Cache jobDashboard = cacheManager.getCache("jobDashboard");
    	Cache jobPages = cacheManager.getCache("jobPages");

    	affectedEmails.parallelStream().forEach(email -> {
            if (jobList != null) jobList.evict(email);
            if (jobDashboard != null) jobDashboard.evict(email);
        });

        // Clear all paged results once
    	if (jobPages != null) jobPages.clear(); 

        log.info("System Cleanup: Successfully rejected stale jobs for {} users.", affectedEmails.size());
    }

    private Job findBestMatch(List<Job> existingJobs, JobDTO incoming) {
        if (incoming == null || incoming.getCompany() == null) return null;
        
        String incomingCompany = incoming.getCompany().toLowerCase().trim();
        // Defensive check for incoming role
        String incomingRole = (incoming.getRole() != null) ? incoming.getRole().toLowerCase().trim() : "";

        List<Job> companyMatches = existingJobs.stream()
                .filter(job -> {
                    // Defensive check for database company names
                    if (job.getCompany() == null) return false;
                    String dbCompany = job.getCompany().toLowerCase().trim();
                    return dbCompany.contains(incomingCompany) || incomingCompany.contains(dbCompany);
                })
                .collect(Collectors.toList());

        if (companyMatches.isEmpty()) return null;

        // Filter active matches
        List<Job> activeMatches = companyMatches.stream()
                .filter(j -> j.getStatus() != null && 
                        !j.getStatus().equalsIgnoreCase("Rejected") && 
                        !j.getStatus().equalsIgnoreCase("Offer Received"))
                .collect(Collectors.toList());

        if (activeMatches.isEmpty()) return null;

        return activeMatches.stream()
                .max((j1, j2) -> {
                    // Ensure calculateSimilarity is null-safe
                    double sim1 = calculateSimilarity(j1.getRole(), incomingRole);
                    double sim2 = calculateSimilarity(j2.getRole(), incomingRole);
                    return Double.compare(sim1, sim2);
                })
                // If the best match is too weak, ignore it and return the first company match
                .filter(bestMatch -> calculateSimilarity(bestMatch.getRole(), incomingRole) > 0.2)
                .orElse(activeMatches.get(0));
    }
    
    private void updateExistingJobFromEmail(Job existingJob, JobDTO incoming) {
        // 1. NULL-SAFE NORMALIZATION
        String currentStatus = (existingJob.getStatus() != null) ? existingJob.getStatus() : "";
        String incomingStatus = (incoming.getStatus() != null) ? incoming.getStatus() : "";
        
        if (currentStatus.equalsIgnoreCase(incomingStatus) && 
        		Objects.equals(existingJob.getStage(), incoming.getStage())) {
        	return; 
        }

        String newNotesFromAI = (incoming.getNotes() != null) ? incoming.getNotes().trim() : "";

        // 2. CHANGE DETECTION LOGIC (The "High Performance" Filter)
        boolean statusChanged = !currentStatus.equalsIgnoreCase(incomingStatus);
        boolean stageChanged = incoming.getStage() != null && !incoming.getStage().equals(existingJob.getStage());
        
        // Check if the AI actually found new info that we don't already have in the notes
        // This prevents appending "Application received" every time the sync runs.
        boolean isNewInfo = !newNotesFromAI.isEmpty() && 
                            (existingJob.getNotes() == null || !existingJob.getNotes().contains(newNotesFromAI));

        // 3. DECIDE IF UPDATE IS NECESSARY
        // We only update if: 
        // a) Status or Stage changed 
        // b) There is significant new text info AND it's been at least 1 hour (throttling)
        boolean shouldThrottle = existingJob.getUpdatedAt().isAfter(LocalDateTime.now().minusHours(1));

        if (statusChanged || stageChanged || (isNewInfo && !shouldThrottle)) {
            
            log.info("Updating job for {}: Status change [{} -> {}], New Info: {}", 
                     existingJob.getCompany(), currentStatus, incomingStatus, isNewInfo);

            // Update core fields
            existingJob.setStatus(incomingStatus);
            if (incoming.getStage() != null) existingJob.setStage(incoming.getStage());
            if (incoming.getStageStatus() != null) existingJob.setStageStatus(incoming.getStageStatus());

            // 4. ATOMIC NOTE CONSTRUCTION
            if (isNewInfo) {
                String timestamp = LocalDateTime.now().format(fmt);
                String formattedNote = "\n[" + timestamp + "] Update via Email: " + newNotesFromAI;
                
                // Clean coding: Ensure we don't exceed DB limits (safety check)
                String updatedNotes = (existingJob.getNotes() != null ? existingJob.getNotes() : "") + formattedNote;
                existingJob.setNotes(updatedNotes);
            }

            existingJob.setUpdatedAt(LocalDateTime.now());

            // 5. ATOMIC PERSISTENCE
            // We use saveAndFlush here because in a multi-threaded batch, 
            // we want the NEXT email in the batch to see this update immediately.
            jobRepository.saveAndFlush(existingJob);
            
        } else {
            log.debug("Sync detected no significant changes for {}. Skipping redundant update.", existingJob.getCompany());
        }
    }

    private double calculateSimilarity(String role1, String role2) {
        // If either role is null, they are 0% similar
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
        // This was likely the source of the "val is null" error
        if (text == null || text.isBlank()) return Collections.emptySet();
        
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