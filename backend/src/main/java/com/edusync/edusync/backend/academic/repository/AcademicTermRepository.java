package com.edusync.edusync.backend.academic.repository;

import com.edusync.edusync.backend.academic.entity.AcademicTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /**
     * Terms whose dates include the given day (both ends included), latest start first.
     * Used by CurrentTermService; normally 0 or 1 row.
     */
    @Query("""
            select t from AcademicTerm t
            where t.startDate <= :day and t.endDate >= :day
            order by t.startDate desc, t.termId desc
            """)
    List<AcademicTerm> findContaining(@Param("day") LocalDate day);
}
