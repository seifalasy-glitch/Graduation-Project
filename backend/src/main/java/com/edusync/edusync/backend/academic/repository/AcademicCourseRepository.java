package com.edusync.edusync.backend.academic.repository;

import com.edusync.edusync.backend.academic.entity.AcademicCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AcademicCourseRepository extends JpaRepository<AcademicCourse, Long> {

    List<AcademicCourse> findAllByOrderByCourseCodeAsc();

    @Query("""
            select c from AcademicCourse c
            where lower(c.courseCode) like lower(concat('%', :q, '%'))
               or lower(c.title) like lower(concat('%', :q, '%'))
            order by c.courseCode asc
            """)
    List<AcademicCourse> search(@Param("q") String q);

    boolean existsByCourseCodeIgnoreCase(String courseCode);

    boolean existsByCourseCodeIgnoreCaseAndCourseIdNot(
            String courseCode,
            Long courseId
    );
}
