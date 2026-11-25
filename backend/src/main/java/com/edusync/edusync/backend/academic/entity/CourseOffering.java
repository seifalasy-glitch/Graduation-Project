package com.edusync.edusync.backend.academic.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "course_offering")
public class CourseOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offering_id")
    private Long offeringId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private AcademicCourse course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private AcademicTerm term;

    @Column(name = "coursework_max")
    private BigDecimal courseworkMax;

    @Column(name = "section")
    private String section;

    @Column(name = "capacity")
    private Integer capacity;

    public CourseOffering() {
    }

    public Long getOfferingId() {
        return offeringId;
    }

    public void setOfferingId(Long offeringId) {
        this.offeringId = offeringId;
    }

    public AcademicCourse getCourse() {
        return course;
    }

    public void setCourse(AcademicCourse course) {
        this.course = course;
    }

    public AcademicTerm getTerm() {
        return term;
    }

    public void setTerm(AcademicTerm term) {
        this.term = term;
    }

    public BigDecimal getCourseworkMax() {
        return courseworkMax;
    }

    public void setCourseworkMax(BigDecimal courseworkMax) {
        this.courseworkMax = courseworkMax;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
}
