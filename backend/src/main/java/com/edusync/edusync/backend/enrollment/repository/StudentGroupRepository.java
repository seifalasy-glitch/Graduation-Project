package com.edusync.edusync.backend.enrollment.repository;

import com.edusync.edusync.backend.enrollment.entity.StudentGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentGroupRepository extends JpaRepository<StudentGroup, Long> {

    @Query("select g.offeringId from StudentGroup g where g.groupId = :groupId")
    Optional<Long> findOfferingId(@Param("groupId") Long groupId);

    List<StudentGroup> findByOfferingIdOrderByGroupTypeAscLabelAsc(Long offeringId);

    boolean existsByOfferingIdAndGroupTypeAndLabelIgnoreCase(
            Long offeringId,
            String groupType,
            String label
    );

    boolean existsByOfferingIdAndGroupTypeAndLabelIgnoreCaseAndGroupIdNot(
            Long offeringId,
            String groupType,
            String label,
            Long groupId
    );
}
