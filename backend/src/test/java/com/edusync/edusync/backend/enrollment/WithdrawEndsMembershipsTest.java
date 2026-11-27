package com.edusync.edusync.backend.enrollment;

import com.edusync.edusync.backend.TestcontainersConfiguration;
import com.edusync.edusync.backend.enrollment.dto.UpdateEnrollmentRequest;
import com.edusync.edusync.backend.enrollment.service.EnrollmentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PATCH /enrollments/{id} with status WITHDRAWN ends the student's groups:
 * open memberships end now, not-started ones are removed, already-ended ones stay
 * as they were, and other students are untouched. Rolled back after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class WithdrawEndsMembershipsTest {

    @Autowired EnrollmentService enrollmentService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    long enrollment;
    long otherEnrollment;
    long openMembership;
    long endedMembership;
    long futureMembership;
    long otherMembership;
    LocalDateTime lastWeek = LocalDateTime.now().minusDays(7);
    LocalDateTime yesterday = LocalDateTime.now().minusDays(1);

    @BeforeEach
    void setUp() {
        long program = id("INSERT INTO academic_program (code, name, active) VALUES ('WD', 'WD', true) RETURNING program_id");
        long term = id("INSERT INTO academic_term (academic_year, term_type, start_date, end_date) VALUES ('2091/2092', 'FALL', ?, ?) RETURNING term_id",
                LocalDate.now().minusDays(30), LocalDate.now().plusDays(30));
        long course = id("INSERT INTO course (course_code, title, credit_hours) VALUES ('WD101', 'WD', 3) RETURNING course_id");
        long offering = id("INSERT INTO course_offering (course_id, term_id, section) VALUES (?, ?, '1') RETURNING offering_id", course, term);
        long lecture = id("INSERT INTO student_group (offering_id, group_type, label) VALUES (?, 'LECTURE', 'A') RETURNING group_id", offering);
        long lab = id("INSERT INTO student_group (offering_id, group_type, label) VALUES (?, 'PRACTICAL', 'L1') RETURNING group_id", offering);
        long lab2 = id("INSERT INTO student_group (offering_id, group_type, label) VALUES (?, 'PRACTICAL', 'L2') RETURNING group_id", offering);

        enrollment = enrollment("wd.s1", offering, program);
        otherEnrollment = enrollment("wd.s2", offering, program);

        openMembership = membership(enrollment, lecture, lastWeek, null);
        endedMembership = membership(enrollment, lab, lastWeek, yesterday);
        futureMembership = membership(enrollment, lab2, LocalDateTime.now().plusDays(3), null);
        otherMembership = membership(otherEnrollment, lecture, lastWeek, null);
    }

    @Test
    void withdraw_endsOpenGroups_removesFutureOnes_keepsHistory() {
        LocalDateTime before = LocalDateTime.now();

        enrollmentService.update(enrollment, new UpdateEnrollmentRequest(null, "WITHDRAWN", null));
        entityManager.flush();   // the real request commits here; the test reads with plain SQL

        LocalDateTime openEnd = endsAt(openMembership);
        assertNotNull(openEnd, "open membership was not ended");
        assertFalse(openEnd.isBefore(before.minusSeconds(1)), "ended at the wrong time: " + openEnd);

        assertEquals(yesterday.withNano(0), endsAt(endedMembership).withNano(0), "an already-ended membership changed");
        assertEquals(0, count("SELECT COUNT(*) FROM group_membership WHERE membership_id = ?", futureMembership),
                "future membership was not removed");
        assertNull(endsAt(otherMembership), "another student's membership was touched");
        assertEquals("WITHDRAWN", jdbc.queryForObject(
                "SELECT status FROM enrollment WHERE enrollment_id = ?", String.class, enrollment));
    }

    @Test
    void otherStatusChanges_doNotTouchGroups() {
        enrollmentService.update(enrollment, new UpdateEnrollmentRequest(null, "COMPLETED", null));
        entityManager.flush();

        assertNull(endsAt(openMembership));
        assertEquals(1, count("SELECT COUNT(*) FROM group_membership WHERE membership_id = ?", futureMembership));
    }

    // ---------------- helpers ----------------

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long enrollment(String name, long offering, long program) {
        long user = id("INSERT INTO users (university_id, email, full_name, password_hash) VALUES (?, ?, ?, 'x') RETURNING user_id",
                name, name + "@sci.asu.edu.eg", name);
        return id("INSERT INTO enrollment (student_user_id, offering_id, program_id, status) VALUES (?, ?, ?, 'ACTIVE') RETURNING enrollment_id",
                user, offering, program);
    }

    private long membership(long enrollment, long group, LocalDateTime from, LocalDateTime to) {
        return id("INSERT INTO group_membership (enrollment_id, group_id, starts_at, ends_at) VALUES (?, ?, ?, ?) RETURNING membership_id",
                enrollment, group, from, to);
    }

    private LocalDateTime endsAt(long membership) {
        Timestamp t = jdbc.queryForObject("SELECT ends_at FROM group_membership WHERE membership_id = ?", Timestamp.class, membership);
        return t == null ? null : t.toLocalDateTime();
    }
}
