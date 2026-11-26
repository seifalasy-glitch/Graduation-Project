package com.edusync.edusync.backend.registration.repository;

import com.edusync.edusync.backend.enrollment.entity.Enrollment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationSeatRepository extends JpaRepository<Enrollment, Long> {

    @Query("""
            select e.offeringId, count(e) from Enrollment e
            where e.offeringId in :ids and e.status = 'ACTIVE'
            group by e.offeringId
            """)
    List<Object[]> countActiveByOffering(@Param("ids") Collection<Long> ids);

    @Query("""
            select o.course.courseId from Enrollment e, CourseOffering o
            where e.offeringId = o.offeringId
              and e.studentUserId = :studentId
              and o.term.termId = :termId
              and e.status = 'ACTIVE'
            """)
    List<Long> findActiveCourseIdsInTerm(@Param("studentId") Long studentId, @Param("termId") Long termId);

    @Query("""
            select coalesce(sum(o.course.creditHours), 0) from Enrollment e, CourseOffering o
            where e.offeringId = o.offeringId
              and e.studentUserId = :studentId
              and o.term.termId = :termId
              and e.status = 'ACTIVE'
            """)
    long sumActiveCreditsInTerm(@Param("studentId") Long studentId, @Param("termId") Long termId);

    @Query("select e.offeringId from Enrollment e where e.studentUserId = :studentId and e.offeringId in :ids")
    List<Long> findEnrolledOfferingIds(@Param("studentId") Long studentId, @Param("ids") Collection<Long> ids);
}
