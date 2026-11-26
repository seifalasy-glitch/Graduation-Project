package com.edusync.edusync.backend.registration.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "registration_request")
@Getter
@Setter
public class RegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @Column(name = "advisor_id")
    private Long advisorId;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "total_credits")
    private Integer totalCredits;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "advisor_decided_at")
    private LocalDateTime advisorDecidedAt;

    @Column(name = "admin_decided_at")
    private LocalDateTime adminDecidedAt;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}
