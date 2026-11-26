package com.edusync.edusync.backend.registration.repository;

import com.edusync.edusync.backend.enrollment.entity.TeachingAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationTeachingRepository extends JpaRepository<TeachingAssignment, Long> {

    @Query("""
            select a from TeachingAssignment a
            where a.groupId in :groupIds
              and a.startsAt <= :now
              and (a.endsAt is null or a.endsAt > :now)
            """)
    List<TeachingAssignment> findCurrentByGroupIds(
            @Param("groupIds") Collection<Long> groupIds,
            @Param("now") LocalDateTime now
    );
}
