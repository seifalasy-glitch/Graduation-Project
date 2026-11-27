package com.edusync.edusync.backend.academic.service;

import com.edusync.edusync.backend.TestcontainersConfiguration;
import com.edusync.edusync.backend.academic.dto.AcademicProgramResponse;
import com.edusync.edusync.backend.academic.dto.CreateAcademicProgramRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Program.studentCount / courseCount (rules approved 27 Nov 2026), on the
 * throwaway Testcontainers database. @Transactional: every test is rolled back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class AcademicProgramCountsTest {

    @Autowired AcademicProgramService programService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void countsActiveStudentsOnly_andEachPlanCourseOnce() {
        long cs = program("T-CS");
        long empty = program("T-EMPTY");

        student("t-s1", "ACTIVE", cs);
        student("t-s2", "ACTIVE", cs);
        student("t-s3", "DISABLED", cs);       // not counted

        long algo = course("T-ALG");
        long db = course("T-DB");
        plan(cs, algo, 1);
        plan(cs, algo, 2);                     // same course in another level: counted once
        plan(cs, db, 1);

        AcademicProgramResponse csRow = find(cs);
        assertEquals(2, csRow.studentCount());
        assertEquals(2, csRow.courseCount());

        AcademicProgramResponse emptyRow = find(empty);
        assertEquals(0, emptyRow.studentCount());
        assertEquals(0, emptyRow.courseCount());
    }

    @Test
    void newProgram_hasZeroCounts() {
        AcademicProgramResponse created = programService.createProgram(
                new CreateAcademicProgramRequest("T-NEW", "Test New", null, null));

        assertEquals(0, created.studentCount());
        assertEquals(0, created.courseCount());
    }

    // ---------------- helpers: insert rows straight into the test database ----------------

    private AcademicProgramResponse find(long programId) {
        return programService.getPrograms(false).stream()
                .filter(p -> p.programId() == programId)
                .findFirst()
                .orElseThrow();
    }

    private long program(String code) {
        return jdbc.queryForObject(
                "INSERT INTO academic_program (code, name, active) VALUES (?, ?, true) RETURNING program_id",
                Long.class, code, code + " program");
    }

    private void student(String name, String status, long programId) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (university_id, email, full_name, password_hash, status)
                VALUES (?, ?, ?, 'x', ?) RETURNING user_id
                """, Long.class, name, name + "@sci.asu.edu.eg", name, status);
        jdbc.update("INSERT INTO student_profile (user_id, program_id, level) VALUES (?, ?, 1)",
                userId, programId);
    }

    private long course(String code) {
        return jdbc.queryForObject(
                "INSERT INTO course (course_code, title, credit_hours) VALUES (?, ?, 3) RETURNING course_id",
                Long.class, code, code + " course");
    }

    private void plan(long programId, long courseId, int level) {
        jdbc.update("INSERT INTO program_plan_course (program_id, course_id, level, term_type) VALUES (?, ?, ?, 'FALL')",
                programId, courseId, level);
    }
}
