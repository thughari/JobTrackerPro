package com.thughari.jobtrackerpro.util;

import com.thughari.jobtrackerpro.dto.JobDTO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateParserTest {

    @Test
    void testCleanSubject() {
        assertEquals("You applied to Software Engineer at Stripe", 
            TemplateParser.cleanSubject("Fwd: You applied to Software Engineer at Stripe"));
        assertEquals("You applied to Software Engineer at Stripe", 
            TemplateParser.cleanSubject("Re: You applied to Software Engineer at Stripe"));
        assertEquals("You applied to Software Engineer at Stripe", 
            TemplateParser.cleanSubject("fwd: re: You applied to Software Engineer at Stripe"));
        assertEquals("You applied to Software Engineer at Stripe", 
            TemplateParser.cleanSubject("Fwd Fw: You applied to Software Engineer at Stripe"));
    }

    @Test
    void testLinkedInTemplates() {
        // Template A
        JobDTO jobA = TemplateParser.parse("jobs-listings@linkedin.com", 
            "Your application to Stripe for Software Engineer was sent", 
            "[LINK_START]View job[LINK_URL]https://www.linkedin.com/jobs/view/12345[LINK_END]");
        assertNotNull(jobA);
        assertEquals("Stripe", jobA.getCompany());
        assertEquals("Software Engineer", jobA.getRole());
        assertEquals("Applied", jobA.getStatus());
        assertEquals("https://www.linkedin.com/jobs/view/12345", jobA.getUrl());
        assertEquals(0, jobA.getUrlIndex());

        // Template B
        JobDTO jobB = TemplateParser.parse("jobs-listings@linkedin.com", 
            "You applied to Backend Developer at Google LLC", 
            "Hello, you applied to this job. Check status: [LINK_START]Status[LINK_URL]https://www.linkedin.com/jobs/view/67890[LINK_END]");
        assertNotNull(jobB);
        assertEquals("Google", jobB.getCompany()); // Suffix LLC removed
        assertEquals("Backend Developer", jobB.getRole());
        assertEquals("https://www.linkedin.com/jobs/view/67890", jobB.getUrl());

        // Template C
        JobDTO jobC = TemplateParser.parse("confirmations@linkedin.com", 
            "Confirming your application for Data Scientist at Netflix Inc.", 
            "Thanks for applying.");
        assertNotNull(jobC);
        assertEquals("Netflix", jobC.getCompany()); // Suffix Inc removed
        assertEquals("Data Scientist", jobC.getRole());

        // Template D (new format: your application was sent to [Company])
        JobDTO jobD = TemplateParser.parse("jobs-noreply@linkedin.com",
            "Hari, your application was sent to Selby Jennings",
            "Your application was sent to Selby Jennings software engineer Selby Jennings &middot; Amsterdam View job: [LINK_START]View job[LINK_URL]https://www.linkedin.com/comm/jobs/view/4415210885/[LINK_END]");
        assertNotNull(jobD);
        assertEquals("Selby Jennings", jobD.getCompany());
        assertEquals("software engineer", jobD.getRole());
        assertEquals("Amsterdam", jobD.getLocation());
        assertEquals("https://www.linkedin.com/comm/jobs/view/4415210885/", jobD.getUrl());

        // Template E (Hybrid location and parentheses role)
        JobDTO jobE = TemplateParser.parse("jobs-noreply@linkedin.com",
            "Hari, your application was sent to Infinity Quest",
            "Your application was sent to Infinity Quest Java Backend Developer (AWS) Infinity Quest &middot; Amsterdam (Hybrid) Applied on June 2, 2026");
        assertNotNull(jobE);
        assertEquals("Infinity Quest", jobE.getCompany());
        assertEquals("Java Backend Developer (AWS)", jobE.getRole());
        assertEquals("Amsterdam (Hybrid)", jobE.getLocation());

        // Template F (Callisto Web Solutions B.V.)
        JobDTO jobF = TemplateParser.parse("jobs-noreply@linkedin.com",
            "Hari, your application was sent to Callisto Web Solutions B.V.",
            "Your application was sent to Callisto Web Solutions B.V. Java Software Engineer Callisto Web Solutions B.V. Zaandam View job: [LINK_START]View job[LINK_URL]https://www.linkedin.com/comm/jobs/view/4411341070/[LINK_END] ... Callisto Web Solutions B.V. &middot; Zaandam (Hybrid) Applied on June 2, 2026");
        assertNotNull(jobF);
        assertEquals("Callisto Web Solutions B.V", jobF.getCompany());
        assertEquals("Java Software Engineer", jobF.getRole());
        assertEquals("Zaandam (Hybrid)", jobF.getLocation());
    }

    @Test
    void testIndeedTemplates() {
        // Template A
        JobDTO jobA = TemplateParser.parse("alert@indeed.com", 
            "Indeed Application Received: Java Engineer at Meta Platforms", 
            "View details: [LINK_START]Job Details[LINK_URL]https://www.indeed.com/viewjob?jk=abc[LINK_END]");
        assertNotNull(jobA);
        assertEquals("Meta Platforms", jobA.getCompany());
        assertEquals("Java Engineer", jobA.getRole());
        assertEquals("https://www.indeed.com/viewjob?jk=abc", jobA.getUrl());

        // Template B
        JobDTO jobB = TemplateParser.parse("no-reply@indeedemail.com", 
            "Application received: Frontend Engineer at Apple", 
            "Apple has received your application.");
        assertNotNull(jobB);
        assertEquals("Apple", jobB.getCompany());
        assertEquals("Frontend Engineer", jobB.getRole());

        // Template C (new format: Indeed Application: [Role])
        JobDTO jobC = TemplateParser.parse("indeedapply@indeed.com",
            "Indeed Application: Java Developer",
            "Application submitted [LINK_START]Java Developer[LINK_URL]https://apply.indeed.com/indeedapply/confirmemail/viewjob?iaUid=123[LINK_END] MedRec Technologies - Navi Mumbai, Maharashtra");
        assertNotNull(jobC);
        assertEquals("MedRec Technologies", jobC.getCompany());
        assertEquals("Java Developer", jobC.getRole());
        assertEquals("Navi Mumbai, Maharashtra", jobC.getLocation());
        assertEquals("https://apply.indeed.com/indeedapply/confirmemail/viewjob?iaUid=123", jobC.getUrl());

        // Template D (Indeed Application: hyphenated role, location with reviews count, next URL parameter, safety disclaimer in footer)
        String nagarroBody = "Your application has been submitted. Good luck!\n" +
                             "Application submitted [LINK_START]Senior Engineer, Java[LINK_URL]https://apply.indeed.com/indeedapply/confirmemail/viewjob?iaUid=123&apiToken=abc&next=https%3A%2F%2Fin.indeed.com%2Fviewjob%3Fjk%3D12345[LINK_END] Nagarro - Remote 95 reviews\n" +
                             "Never share financial info or take job offers without an interview.";
        JobDTO jobD = TemplateParser.parse("indeedapply@indeed.com",
            "Indeed Application: Senior Engineer, Java",
            nagarroBody);
        assertNotNull(jobD);
        assertEquals("Nagarro", jobD.getCompany());
        assertEquals("Senior Engineer, Java", jobD.getRole());
        assertEquals("Remote", jobD.getLocation());
        assertEquals("Applied", jobD.getStatus());
        assertEquals("https://in.indeed.com/viewjob?jk=12345", jobD.getUrl());

        // Template E (Indeed Application: Java Backend Developer - MS)
        String capcoBody = "Your application has been submitted. Good luck!\n" +
                           "Application submitted [LINK_START]Java Backend Developer - MS[LINK_URL]https://apply.indeed.com/indeedapply/confirmemail/viewjob?iaUid=456&apiToken=xyz&next=https%3A%2F%2Fin.indeed.com%2Fviewjob%3Fjk%3D67890[LINK_END] Capco - Mumbai, MH 274 reviews\n" +
                           "Never share financial info or take job offers without an interview.\n" +
                           "[LINK_START]Learn how to avoid scams[LINK_URL]https://apply.indeed.com/indeedapply/confirmemail/fraudlink?iaUid=456&apiToken=xyz[LINK_END]\n" +
                           "Please [LINK_START]contact Indeed[LINK_URL]https://apply.indeed.com/indeedapply/confirmemail/contactus?iaUid=456&apiToken=xyz[LINK_END]";
        JobDTO jobE = TemplateParser.parse("indeedapply@indeed.com",
            "Indeed Application: Java Backend Developer - MS",
            capcoBody);
        assertNotNull(jobE);
        assertEquals("Capco", jobE.getCompany());
        assertEquals("Java Backend Developer - MS", jobE.getRole());
        assertEquals("Mumbai, MH", jobE.getLocation());
        assertEquals("Applied", jobE.getStatus());
        assertEquals("https://in.indeed.com/viewjob?jk=67890", jobE.getUrl());
    }

    @Test
    void testForwardedEmails() {
        String body = "---------- Forwarded message ---------\n" +
                      "From: LinkedIn <jobs-listings@linkedin.com>\n" +
                      "Date: Tue, Jun 2, 2026 at 7:00 AM\n" +
                      "Subject: You applied to Fullstack Engineer at Microsoft\n\n" +
                      "Here is the body of the email: [LINK_START]View job[LINK_URL]https://www.linkedin.com/jobs/view/999[LINK_END]";

        JobDTO job = TemplateParser.parse("user-forwarder@gmail.com", "Fwd: forwarded job email", body);
        assertNotNull(job);
        assertEquals("Microsoft", job.getCompany());
        assertEquals("Fullstack Engineer", job.getRole());
        assertEquals("https://www.linkedin.com/jobs/view/999", job.getUrl());
    }

    @Test
    void testStatusDetermination() {
        // Rejected
        JobDTO rejected = TemplateParser.parse("jobs-listings@linkedin.com", 
            "Your application to Uber for Software Engineer was sent", 
            "Thank you for your interest. Unfortunately, we decided not to move forward with your application.");
        assertNotNull(rejected);
        assertEquals("Rejected", rejected.getStatus());
        assertEquals(1, rejected.getStage());
        assertEquals("failed", rejected.getStageStatus());

        // Interview Scheduled
        JobDTO interview = TemplateParser.parse("jobs-listings@linkedin.com", 
            "Your application to Uber for Software Engineer was sent", 
            "Let's schedule your interview for next week.");
        assertNotNull(interview);
        assertEquals("Interview Scheduled", interview.getStatus());
        assertEquals(3, interview.getStage());

        // Shortlisted / Assessment
        JobDTO assessment = TemplateParser.parse("jobs-listings@linkedin.com", 
            "Your application to Uber for Software Engineer was sent", 
            "Please complete this coding test link on HackerRank.");
        assertNotNull(assessment);
        assertEquals("Shortlisted", assessment.getStatus());
        assertEquals(2, assessment.getStage());
    }

    @Test
    void testNonLinkedInOrIndeedFallback() {
        // Should ignore emails that are not from LinkedIn or Indeed
        JobDTO job = TemplateParser.parse("jobs@stripe.com", 
            "Your application to Stripe for Software Engineer was sent", 
            "Some content.");
        assertNull(job);
    }
}
