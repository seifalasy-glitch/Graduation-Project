package com.edusync.edusync.backend.registration.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "program_plan_course")
@Getter
@Setter
public class ProgramPlanCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_course_id")
    private Long planCourseId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "term_type", nullable = false)
    private String termType;
}
