package com.thughari.jobtrackerpro.service;

import com.thughari.jobtrackerpro.dto.*;
import com.thughari.jobtrackerpro.entity.Job;
import com.thughari.jobtrackerpro.entity.User;
import com.thughari.jobtrackerpro.exception.ResourceNotFoundException;
import com.thughari.jobtrackerpro.repo.JobRepository;
import com.thughari.jobtrackerpro.repo.UserRepository;
import com.thughari.jobtrackerpro.util.CacheEvictService;
import com.thughari.jobtrackerpro.util.UrlParser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
    
    private final CacheEvictService cacheEvictService;
    
    private final UserRepository userRepository;

    public JobService(JobRepository jobRepository, CacheManager cacheManager,UserRepository userRepository, CacheEvictService cacheEvictService) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.cacheEvictService = cacheEvictService;
    }

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

    @Transactional(readOnly = true)
    @Cacheable(value = "jobList", key = "#email")
    public List<JobDTO> getAllJobs(String email) {
        return jobRepository.findByUserEmailOrderByUpdatedAtDesc(email)
                .parallelStream()
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
        long active = jobs.parallelStream().filter(j -> j.getStatus() != null && 
        		!j.getStatus().equals("Rejected") && !j.getStatus().equals("Offer Received")).count();
        long interviews = jobs.parallelStream()
        	    .filter(j -> "Interview Scheduled".equals(j.getStatus()) || 
        	                 (j.getStage() != null && j.getStage() >= 3))
        	    .count();
        long offers = jobs.parallelStream().filter(j -> "Offer Received".equals(j.getStatus())).count();
        long activeInterviews = jobs.parallelStream().filter(j -> "Interview Scheduled".equals(j.getStatus())).count();

        response.setStats(new DashboardStatsDTO(total, active, interviews, activeInterviews, offers));

        Map<String, Long> statusMap = jobs.parallelStream()
            .collect(Collectors.groupingBy(Job::getStatus, Collectors.counting()));
        response.setStatusChart(mapToChartData(statusMap));

        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yy");
        
        List<Job> jobsForMonthly = jobs.parallelStream()
            .filter(job -> job.getAppliedDate() != null)
            .sorted(Comparator.comparing(Job::getAppliedDate))
            .toList();
        
        List<Job> last6Months = jobsForMonthly.parallelStream()
            .filter(job -> job.getAppliedDate().isAfter(sixMonthsAgo))
            .toList();
        
        List<Job> jobsToChart = last6Months.parallelStream()
            .collect(Collectors.groupingBy(
                job -> job.getAppliedDate().format(formatter),
                Collectors.toList()
            )).size() >= 3 ? last6Months : jobsForMonthly;
        
        Map<String, Long> monthMap = jobsToChart.parallelStream()
            .collect(Collectors.groupingBy(
                job -> job.getAppliedDate().format(formatter),
                LinkedHashMap::new, 
                Collectors.counting()
            ));
        response.setMonthlyChart(mapToChartData(monthMap));

        long interviewCount = jobs.parallelStream()
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
    
    @Transactional
    public void saveBatchResults(String email, List<EmailBatchItem> batchItems, List<JobDTO> extractedJobs) {
    	
    	List<List<String>> batchUrlLists = batchItems.parallelStream()
                .map(item -> UrlParser.extractAndCleanUrls(item.body()))
                .toList();            
        for (JobDTO job : extractedJobs) {
            Integer idx = job.getInputIndex();
            
            if (idx != null && idx >= 0 && idx < batchUrlLists.size()) {
                List<String> originalUrls = batchUrlLists.get(idx);

                if (job.getUrlIndex() != null && job.getUrlIndex() >= 0 && job.getUrlIndex() < originalUrls.size()) {
                    job.setUrl(originalUrls.get(job.getUrlIndex()));
                } 
                else if (job.getUrl() == null || job.getUrl().isEmpty()) {
                    job.setUrl(originalUrls.parallelStream()
                    		.filter(u -> {
                    		    String lower = u.toLowerCase();
                    		    return lower.contains("career") ||
                    		           lower.contains("job") ||
                    		           lower.contains("apply") ||
                    		           lower.contains("/jobs/") ||
                    		           lower.contains("/comm/") ||
                    		           lower.contains("/careers/") ||
                    		           lower.contains("/view/");
                    		})
                        .findFirst().orElse(""));
                }
            }
            
            if (job.getUrl() != null) {
                String lower = job.getUrl().toLowerCase();
                if (lower.contains("unsubscribe") ||
                    lower.contains("privacy") ||
                    lower.contains("help") ||
                    lower.contains("settings")) {
                    job.setUrl("");
                }
            }
            
            job.setUrlIndex(null); 
            job.setInputIndex(null);

            createOrUpdateJob(job, email);
        }
        cacheEvictService.evictAllForUser(email);
    }
    
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
    
    @Transactional
    public void finalizeManualSync(String email, String historyId) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setGmailHistoryId(historyId);
        
        userRepository.saveAndFlush(user); 
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
    	
    	affectedEmails.forEach(cacheEvictService::evictAllForUser);

        log.info("System Cleanup: Successfully rejected stale jobs for {} users.", affectedEmails.size());
    }

    private Job findBestMatch(List<Job> existingJobs, JobDTO incoming) {
        if (incoming == null || incoming.getCompany() == null) return null;
        
        String incomingCompany = incoming.getCompany().toLowerCase().trim();
        String incomingRole = (incoming.getRole() != null) ? incoming.getRole().toLowerCase().trim() : "";

        List<Job> companyMatches = existingJobs.parallelStream()
                .filter(job -> {
                    if (job.getCompany() == null) return false;
                    String dbCompany = job.getCompany().toLowerCase().trim();
                    return dbCompany.contains(incomingCompany) || incomingCompany.contains(dbCompany);
                })
                .collect(Collectors.toList());

        if (companyMatches.isEmpty()) return null;

        List<Job> activeMatches = companyMatches.parallelStream()
                .filter(j -> j.getStatus() != null && 
                        !j.getStatus().equalsIgnoreCase("Rejected") && 
                        !j.getStatus().equalsIgnoreCase("Offer Received"))
                .collect(Collectors.toList());

        if (activeMatches.isEmpty()) return null;

        return activeMatches.parallelStream()
                .max((j1, j2) -> {
                    double sim1 = calculateSimilarity(j1.getRole(), incomingRole);
                    double sim2 = calculateSimilarity(j2.getRole(), incomingRole);
                    return Double.compare(sim1, sim2);
                })
                .filter(bestMatch -> calculateSimilarity(bestMatch.getRole(), incomingRole) > 0.2)
                .orElse(activeMatches.get(0));
    }
    
    private void updateExistingJobFromEmail(Job existingJob, JobDTO incoming) {
        String currentStatus = (existingJob.getStatus() != null) ? existingJob.getStatus() : "";
        String incomingStatus = (incoming.getStatus() != null) ? incoming.getStatus() : "";
        
        LocalDateTime incomingTime = incoming.getUpdatedAt();
        LocalDateTime existingTime = existingJob.getUpdatedAt();
        
        if (incomingTime.isBefore(existingTime) || incomingTime.isEqual(existingTime)) {
            return; 
        }
        
        if (currentStatus.equalsIgnoreCase(incomingStatus) && 
        		Objects.equals(existingJob.getStage(), incoming.getStage())) {
        	return; 
        }

        String newNotesFromAI = (incoming.getNotes() != null) ? incoming.getNotes().trim() : "";

        boolean statusChanged = !currentStatus.equalsIgnoreCase(incomingStatus);
        boolean stageChanged = incoming.getStage() != null && !incoming.getStage().equals(existingJob.getStage());
        
        boolean isNewInfo = !newNotesFromAI.isEmpty() && 
                            (existingJob.getNotes() == null || !existingJob.getNotes().contains(newNotesFromAI));

        boolean shouldThrottle = existingJob.getUpdatedAt().isAfter(LocalDateTime.now().minusHours(1));

        if (statusChanged || stageChanged || (isNewInfo && !shouldThrottle)) {
            
            log.info("Updating job for {}: Status change [{} -> {}], New Info: {}", 
                     existingJob.getCompany(), currentStatus, incomingStatus, isNewInfo);

            existingJob.setStatus(incomingStatus);
            if (incoming.getStage() != null) existingJob.setStage(incoming.getStage());
            if (incoming.getStageStatus() != null) existingJob.setStageStatus(incoming.getStageStatus());
            existingJob.setUpdatedAt(incomingTime);

            if (isNewInfo) {
                String timestamp = LocalDateTime.now().format(fmt);
                String formattedNote = "\n[" + timestamp + "] Update via Email: " + newNotesFromAI;
                
                String updatedNotes = (existingJob.getNotes() != null ? existingJob.getNotes() : "") + formattedNote;
                existingJob.setNotes(updatedNotes);
            }

            existingJob.setUpdatedAt(LocalDateTime.now());

            jobRepository.saveAndFlush(existingJob);
            
        } else {
            log.debug("Sync detected no significant changes for {}. Skipping redundant update.", existingJob.getCompany());
        }
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
        return map.entrySet().parallelStream()
            .map(e -> new ChartData(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }
}