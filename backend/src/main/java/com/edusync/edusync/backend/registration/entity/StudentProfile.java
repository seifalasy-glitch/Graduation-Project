package com.edusync.edusync.backend.registration.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "student_profile")
@Getter
@Setter
public class StudentProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "program_id")
    private Long programId;

    @Column(name = "advisor_id")
    private Long advisorId;

    @Column(name = "level")
    private Integer level;

    @Column(name = "max_credit_hours")
    private Integer maxCreditHours;
}
