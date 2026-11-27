package com.edusync.edusync.backend.registration.service;

import com.edusync.edusync.backend.common.AfterCommitMailer;
import com.edusync.edusync.backend.users.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class RegistrationNotifier {

    private final JavaMailSender mailSender;
    private final AfterCommitMailer afterCommitMailer;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public RegistrationNotifier(JavaMailSender mailSender, AfterCommitMailer afterCommitMailer) {
        this.mailSender = mailSender;
        this.afterCommitMailer = afterCommitMailer;
    }

    public void submitted(User advisor, User student) {
        send(advisor.getEmail(),
                "EduSync - Registration request from " + student.getFullName(),
                "Hello " + advisor.getFullName() + ",\n\n"
                        + student.getFullName() + " submitted a course registration request waiting for your review.\n\n"
                        + "Log in here: " + frontendUrl + "/login"
                        + "\n\nEduSync Team");
    }

    public void decided(User student, boolean approved, String note) {
        String result = approved
                ? "was approved and you are now enrolled in the chosen courses."
                : "was returned. Please edit it and submit again.";

        send(student.getEmail(),
                "EduSync - Your registration request " + (approved ? "was approved" : "was returned"),
                "Hello " + student.getFullName() + ",\n\n"
                        + "Your course registration request " + result
                        + (note == null || note.isBlank() ? "" : "\n\nNote: " + note)
                        + "\n\nLog in here: " + frontendUrl + "/login"
                        + "\n\nEduSync Team");
    }

    // Sent in the background after the request is saved (see AfterCommitMailer)
    private void send(String to, String subject, String text) {
        afterCommitMailer.sendAfterCommit("registration email to " + to, () -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        });
    }
}
