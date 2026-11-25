package com.edusync.edusync.backend.enrollment.repository;

import com.edusync.edusync.backend.enrollment.entity.GroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {

    List<GroupMembership> findByGroupId(Long groupId);

    List<GroupMembership> findByEnrollmentId(Long enrollmentId);

    @Query("select m.groupId from GroupMembership m where m.membershipId = :membershipId")
    Optional<Long> findGroupId(@Param("membershipId") Long membershipId);

    @Query("""
            select case when count(m) > 0 then true else false end
            from GroupMembership m
            where m.enrollmentId = :enrollmentId
              and m.groupId = :groupId
              and (m.endsAt is null or m.endsAt > :startsAt)
            """)
    boolean existsOverlappingMembershipInSameGroup(
            @Param("enrollmentId") Long enrollmentId,
            @Param("groupId") Long groupId,
            @Param("startsAt") LocalDateTime startsAt
    );

    @Query("""
            select case when count(m) > 0 then true else false end
            from GroupMembership m, StudentGroup g
            where m.groupId = g.groupId
              and m.enrollmentId = :enrollmentId
              and m.groupId <> :groupId
              and g.groupType = :groupType
              and (m.endsAt is null or m.endsAt > :startsAt)
            """)
    boolean existsOverlappingMembershipInSameType(
            @Param("enrollmentId") Long enrollmentId,
            @Param("groupId") Long groupId,
            @Param("groupType") String groupType,
            @Param("startsAt") LocalDateTime startsAt
    );

    @Query("""
            select case when count(m) > 0 then true else false end
            from GroupMembership m, StudentGroup g
            where m.groupId = g.groupId
              and m.enrollmentId = :enrollmentId
              and m.membershipId <> :membershipId
              and g.groupType = :groupType
              and m.startsAt < :endsAt
              and (m.endsAt is null or m.endsAt > :startsAt)
            """)
    boolean existsOverlappingMembershipForUpdate(
            @Param("enrollmentId") Long enrollmentId,
            @Param("membershipId") Long membershipId,
            @Param("groupType") String groupType,
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt
    );

    @Query("""
            select m.groupId, count(m) from GroupMembership m
            where m.groupId in :groupIds
              and m.startsAt <= :now
              and (m.endsAt is null or m.endsAt > :now)
            group by m.groupId
            """)
    List<Object[]> countCurrentByGroup(
            @Param("groupIds") Collection<Long> groupIds,
            @Param("now") LocalDateTime now
    );
    @Query("""
            select case when count(m) > 0 then true else false end
            from GroupMembership m, GroupMembership other, StudentGroup g
            where m.groupId = :groupId
              and other.groupId <> :groupId
              and other.enrollmentId = m.enrollmentId
              and other.groupId = g.groupId
              and g.groupType = :groupType
              and (m.endsAt is null or other.startsAt < m.endsAt)
              and (other.endsAt is null or m.startsAt < other.endsAt)
            """)
    boolean existsOverlapForGroupTypeChange(
            @Param("groupId") Long groupId,
            @Param("groupType") String groupType
    );

}
