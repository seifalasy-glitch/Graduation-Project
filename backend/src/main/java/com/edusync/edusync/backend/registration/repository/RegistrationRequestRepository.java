package com.edusync.edusync.backend.registration.repository;

import jakarta.persistence.LockModeType;
import com.edusync.edusync.backend.registration.entity.RegistrationRequest;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {

    Optional<RegistrationRequest> findByStudentUserIdAndTermId(Long studentUserId, Long termId);

    List<RegistrationRequest> findByAdvisorId(Long advisorId);

    List<RegistrationRequest> findByStatusNot(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationRequest r where r.studentUserId = :studentId and r.termId = :termId")
    Optional<RegistrationRequest> findForUpdate(@Param("studentId") Long studentId, @Param("termId") Long termId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RegistrationRequest r where r.requestId = :requestId")
    Optional<RegistrationRequest> findByIdForUpdate(@Param("requestId") Long requestId);
}
