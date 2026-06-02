package com.thughari.jobtrackerpro.util;

import com.thughari.jobtrackerpro.dto.JobDTO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateParser {

    private static class PatternConfig {
        final Pattern pattern;
        final int companyGroup;
        final int roleGroup;

        PatternConfig(Pattern pattern, int companyGroup, int roleGroup) {
            this.pattern = pattern;
            this.companyGroup = companyGroup;
            this.roleGroup = roleGroup;
        }
    }

    private static final List<PatternConfig> PATTERN_CONFIGS = List.of(
        // LinkedIn templates
        new PatternConfig(Pattern.compile("(?i)^Your application to (.+?) for (.+?) was sent$"), 1, 2),
        new PatternConfig(Pattern.compile("(?i)^You applied to (.+?) at (.+)$"), 2, 1),
        new PatternConfig(Pattern.compile("(?i)^Confirming your application for (.+?) at (.+)$"), 2, 1),
        new PatternConfig(Pattern.compile("(?i)^Your application to (.+?) - (.+)$"), 1, 2),
        new PatternConfig(Pattern.compile("(?i)^Your application to (.+?): (.+)$"), 1, 2),
        new PatternConfig(Pattern.compile("(?i)^(.+?) has viewed your application for (.+)$"), 1, 2),
        new PatternConfig(Pattern.compile("(?i)^(.+?) viewed your application$"), 1, -1),
        new PatternConfig(Pattern.compile("(?i)^.*?,?\\s*your application was sent to (.+)$"), 1, -1),

        // Indeed templates
        new PatternConfig(Pattern.compile("(?i)^Indeed Application Received:\\s*(.+?)\\s*at\\s*(.+)$"), 2, 1),
        new PatternConfig(Pattern.compile("(?i)^Application received:\\s*(.+?)\\s*at\\s*(.+)$"), 2, 1),
        new PatternConfig(Pattern.compile("(?i)^Indeed Application:\\s*(.+?)\\s*-\\s*(.+)$"), 2, 1),
        new PatternConfig(Pattern.compile("(?i)^Indeed Application:\\s*(.+)$"), -1, 1),
        new PatternConfig(Pattern.compile("(?i)^Your application to\\s*(.+?)\\s*-\\s*(.+)$"), 1, 2),
        new PatternConfig(Pattern.compile("(?i)^Your application to\\s*(.+?)\\s*:\\s*(.+)$"), 1, 2)
    );

    private static final Pattern LINK_PATTERN = Pattern.compile("\\[LINK_START\\](.*?)\\[LINK_URL\\](.*?)\\[LINK_END\\]");

    public static class EmailDetails {
        public final String from;
        public final String subject;
        public final String body;

        public EmailDetails(String from, String subject, String body) {
            this.from = from != null ? from.trim() : "";
            this.subject = subject != null ? subject.trim() : "";
            this.body = body != null ? body.trim() : "";
        }
    }

    /**
     * Parses forwarded email headers from the body if the email was forwarded.
     */
    public static EmailDetails parseForwardedHeaders(String from, String subject, String body) {
        if (body == null || body.isBlank()) {
            return new EmailDetails(from, subject, body);
        }

        String lowerBody = body.toLowerCase();
        if (lowerBody.contains("forwarded message") || lowerBody.contains("original message") || 
            (lowerBody.contains("subject:") && lowerBody.contains("from:"))) {
            
            String parsedFrom = null;
            String parsedSubject = null;
            String[] lines = body.split("\\r?\\n");
            int count = 0;
            
            for (String line : lines) {
                count++;
                if (count > 30) break; // Limit scanning to top 30 lines
                
                String trimmed = line.trim();
                if (trimmed.toLowerCase().startsWith("from:")) {
                    parsedFrom = trimmed.substring(5).trim();
                } else if (trimmed.toLowerCase().startsWith("subject:")) {
                    parsedSubject = trimmed.substring(8).trim();
                }
                
                if (parsedFrom != null && parsedSubject != null) {
                    break;
                }
            }
            
            if (parsedFrom != null && parsedSubject != null) {
                return new EmailDetails(parsedFrom, parsedSubject, body);
            }
        }
        return new EmailDetails(from, subject, body);
    }

    /**
     * Cleans standard email subject prefixes (Re:, Fwd:, etc.).
     */
    public static String cleanSubject(String subject) {
        if (subject == null) return "";
        String cleaned = subject.trim();
        while (true) {
            String lower = cleaned.toLowerCase();
            if (lower.startsWith("fwd:") || lower.startsWith("re:") || lower.startsWith("fw:")) {
                cleaned = cleaned.substring(cleaned.indexOf(":") + 1).trim();
            } else if (lower.startsWith("fwd ") || lower.startsWith("re ") || lower.startsWith("fw ")) {
                cleaned = cleaned.substring(4).trim();
            } else {
                break;
            }
        }
        return cleaned;
    }

    /**
     * Checks if the sender domain or address belongs to LinkedIn or Indeed.
     */
    public static boolean isLinkedInOrIndeed(EmailDetails details) {
        String from = details.from.toLowerCase();
        return from.contains("linkedin.com") || from.contains("indeed.com") || from.contains("indeedemail.com");
    }

    /**
     * Attempts to manually parse job details from a LinkedIn/Indeed email.
     * Returns null if it doesn't match standard templates.
     */
    public static JobDTO parse(String originalFrom, String originalSubject, String body) {
        EmailDetails details = parseForwardedHeaders(originalFrom, originalSubject, body);
        
        if (!isLinkedInOrIndeed(details)) {
            return null;
        }

        String cleanedSubject = cleanSubject(details.subject);
        PatternConfig matchedConfig = null;
        Matcher matcher = null;

        for (PatternConfig config : PATTERN_CONFIGS) {
            Matcher m = config.pattern.matcher(cleanedSubject);
            if (m.matches()) {
                matchedConfig = config;
                matcher = m;
                break;
            }
        }

        if (matchedConfig == null) {
            return null;
        }

        String company = null;
        String role = null;
        String extractedLocation = null;

        // If Indeed, try parsing from body first
        if (details.from.toLowerCase().contains("indeed")) {
            Pattern indeedBodyPattern = Pattern.compile("(?i)Application submitted \\[LINK_START\\](.*?)\\[LINK_URL\\].*?\\[LINK_END\\]\\s*([^\\n\\-]+?)\\s*-\\s*([^\\n\\r\\.]+)(?:\\s+Applied on|\\s+The following|\\s+Good luck|\\s+\\d+\\s+reviews|$)");
            Matcher bodyMatcher = indeedBodyPattern.matcher(details.body);
            if (bodyMatcher.find()) {
                role = bodyMatcher.group(1).trim();
                company = bodyMatcher.group(2).trim();
                extractedLocation = bodyMatcher.group(3).trim();
            }
        }

        if (company == null) {
            if (matchedConfig.companyGroup == -1) {
                company = "Unknown Company";
                Pattern indeedCompanyPattern = Pattern.compile("(?i)\\[LINK_END\\]\\s*([^\\n\\-]+?)\\s*-\\s*");
                Matcher bodyMatcher = indeedCompanyPattern.matcher(details.body);
                if (bodyMatcher.find()) {
                    company = bodyMatcher.group(1).trim();
                }
            } else {
                company = matcher.group(matchedConfig.companyGroup);
            }
            company = cleanCompany(company);
        } else {
            company = cleanCompany(company);
        }

        if (role == null) {
            if (matchedConfig.roleGroup == -1) {
                if (!company.equals("Unknown Company")) {
                    String escapedCompany = Pattern.quote(company);
                    Pattern linkedinRolePattern = Pattern.compile("(?i)Your application was sent to\\s+" + escapedCompany + "\\s*[,.;:\\-_]?\\s+(.+?)\\s*[,.;:\\-_]?\\s+" + escapedCompany);
                    Matcher bodyMatcher = linkedinRolePattern.matcher(details.body);
                    if (bodyMatcher.find()) {
                        role = bodyMatcher.group(1).trim();
                    }
                }
                if (role == null || role.isEmpty()) {
                    Pattern linkedinRolePatternGen = Pattern.compile("(?i)Your application was sent to\\s+(.+?)\\s*[,.;:\\-_]?\\s+(.+?)\\s*[,.;:\\-_]?\\s+\\1");
                    Matcher bodyMatcher = linkedinRolePatternGen.matcher(details.body);
                    if (bodyMatcher.find()) {
                        role = bodyMatcher.group(2).trim();
                    }
                }
                if (role == null || role.isEmpty()) {
                    role = "Software Engineer";
                }
            } else {
                role = matcher.group(matchedConfig.roleGroup);
            }
            role = cleanValue(role);
        } else {
            role = cleanValue(role);
        }
        if (role.isEmpty()) role = "Software Engineer";

        JobDTO job = new JobDTO();
        job.setCompany(company);
        job.setRole(role);
        
        // Extract Location dynamically
        String location = "Remote";
        if (extractedLocation != null) {
            location = extractedLocation;
        } else {
            if (!company.equals("Unknown Company")) {
                String escapedCompany = Pattern.quote(company);
                Pattern locPattern = Pattern.compile("(?i)" + escapedCompany + "\\s*[,.;:\\-_]?\\s*(?:·|&middot;|•|\\-|\\u00B7)\\s*([^\\n\\r]+?)(?:\\s+Applied on|\\s+View job|\\s+Easy Apply|\\s+Apply with|$)");
                Matcher lm = locPattern.matcher(details.body);
                if (lm.find()) {
                    location = lm.group(1).trim();
                }
            }
            
            if (location.equals("Remote") && details.from.toLowerCase().contains("indeed")) {
                Pattern indeedLocPattern = Pattern.compile("(?i)\\[LINK_END\\]\\s*[^\\n\\-]+?\\s*-\\s*([^\\n\\r]+?)(?:\\s+The following|$)");
                Matcher lm = indeedLocPattern.matcher(details.body);
                if (lm.find()) {
                    location = lm.group(1).trim();
                }
            }
        }
        
        if (location != null) {
            location = location.replaceAll("(?i)\\s*\\d+(?:,\\d+)?\\s+reviews.*", "");
        }
        location = cleanValue(location);
        if (location.isEmpty()) {
            location = "Remote";
        }
        job.setLocation(location);
        
        String status = determineStatus(details.subject, details.body);
        job.setStatus(status);
        job.setStage(mapStatusToStage(status));
        
        if ("Rejected".equalsIgnoreCase(status)) {
            job.setStageStatus("failed");
        } else if ("Offer Received".equalsIgnoreCase(status)) {
            job.setStageStatus("passed");
        } else {
            job.setStageStatus("active");
        }

        job.setSalaryMin(0.0);
        job.setSalaryMax(0.0);

        String platformName = details.from.toLowerCase().contains("linkedin") ? "LinkedIn" : "Indeed";
        job.setNotes("Extracted automatically from " + platformName + " email template.");

        LocalDateTime now = LocalDateTime.now();
        job.setAppliedDate(now);
        job.setUpdatedAt(now);

        // Find URL index map
        List<String> cleanUrls = UrlParser.extractAndCleanUrls(details.body);
        String targetUrl = extractJobUrl(details.body);
        if (!targetUrl.isEmpty()) {
            targetUrl = UrlParser.cleanUrl(targetUrl);
            job.setUrl(targetUrl);
            if (cleanUrls.contains(targetUrl)) {
                job.setUrlIndex(cleanUrls.indexOf(targetUrl));
            }
        } else if (!cleanUrls.isEmpty()) {
            for (int i = 0; i < cleanUrls.size(); i++) {
                String u = cleanUrls.get(i).toLowerCase();
                if (u.contains("job") || u.contains("career") || u.contains("apply")) {
                    job.setUrlIndex(i);
                    job.setUrl(cleanUrls.get(i));
                    break;
                }
            }
        }

        if (job.getUrl() == null) {
            job.setUrl("");
        }

        return job;
    }

    private static String cleanValue(String val) {
        if (val == null) return "";
        String clean = val.replaceAll("<[^>]*>", " ");
        clean = clean.replaceAll("^[\\s,.;:\\-_]+", "").replaceAll("[\\s,.;:\\-_]+$", "");
        return clean.trim();
    }

    private static String cleanCompany(String company) {
        String cleaned = cleanValue(company);
        if (cleaned.isEmpty()) return "Unknown Company";

        String lower = cleaned.toLowerCase();
        if (lower.endsWith(" inc") || lower.endsWith(" inc.")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4).trim();
        } else if (lower.endsWith(" llc") || lower.endsWith(" llc.")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4).trim();
        } else if (lower.endsWith(" corp") || lower.endsWith(" corp.") || lower.endsWith(" corporation")) {
            int idx = lower.lastIndexOf(" corp");
            cleaned = cleaned.substring(0, idx).trim();
        } else if (lower.endsWith(" ltd") || lower.endsWith(" ltd.")) {
            cleaned = cleaned.substring(0, cleaned.length() - 4).trim();
        }

        cleaned = cleaned.replaceAll("^[\\s\\p{Punct}]+", "").replaceAll("[\\s\\p{Punct}]+$", "").trim();
        return cleaned.isEmpty() ? "Unknown Company" : cleaned;
    }

    private static String determineStatus(String subject, String body) {
        if (body == null) body = "";
        String cleanedBody = body
            .replaceAll("(?i)never share financial info (?:or take job offers )?without an? interview", "")
            .replaceAll("(?i)without (?:an? )?interview", "");
        
        String combined = (subject + " " + cleanedBody).toLowerCase();
        if (combined.contains("interview") || combined.contains("schedule your") || combined.contains("speaking with you")) {
            return "Interview Scheduled";
        }
        if (combined.contains("assessment") || combined.contains("coding test") || combined.contains("test link") || 
            combined.contains("hackerrank") || combined.contains("codility") || combined.contains("challenge")) {
            return "Shortlisted";
        }
        if (combined.contains("unfortunately") || combined.contains("not moving forward") || 
            combined.contains("decided to move forward with other") || combined.contains("thank you for your interest but")) {
            return "Rejected";
        }
        return "Applied";
    }

    private static Integer mapStatusToStage(String status) {
        if (status == null) return 1;
        if (status.contains("Offer")) return 4;
        if (status.contains("Interview")) return 3;
        if (status.contains("Shortlisted")) return 2;
        return 1;
    }

    private static String extractJobUrl(String body) {
        if (body == null || body.isBlank()) return "";
        Matcher m = LINK_PATTERN.matcher(body);
        String fallbackUrl = "";
        while (m.find()) {
            String text = m.group(1).toLowerCase();
            String url = m.group(2).trim();
            String urlLower = url.toLowerCase();
            
            // Skip known non-job URLs (support, contact, fraud, unsubscribe, etc.)
            if (urlLower.contains("contact") || urlLower.contains("support") || 
                urlLower.contains("fraud") || urlLower.contains("scam") || 
                urlLower.contains("unsubscribe") || urlLower.contains("privacy") || 
                urlLower.contains("settings") || urlLower.contains("help")) {
                continue;
            }
            
            if (url.contains("linkedin.com/jobs/view") || 
                url.contains("indeed.com/viewjob") || 
                url.contains("indeed.com/rc/clk") ||
                url.contains("indeedapply/confirmemail/viewjob")) {
                return url;
            }
            if (text.contains("view job") || text.contains("view application") || text.contains("check status") || text.contains("job details")) {
                return url;
            }
            if (url.contains("/jobs/") || url.contains("/careers/") || url.contains("apply")) {
                if (fallbackUrl.isEmpty()) {
                    fallbackUrl = url;
                }
            }
        }
        if (!fallbackUrl.isEmpty()) return fallbackUrl;

        Pattern rawUrlPattern = Pattern.compile("https?://[a-zA-Z0-9./?=&%_\\-+]+");
        Matcher rm = rawUrlPattern.matcher(body);
        while (rm.find()) {
            String url = rm.group().trim();
            if (url.contains("linkedin.com/jobs") || url.contains("indeed.com")) {
                return url;
            }
        }
        return "";
    }
}
