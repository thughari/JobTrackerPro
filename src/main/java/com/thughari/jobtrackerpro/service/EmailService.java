package com.thughari.jobtrackerpro.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;

@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.ui.url}")
    private String uiUrl;
    
    @Value("${email.sender_email}") 
    private String fromEmail; 
    
    @Value("${email.sender_name}") 
    private String fromName; 

    @Async
    public void sendResetEmail(String to, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);             
            helper.setTo(to);
            helper.setSubject("Reset Password - JobTrackPro");
            
            String resetLink = uiUrl + "/reset-password?token=" + token;
            
            String htmlContent = """
                <div style="background-color: #f3f4f6; padding: 20px; font-family: sans-serif;">
                    <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 30px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.05);">
                        <h2 style="color: #111827; margin-top: 0;">Reset Your Password</h2>
                        <p style="color: #4b5563; line-height: 1.6;">Hello,</p>
                        <p style="color: #4b5563; line-height: 1.6;">You requested to reset your password for JobTrackPro. Please click the button below to proceed:</p>
                        
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="background-color: #6366f1; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;">Reset Password</a>
                        </div>
                        
                        <p style="color: #6b7280; font-size: 12px; margin-top: 30px; border-top: 1px solid #e5e7eb; padding-top: 20px;">
                            If you did not request this, please ignore this email. The link will expire in 15 minutes.
                        </p>
                    </div>
                </div>
                """.formatted(resetLink);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset email sent to: {}", to);
            
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send email to {}", to, e);
        }
    }
}