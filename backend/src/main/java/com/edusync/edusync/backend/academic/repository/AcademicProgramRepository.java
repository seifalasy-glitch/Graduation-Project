package com.edusync.edusync.backend.academic.repository;

import com.edusync.edusync.backend.academic.entity.AcademicProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AcademicProgramRepository extends JpaRepository<AcademicProgram, Long> {

    List<AcademicProgram> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndProgramIdNot(String code, Long programId);

    /**
     * Program.studentCount: students whose profile is in the program AND whose account
     * is ACTIVE (disabled accounts are not counted). One query for all the given programs.
     * Each row: [programId, count]. Programs with no students are missing from the result.
     */
    @Query(value = """
            SELECT sp.program_id, COUNT(*)
            FROM student_profile sp
            JOIN users u ON u.user_id = sp.user_id
            WHERE u.status = 'ACTIVE' AND sp.program_id IN (:programIds)
            GROUP BY sp.program_id
            """, nativeQuery = true)
    List<Object[]> countActiveStudents(@Param("programIds") Collection<Long> programIds);

    /**
     * Program.courseCount: different courses in the program's study plan (a course
     * planned in two levels/terms counts once). Each row: [programId, count].
     */
    @Query(value = """
            SELECT ppc.program_id, COUNT(DISTINCT ppc.course_id)
            FROM program_plan_course ppc
            WHERE ppc.program_id IN (:programIds)
            GROUP BY ppc.program_id
            """, nativeQuery = true)
    List<Object[]> countPlanCourses(@Param("programIds") Collection<Long> programIds);
}
