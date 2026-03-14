package com.thughari.jobtrackerpro.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thughari.jobtrackerpro.dto.EmailBatchItem;
import com.thughari.jobtrackerpro.dto.JobDTO;
import com.thughari.jobtrackerpro.interfaces.GeminiService;
import com.thughari.jobtrackerpro.util.UrlParser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(name = "app.gemini.enabled", havingValue = "true")
public class GeminiExtractionService implements GeminiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    public GeminiExtractionService() {
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public JobDTO extractJobFromEmail(String from, String subject, String body) {
        String prompt = buildPrompt(from, subject, body);

        try {
        	Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                        Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", prompt))
                        )
                    )
                );

            String response = restClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseGeminiResponse(response);

        } catch (Exception e) {
            log.error("AI Extraction failed or timed out", e);
            return null; 
        }
    }
    
    @Override
    public List<JobDTO> extractJobsFromBatch(List<EmailBatchItem> items) {
        if (items == null || items.isEmpty()) return List.of();

        String prompt = buildBatchPrompt(items);

        try {
            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))
                )
            );

            String response = restClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseBulkGeminiResponse(response);

        } catch (Exception e) {
            log.error("Bulk AI Extraction failed", e);
            return List.of();
        }
    }
    
    private String buildBatchPrompt(List<EmailBatchItem> items) {
        StringBuilder emailListBuilder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            EmailBatchItem item = items.get(i);
            String rawBody = item.body() == null ? "" : item.body();

            String trimmed = UrlParser.trimNoise(rawBody);
            String safeBody = trimmed.length() > 3000 
                    ? trimmed.substring(0, 3000 )
                    : trimmed;

            List<String> urls = UrlParser.extractAndCleanUrls(rawBody);
            
            emailListBuilder.append("""
            		--- EMAIL INDEX: %d ---
            		FROM: %s
            		REPLY-TO: %s
            		SUBJECT: %s
            		BODY: %s

            		AVAILABLE_URLS:
            		%s
            		-----------------------
            		""".formatted(
            		    i,
            		    item.from(),
            		    item.replyTo(),
            		    item.subject(),
            		    safeBody,
            		    buildUrlIndexList(urls)
            		));
        }

        return """
        ##Act as a strict Global Data Extraction System for a Job Application Tracker.
        
		You analyze emails and extract structured job application data.
		
		--------------------------------------------------
		
		### TASK
		
		Analyze the list of emails provided below.
		
		For EACH email you must determine:
		
		1. Is this email related to a REAL hiring interaction?
		2. If YES → extract structured job data.
		3. If NO → completely ignore the email.
		
		A valid job-related email must indicate interaction with a hiring process.
		
		Examples of valid hiring interactions:
		- Application confirmation
		- Recruiter outreach
		- Interview invitation
		- Coding assessment invitation
		- Hiring process update
		- Offer
		- Rejection
		
		Emails that are only informational, promotional, or educational MUST be ignored.
		
		--------------------------------------------------
		
		### HIRING SIGNAL RULE (VERY IMPORTANT)
		
		A valid job email MUST contain at least ONE hiring process signal such as:
		
		- "applied"
		- "application received"
		- "thank you for applying"
		- "interview"
		- "assessment"
		- "coding challenge"
		- "recruiter"
		- "hiring team"
		- "offer"
		- "rejected"
		- "next steps"
		
		If NONE of these signals exist, the email MUST be ignored.
		
		Company name + role alone is NOT sufficient.
		
		--------------------------------------------------
		
		### HARD EXCLUSION RULES
		
		DO NOT treat the email as job-related if it is any of the following:
		
		- Job alerts
		- Job recommendation digests
		- Emails listing multiple job postings
		- Developer events or workshops
		- Coding contests
		- Newsletters
		- Marketing campaigns
		- Community announcements
		- Learning resources or interview prep
		- General job search tips
		
		Examples that MUST be ignored:
		
		- "Your job alert"
		- "Recommended jobs"
		- "Jobs you may like"
		- "New jobs for you"
		- "Join our developer workshop"
		- "LeetCode Weekly Contest"
		- "Google Cloud Labs event"
		- "Upcoming coding challenge"
		
		These emails MUST produce NO OUTPUT.
		
		--------------------------------------------------
		
		### MULTI-COMPANY RULE
		
		If an email lists multiple different companies or multiple unrelated job listings,
		it is a job alert or recommendation email and MUST be ignored.
		
		Valid job emails normally reference ONE specific job opportunity.
		
		--------------------------------------------------
		
		### NON-JOB EMAIL EXCLUSIONS
		
		Strictly ignore emails related to:
		
		- banking
		- OTP
		- payment confirmation
		- receipts
		- subscriptions
		- invoices
		- shipping notifications
		- system alerts
		- account security notifications
		
		--------------------------------------------------
		
		### INDEXING RULE (STRICT)
		
		Each email is labeled:
		
		--- EMAIL INDEX: X ---
		
		You MUST:
		
		- Return "inputIndex" exactly equal to the EMAIL INDEX value.
		- NEVER renumber indexes.
		- NEVER invent indexes.
		- Only include job-related emails.
		- Preserve original index numbers.
		
		Example:
		
		If emails are 0,1,2,3 and only 0 and 3 are job-related:
		
		[
		  { "inputIndex": 0, ... },
		  { "inputIndex": 3, ... }
		]
		
		Do NOT return sequential indexes.
		
		--------------------------------------------------
		
		### LIST OF EMAILS TO ANALYZE
		
		%s
		
		--------------------------------------------------
		
		### EXTRACTION RULES
		
		Apply these only to valid job-related emails.
		
		1. COMPANY
		
		Identify the hiring company.
		
		Rules:
		
		- Prefer the company explicitly mentioned in hiring context.
		- If multiple companies appear, select the one responsible for the job.
		- Fallback: extract from sender domain.
		
		Examples:
		careers@stripe.com → Stripe  
		talent.wayfair.com → Wayfair
		
		Emails may be sent by recruiting platforms such as: Naukri, Talent500, LinkedIn, Hired, Wellfound, Indeed, etc.
		
		These platforms are NOT the hiring company.

		If a recruiting platform is mentioned, identify the actual employer
		mentioned in the job description or company section.
		
		Example:
		Email from: Talent500
		Job description mentions: Albertsons Companies
		
		Correct company: Albertsons
		
		If the email contains a Reply-To header, and the domain appears to be a company domain, prefer that domain over recruiting platforms.
		
		Example:
		
		From: messages.naukri.com
		Reply-To: recruiter@yupptv.com
		
		Company = YuppTV
		
		Ignore generic domains:
		gmail, yahoo, outlook, etc.
		
		Default:
		"Unknown Company"
		
		--------------------------------------------------
		
		2. ROLE
		
		Extract the job title.
		
		Examples:
		Software Engineer  
		Java Developer  
		Backend Engineer  
		
		If no clear role exists:
		Default to **"Software Engineer"**
		
		--------------------------------------------------
		
		3. STATUS
		
		Return EXACTLY one of:
		
		- "Applied"
		- "Shortlisted"
		- "Interview Scheduled"
		- "Offer Received"
		- "Rejected"
		
		Rules:
		
		Recruiter outreach -> "Applied"  
		Referral messages -> "Applied"  
		Assessment invitations -> "Shortlisted"  
		Interview scheduling -> "Interview Scheduled"
		Application Rejected -> "Rejected"
		
		Never invent new status values.
		
		--------------------------------------------------
		
		4. LOCATION
		
		Extract city or country if present.
		
		Examples:
		London  
		Hyderabad  
		United States
		
		If no location exists:
		Return **"Remote"**
		
		--------------------------------------------------
		
		5. NOTES
		
		Write ONE concise sentence summarizing the email.
		
		Example:
		"Application received for Software Engineer role."
		
		--------------------------------------------------
		
		6. URL_SELECTION
		
		Each email contains a list of AVAILABLE_URLS.
		
		You MUST:
		
		- choose the index of the most relevant job-related link
		- never invent URLs
		- ignore footer links
		
		Ignore links related to:
		
		- unsubscribe
		- help
		- privacy
		- settings
		- account management
		
		If no job-related link exists:
		
		"urlIndex": -1
		
		--------------------------------------------------
		
		7. SALARY
		
		Extract salary if present.
		
		If not present:
		
		salaryMin = 0.0  
		salaryMax = 0.0
		
		--------------------------------------------------
		
		### MULTILINGUAL RULE
		
		Emails may be written in any language.
		
		Extract information normally but return ALL output values in English.
		
		--------------------------------------------------
		
		### OUTPUT FORMAT
		
		Return ONLY a raw JSON array.
		
		No markdown  
		No explanations  
		No text before or after JSON.
		
		Return [] if no job-related emails exist.
		
		--------------------------------------------------
		
		### Example Output
		
		[
		  {
		    "inputIndex": 0,
		    "company": "Stripe",
		    "role": "Software Engineer",
		    "location": "Remote",
		    "status": "Applied",
		    "urlIndex": 1,
		    "salaryMin": 0.0,
		    "salaryMax": 0.0,
		    "notes": "Application confirmation for Software Engineer role."
		  }
		]""".formatted(emailListBuilder.toString());
    }

    private List<JobDTO> parseBulkGeminiResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            
            if (candidates.isMissingNode() || candidates.isEmpty()) return List.of();

            String contentText = candidates.get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            contentText = contentText.replaceAll("```json", "").replaceAll("```", "").trim();

            if (contentText.equals("[]") || contentText.equalsIgnoreCase("null")) {
                return List.of();
            }

            List<JobDTO> jobs = objectMapper.readValue(contentText, new TypeReference<List<JobDTO>>() {});
            
            LocalDateTime now = LocalDateTime.now();
            jobs.forEach(job -> {
                job.setAppliedDate(now); 
                job.setUpdatedAt(now);
                job.setStage(mapStatusToStage(job.getStatus()));
                
                if ("Rejected".equalsIgnoreCase(job.getStatus())) {
                    job.setStageStatus("failed");
                } else if ("Offer Received".equalsIgnoreCase(job.getStatus())) {
                    job.setStageStatus("passed");
                } else {
                    job.setStageStatus("active");
                }
            });
            
            return jobs;

        } catch (Exception e) {
            log.error("Failed to parse Bulk AI response: {}", rawResponse);
            return List.of();
        }
    }
    
    private String buildUrlIndexList(List<String> urls) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < urls.size(); i++) {
            sb.append(i).append(": ").append(urls.get(i)).append("\n");
        }
        return sb.toString();
    }

    private String buildPrompt(String from, String subject, String body) {
        String safeBody = (body != null) ? (body.length() > 8000 ? body.substring(0, 8000 ) : body) : "";

        return """
            Act as a strict Global Data Extraction System for a Job Application Tracker.

            ### TASK
            Analyze the email content below.
            Determine whether the email is related to a specific job opportunity, hiring process, or recruiter communication.

            If the email mentions a company and a job role in a hiring context, it MUST be treated as job-related.
            Only return `null` if the email is clearly commercial spam, a receipt, or completely unrelated to jobs or careers.

            Valid categories include:
            1. Application Confirmations (ATS).
            2. Interview Invites.
            3. Offers/Rejections.
            4. **Recruiter Outreach / Walk-In Drive Invitations**.
            5. **User sent emails** (e.g. user replying to a recruiter about a role).
            
            **CRITICAL RULE:** 
            Only return `null` if the email is strictly commercial spam (selling products), receipts, or completely unrelated to careers.
            
            ### EMAIL CONTENT
            FROM: %s
            SUBJECT: %s
            BODY: %s

            ### EXTRACTION RULES
            1. **COMPANY**: Identify the hiring company. 
                - If multiple companies are mentioned, select the one most directly related to the role, interview, assessment, offer, or rejection.
                - If no company name is explicitly mentioned in the email content:
                    - Attempt a fallback extraction from the sender's email domain.
                    - Derive the company name from the domain (for example, careers@stripe.com -> Stripe).
                    - Ignore generic email providers such as gmail.com, yahoo.com, outlook.com, and similar.
                - If both content-based and domain-based extraction fail:
                    - Never return null
                    - Return "Unknown Company" as the default value.

            2. **ROLE**: Extract the specific job title. 
                - If it is a Walk-In drive listing multiple roles, pick the one most relevant to "Java" or "Software Engineer", or default to "Software Engineer".
                - Default to "Software Engineer" only if no role is clear.
            3. **STATUS**: Map to one of these exact statuses:
                - "Applied" (Use this for Walk-in invites, Recruiter outreach, or Sent emails)
                - "Shortlisted" (Use this for 'Next steps', Coding Tests, Exams, or HackerRank invites or similar)
                - "Interview Scheduled" (for any interview invites)
                - "Offer Received"
                - "Rejected"
            4. **NOTES**: A 1-sentence summary (e.g., "Walk-in drive invitation", "Replied to recruiter").
            5. **LOCATION**: Extract City/Country if found, otherwise default to "Remote" but never make it null.
                - If not found, default to "Remote".

            6. **URL**: Hunt for the primary call-to-action link. 
                - Look for URLs immediately following words like "Apply", "View Job", "Click here", or "Check status".
                - If multiple links exist, prioritize ones containing "careers", "jobs", "apply", or "lever.co", "greenhouse.io", "myworkday".
                - Return the full raw URL string.
                - If no URL is found, return the company's website mentioned in the email.
            7. **SALARY**: Extract salary numbers if present.
                - Otherwise return 0.0 for both salaryMin and salaryMax.

            ### MULTILINGUAL RULE
            If the email is in Dutch, French, or any other language, you MUST process it normally but provide the JSON output values in English so the user can understand their dashboard.

            ### OUTPUT FORMAT
            Return ONLY raw JSON (no markdown blocks, no explanations):
            {
              "company": "String",
              "role": "String",
              "location": "String",
              "status": "String",
              "url": "String",
              "salaryMin": 0.0,
              "salaryMax": 0.0,
              "notes": "String"
            }
            OR just: null
            """.formatted(from, subject, safeBody);
    }

    private JobDTO parseGeminiResponse(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            
            if (candidates.isMissingNode() || candidates.isEmpty()) {
                return null;
            }

            String contentText = candidates.get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            contentText = contentText.replaceAll("```json", "").replaceAll("```", "").trim();

            if (contentText.equalsIgnoreCase("null")) {
                log.info("AI determined this email is NOT a job application.");
                return null;
            }

            JobDTO job = objectMapper.readValue(contentText, JobDTO.class);
            
            LocalDateTime now = LocalDateTime.now();
            job.setAppliedDate(now); 
            job.setUpdatedAt(now);
            
            job.setStage(mapStatusToStage(job.getStatus()));
            
            if ("Rejected".equalsIgnoreCase(job.getStatus())) {
                job.setStageStatus("failed");
            } else if ("Offer Received".equalsIgnoreCase(job.getStatus())) {
                job.setStageStatus("passed");
            } else {
                job.setStageStatus("active");
            }
            
            return job;

        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", rawResponse);
            return null;
        }
    }

    private Integer mapStatusToStage(String status) {
        if (status == null) return 1;
        if (status.contains("Offer")) return 4;
        if (status.contains("Interview")) return 3;
        if (status.contains("Shortlisted") || status.contains("exam") || status.contains("test") || status.contains("hackerrank")) return 2;

        return 1;
    }
}