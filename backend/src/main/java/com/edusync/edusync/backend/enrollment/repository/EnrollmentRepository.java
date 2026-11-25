package com.edusync.edusync.backend.enrollment.repository;

import com.edusync.edusync.backend.enrollment.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.enrollmentId = :enrollmentId")
    Optional<Enrollment> findByIdForUpdate(@Param("enrollmentId") Long enrollmentId);

    @Query("select e.offeringId from Enrollment e where e.enrollmentId = :enrollmentId")
    Optional<Long> findOfferingId(@Param("enrollmentId") Long enrollmentId);

    List<Enrollment> findByOfferingIdOrderByEnrollmentIdAsc(Long offeringId);

    boolean existsByStudentUserIdAndOfferingId(Long studentUserId, Long offeringId);

    long countByOfferingIdAndStatus(Long offeringId, String status);
}
