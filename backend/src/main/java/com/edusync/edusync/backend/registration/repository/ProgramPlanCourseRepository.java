package com.edusync.edusync.backend.registration.repository;

import com.edusync.edusync.backend.registration.entity.ProgramPlanCourse;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramPlanCourseRepository extends JpaRepository<ProgramPlanCourse, Long> {

    List<ProgramPlanCourse> findByProgramIdOrderByLevelAscTermTypeAscPlanCourseIdAsc(Long programId);

    List<ProgramPlanCourse> findByProgramIdAndLevelAndTermType(Long programId, Integer level, String termType);

    boolean existsByProgramIdAndLevelAndTermTypeAndCourseId(
            Long programId,
            Integer level,
            String termType,
            Long courseId
    );
}
