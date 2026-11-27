package com.edusync.edusync.backend.attendance;

import com.edusync.edusync.backend.TestcontainersConfiguration;
import com.edusync.edusync.backend.attendance.service.AttendanceWarningService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The warning email really goes out after commit. NOT @Transactional on purpose (emails
 * are only sent when the save commits), so the rows are committed and deleted in cleanUp().
 * The term lies in the past so it never becomes another test's "current term".
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AttendanceWarningEmailTest {

    @Autowired AttendanceWarningService warnings;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mailSender;

    long program, term, course, offering, group, doctor, student, enrollment, assignment, session, rule;

    @BeforeEach
    void setUp() {
        LocalDate termStart = LocalDate.now().minusDays(200);
        program = id("INSERT INTO academic_program (code, name, active) VALUES ('MAIL', 'Mail', true) RETURNING program_id");
        term = id("INSERT INTO academic_term (academic_year, term_type, start_date, end_date) VALUES ('2095/2096', 'SPRING', ?, ?) RETURNING term_id",
                termStart, termStart.plusDays(100));
        course = id("INSERT INTO course (course_code, title, credit_hours) VALUES ('MAIL101', 'Mail Course', 3) RETURNING course_id");
        offering = id("INSERT INTO course_offering (course_id, term_id, section) VALUES (?, ?, '1') RETURNING offering_id", course, term);
        group = id("INSERT INTO student_group (offering_id, group_type, label) VALUES (?, 'PRACTICAL', 'L1') RETURNING group_id", offering);
        doctor = id("INSERT INTO users (university_id, email, full_name, password_hash) VALUES ('mail.d1', 'mail.d1@sci.asu.edu.eg', 'Mona Doctor', 'x') RETURNING user_id");
        student = id("INSERT INTO users (university_id, email, full_name, password_hash) VALUES ('mail.s1', 'mail.s1@sci.asu.edu.eg', 'Ali Student', 'x') RETURNING user_id");
        enrollment = id("INSERT INTO enrollment (student_user_id, offering_id, program_id, status) VALUES (?, ?, ?, 'ACTIVE') RETURNING enrollment_id",
                student, offering, program);
        LocalDateTime start = termStart.plusDays(8).atTime(12, 0);           // week 2
        jdbc.update("INSERT INTO group_membership (enrollment_id, group_id, starts_at) VALUES (?, ?, ?)", enrollment, group, start.minusDays(7));
        assignment = id("INSERT INTO teaching_assignment (group_id, doctor_user_id, starts_at) VALUES (?, ?, ?) RETURNING assignment_id",
                group, doctor, start.minusDays(7));
        session = id("INSERT INTO attendance_session (group_id, method, state, teaching_assignment_id, starts_at, ends_at) "
                + "VALUES (?, 'MANUAL', 'FINISHED', ?, ?, ?) RETURNING session_id", group, assignment, start, start.plusHours(2));
        jdbc.update("INSERT INTO attendance_record (session_id, enrollment_id, status, method, marked_at) VALUES (?, ?, 'ABSENT', 'MANUAL', ?)",
                session, enrollment, start);
        rule = id("INSERT INTO attendance_warning_rule (offering_id, group_type, enabled, absence_limit, updated_by) "
                + "VALUES (?, 'PRACTICAL', true, 1, ?) RETURNING rule_id", offering, doctor);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM attendance_warning WHERE rule_id = ?", rule);
        jdbc.update("DELETE FROM attendance_warning_rule WHERE rule_id = ?", rule);
        jdbc.update("DELETE FROM attendance_record WHERE session_id = ?", session);
        jdbc.update("DELETE FROM attendance_session WHERE session_id = ?", session);
        jdbc.update("DELETE FROM teaching_assignment WHERE assignment_id = ?", assignment);
        jdbc.update("DELETE FROM group_membership WHERE enrollment_id = ?", enrollment);
        jdbc.update("DELETE FROM enrollment WHERE enrollment_id = ?", enrollment);
        jdbc.update("DELETE FROM student_group WHERE group_id = ?", group);
        jdbc.update("DELETE FROM course_offering WHERE offering_id = ?", offering);
        jdbc.update("DELETE FROM course WHERE course_id = ?", course);
        jdbc.update("DELETE FROM academic_term WHERE term_id = ?", term);
        jdbc.update("DELETE FROM academic_program WHERE program_id = ?", program);
        jdbc.update("DELETE FROM users WHERE user_id IN (?, ?)", doctor, student);
    }

    @Test
    void warningEmail_isSentOnce_afterCommit_withTheContractText() throws Exception {
        warnings.recheck(offering, "PRACTICAL");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(5000).times(1)).send(sent.capture());
        SimpleMailMessage mail = sent.getValue();
        assertArrayEquals(new String[]{"mail.s1@sci.asu.edu.eg"}, mail.getTo());
        assertEquals("Attendance warning – MAIL101 Mail Course", mail.getSubject());
        assertTrue(mail.getText().startsWith("Dear Ali Student,"), mail.getText());
        assertTrue(mail.getText().contains("absent 1 times from the lab of MAIL101 Mail Course (weeks 2)"), mail.getText());
        assertTrue(mail.getText().contains("The limit set by Dr. Mona Doctor is 1 absences."), mail.getText());

        // emailed_at is filled once the mail went out (on the mail thread)
        LocalDateTime emailedAt = null;
        for (int i = 0; i < 50 && emailedAt == null; i++) {
            emailedAt = jdbc.queryForObject("SELECT emailed_at FROM attendance_warning WHERE rule_id = ?", LocalDateTime.class, rule);
            if (emailedAt == null) {
                Thread.sleep(100);
            }
        }
        assertNotNull(emailedAt, "emailed_at was not set");

        // re-checking again does not send a second email
        warnings.recheck(offering, "PRACTICAL");
        Thread.sleep(300);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
