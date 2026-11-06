package com.edusync.edusync.backend.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetEmail(String recipientEmail, String token) {

        String resetLink = frontendUrl
                + "/reset-password?token="
                + token;

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject("EduSync - Reset Your Password");

        message.setText(
                "Hello,\n\n"
                        + "We received a request to reset your EduSync password.\n\n"
                        + "Click the following link to reset your password:\n\n"
                        + resetLink
                        + "\n\nThis link will expire in 30 minutes."
                        + "\nIt can only be used once."
                        + "\n\nIf you did not request a password reset, "
                        + "you can safely ignore this email."
                        + "\n\nEduSync Team"
        );

        mailSender.send(message);
    }
}
