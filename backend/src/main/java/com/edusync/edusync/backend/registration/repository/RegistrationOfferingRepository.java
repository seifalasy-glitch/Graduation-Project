package com.edusync.edusync.backend.registration.repository;

import com.edusync.edusync.backend.academic.entity.CourseOffering;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationOfferingRepository extends JpaRepository<CourseOffering, Long> {

    @Query("select o from CourseOffering o join fetch o.course join fetch o.term where o.term.termId = :termId")
    List<CourseOffering> findByTermWithCourse(@Param("termId") Long termId);

    @Query("select o from CourseOffering o join fetch o.course join fetch o.term where o.offeringId in :ids")
    List<CourseOffering> findWithCourseByIdIn(@Param("ids") Collection<Long> ids);
}
