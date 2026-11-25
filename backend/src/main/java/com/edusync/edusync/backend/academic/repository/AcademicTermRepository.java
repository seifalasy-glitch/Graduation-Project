package com.edusync.edusync.backend.academic.repository;

import com.edusync.edusync.backend.academic.entity.AcademicTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {

    /** API contract: terms, newest first. */
    @Query("select t from AcademicTerm t order by t.startDate desc nulls last, t.termId desc")
    List<AcademicTerm> findAllNewestFirst();

    boolean existsByAcademicYearIgnoreCaseAndTermTypeIgnoreCase(
            String academicYear,
            String termType
    );

    boolean existsByAcademicYearIgnoreCaseAndTermTypeIgnoreCaseAndTermIdNot(
            String academicYear,
            String termType,
            Long termId
    );
}
