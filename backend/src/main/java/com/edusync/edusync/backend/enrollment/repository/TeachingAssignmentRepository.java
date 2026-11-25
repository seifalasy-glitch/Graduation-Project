package com.edusync.edusync.backend.enrollment.repository;

import com.edusync.edusync.backend.enrollment.entity.TeachingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {

    List<TeachingAssignment> findByGroupIdOrderByStartsAtDesc(Long groupId);

    @Query("""
            select count(a) from TeachingAssignment a, StudentGroup g
            where a.groupId = g.groupId
              and g.offeringId = :offeringId
              and a.doctorUserId = :doctorId
              and a.startsAt <= :now
              and (a.endsAt is null or a.endsAt > :now)
            """)
    long countTeachingOffering(
            @Param("doctorId") Long doctorId,
            @Param("offeringId") Long offeringId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select count(a) from TeachingAssignment a
            where a.groupId = :groupId
              and a.doctorUserId = :doctorId
              and (a.endsAt is null or a.endsAt > :now)
            """)
    long countCurrent(
            @Param("groupId") Long groupId,
            @Param("doctorId") Long doctorId,
            @Param("now") LocalDateTime now
    );
}
