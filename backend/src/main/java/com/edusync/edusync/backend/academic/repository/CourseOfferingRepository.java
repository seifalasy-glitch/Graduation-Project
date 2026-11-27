package com.edusync.edusync.backend.academic.repository;

import com.edusync.edusync.backend.academic.entity.CourseOffering;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    /** One query with the course and the term loaded (no extra query per row). */
    @Query("""
            select o from CourseOffering o
            join fetch o.course
            join fetch o.term
            order by o.offeringId desc
            """)
    List<CourseOffering> findAllWithDetails();

    /**
     * Offering.enrolledCount: ACTIVE enrollments (the same count the seat / OFFERING_FULL
     * check uses). One query for all the given offerings; each row is [offeringId, count].
     */
    @Query(value = """
            SELECT offering_id, COUNT(*) FROM enrollment
            WHERE status = 'ACTIVE' AND offering_id IN (:ids)
            GROUP BY offering_id
            """, nativeQuery = true)
    List<Object[]> countActiveEnrollments(@Param("ids") Collection<Long> offeringIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CourseOffering o where o.offeringId = :offeringId")
    Optional<CourseOffering> findByIdForUpdate(@Param("offeringId") Long offeringId);

    boolean existsByCourse_CourseIdAndTerm_TermIdAndSectionIgnoreCase(
            Long courseId,
            Long termId,
            String section
    );

    boolean existsByCourse_CourseIdAndTerm_TermIdAndSectionIgnoreCaseAndOfferingIdNot(
            Long courseId,
            Long termId,
            String section,
            Long offeringId
    );

    // Old rows may have no section (NULL): these two replace the old findAll() scan
    boolean existsByCourse_CourseIdAndTerm_TermIdAndSectionIsNull(
            Long courseId,
            Long termId
    );

    boolean existsByCourse_CourseIdAndTerm_TermIdAndSectionIsNullAndOfferingIdNot(
            Long courseId,
            Long termId,
            Long offeringId
    );
}
