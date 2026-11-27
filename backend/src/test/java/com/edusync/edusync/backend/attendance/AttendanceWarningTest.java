package com.edusync.edusync.backend.attendance;

import com.edusync.edusync.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Attendance warnings C1 (rules, counting, finish / correction hooks, access).
 * Rolled back after each test, so no email is ever sent here (emails go after commit;
 * see AttendanceWarningEmailTest).
 *
 * Course WRN101, lecture A only (no lab), doctor d1. Term started 30 days ago, so
 * sessions on day 1 / 8 / 15 of the term are weeks 1 / 2 / 3. All three are FINISHED:
 *   s1: ABSENT, ABSENT, PRESENT  -> 2 absences, weeks [1, 2]
 *   s2: LATE,   LATE,  (none)    -> 1 unmarked + 2 LATE/2 = 2 absences, weeks [3]
 *   s3: PRESENT x3               -> 0
 *   s4: WITHDRAWN, ABSENT x3     -> skipped
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class AttendanceWarningTest {

    static final String D1 = "wrn.d1@sci.asu.edu.eg", D2 = "wrn.d2@sci.asu.edu.eg", A1 = "wrn.a1@sci.asu.edu.eg";
    static final String S1 = "wrn.s1@sci.asu.edu.eg", S3 = "wrn.s3@sci.asu.edu.eg";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mailSender;   // same as AttendanceWarningEmailTest, so both share one context

    long offering, lecture, e3, week1Session;

    @BeforeEach
    void setUp() {
        LocalDate termStart = LocalDate.now().minusDays(30);
        LocalDateTime lastMonth = termStart.atStartOfDay();
        long program = id("INSERT INTO academic_program (code, name, active) VALUES ('WRN', 'Warnings', true) RETURNING program_id");
        long term = id("INSERT INTO academic_term (academic_year, term_type, start_date, end_date) VALUES ('2094/2095', 'FALL', ?, ?) RETURNING term_id",
                termStart, termStart.plusDays(120));
        long course = id("INSERT INTO course (course_code, title, credit_hours) VALUES ('WRN101', 'Warnings 101', 3) RETURNING course_id");
        offering = id("INSERT INTO course_offering (course_id, term_id, section) VALUES (?, ?, '1') RETURNING offering_id", course, term);
        lecture = id("INSERT INTO student_group (offering_id, group_type, label, day_of_week, start_time, end_time) "
                + "VALUES (?, 'LECTURE', 'A', 'SUNDAY', '10:00', '12:00') RETURNING group_id", offering);

        long d1 = newUser("wrn.d1", D1);
        newUser("wrn.d2", D2);
        newUser("wrn.a1", A1);
        long e1 = enroll(newUser("wrn.s1", S1), program, "ACTIVE");
        long e2 = enroll(newUser("wrn.s2", "wrn.s2@sci.asu.edu.eg"), program, "ACTIVE");
        e3 = enroll(newUser("wrn.s3", S3), program, "ACTIVE");
        long e4 = enroll(newUser("wrn.s4", "wrn.s4@sci.asu.edu.eg"), program, "WITHDRAWN");
        for (long e : new long[]{e1, e2, e3, e4}) {
            jdbc.update("INSERT INTO group_membership (enrollment_id, group_id, starts_at) VALUES (?, ?, ?)", e, lecture, lastMonth);
        }
        long t1 = id("INSERT INTO teaching_assignment (group_id, doctor_user_id, starts_at) VALUES (?, ?, ?) RETURNING assignment_id",
                lecture, d1, lastMonth);

        week1Session = session(t1, termStart.plusDays(1));
        long w2 = session(t1, termStart.plusDays(8));
        long w3 = session(t1, termStart.plusDays(15));
        mark(week1Session, e1, "ABSENT"); mark(w2, e1, "ABSENT"); mark(w3, e1, "PRESENT");
        mark(week1Session, e2, "LATE");   mark(w2, e2, "LATE");
        mark(week1Session, e3, "PRESENT"); mark(w2, e3, "PRESENT"); mark(w3, e3, "PRESENT");
        mark(week1Session, e4, "ABSENT"); mark(w2, e4, "ABSENT"); mark(w3, e4, "ABSENT");
    }

    // ================================================================ rules

    @Test
    void rules_onePerGroupTypeOfTheCourse_offUntilSaved() throws Exception {
        mvc.perform(get("/api/offerings/" + offering + "/attendance-warning-rules").with(user(D1).roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))                         // no lab in this course
                .andExpect(jsonPath("$[0].groupType").value("LECTURE"))
                .andExpect(jsonPath("$[0].ruleId").value(nullValue()))
                .andExpect(jsonPath("$[0].enabled").value(false));
    }

    @Test
    void saveRule_errors() throws Exception {
        rule(D1, "PRACTICAL", "{\"enabled\":true,\"absenceLimit\":2}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_SUCH_GROUP_TYPE"));
        rule(D1, "LECTURE", "{\"enabled\":true}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT_REQUIRED"));
        rule(D1, "LECTURE", "{\"enabled\":true,\"absenceLimit\":0}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        rule(D1, "LAB", "{\"enabled\":true,\"absenceLimit\":2}")
                .andExpect(status().isBadRequest());
        rule(D2, "LECTURE", "{\"enabled\":true,\"absenceLimit\":2}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_YOUR_COURSE"));
    }

    @Test
    void turningOff_withoutLimit_keepsTheOldLimit() throws Exception {
        rule(D1, "LECTURE", "{\"enabled\":true,\"absenceLimit\":4}").andExpect(status().isOk());
        rule(D1, "LECTURE", "{\"enabled\":false}")
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.absenceLimit").value(4));
    }

    // ================================================================ warnings

    @Test
    void turningOn_warnsStudentsAtTheLimit_withCurrentCountsAndWeeks() throws Exception {
        rule(D1, "LECTURE", "{\"enabled\":true,\"absenceLimit\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").isNumber())
                .andExpect(jsonPath("$.absenceLimit").value(2));

        warnings(D1, "DOCTOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))                         // s3 (0) and s4 (withdrawn) not warned
                .andExpect(jsonPath("$[0].student.universityId").value("wrn.s1"))
                .andExpect(jsonPath("$[0].absenceCount").value(2))
                .andExpect(jsonPath("$[0].absentWeeks", contains(1, 2)))
                .andExpect(jsonPath("$[0].groupLabel").value("A"))
                .andExpect(jsonPath("$[0].groupSchedule").value("Sunday 10:00–12:00"))
                .andExpect(jsonPath("$[0].programName").value("Warnings"))
                .andExpect(jsonPath("$[0].courseCode").value("WRN101"))
                .andExpect(jsonPath("$[0].absenceLimit").value(2))
                .andExpect(jsonPath("$[0].emailedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].student.universityId").value("wrn.s2"))
                .andExpect(jsonPath("$[1].absenceCount").value(2))
                .andExpect(jsonPath("$[1].lateCount").value(2))
                .andExpect(jsonPath("$[1].absentWeeks", contains(3)));

        mvc.perform(get("/api/me/attendance-warnings").with(user(S1).roles("STUDENT")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].courseCode").value("WRN101"));
        mvc.perform(get("/api/me/attendance-warnings").with(user(S3).roles("STUDENT")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aStudentIsWarnedOnlyOnce_evenWhenTheLimitChanges() throws Exception {
        rule(D1, "LECTURE", "{\"enabled\":true,\"absenceLimit\":2}");
        rule(D1, "LECTURE", "{\"enabled\":true,\"absenceLimit\":1}");
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM attendance_warning w JOIN attendance_warning_rule r ON r.rule_id = w.rule_id
                WHERE r.offering_id = ?""", Integer.class, offering));
    }

    @Test
    void finishingASession_canCreateAWarning() throws Exception {
        rule(D1, "LECTURE", "{\"enabled\":true,\"absenceLimit\":1}");
        long session = ((Number) JsonPath.read(mvc.perform(post("/api/attendance/sessions").with(user(D1).roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"groupId\":" + lecture + ",\"method\":\"MANUAL\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.sessionId")).longValue();
        mvc.perform(put("/api/attendance/sessions/" + session + "/records/" + e3).with(user(D1).roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ABSENT\"}")).andExpect(status().isOk());

        mvc.perform(get("/api/me/attendance-warnings").with(user(S3).roles("STUDENT"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/attendance/sessions/" + session + "/finish").with(user(D1).roles("DOCTOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/attendance-warnings").with(user(S3).roles("STUDENT")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].absenceCount").value(1));
    }

    @Test
    void aCorrection_canCreateAWarning() throws Exception {
        rule(D1, "LECTURE", "{\"enabled\":true,\"absenceLimit\":1}");
        mvc.perform(post("/api/attendance/sessions/" + week1Session + "/records/" + e3 + "/correction")
                        .with(user(D1).roles("DOCTOR")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"ABSENT\",\"reason\":\"MANUAL_MARKING_ERROR\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/attendance-warnings").with(user(S3).roles("STUDENT")))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].absentWeeks", contains(1)));
    }

    @Test
    void access_adminReads_otherDoctorCannot() throws Exception {
        rule(D1, "LECTURE", "{\"enabled\":true,\"absenceLimit\":2}");
        warnings(A1, "ADMIN").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        warnings(D2, "DOCTOR").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_YOUR_COURSE"));
        mvc.perform(get("/api/offerings/999999/attendance-warnings").with(user(A1).roles("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/offerings/" + offering + "/attendance-warnings").param("groupType", "PRACTICAL")
                        .with(user(D1).roles("DOCTOR")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ================================================================ helpers

    private ResultActions rule(String doctor, String type, String body) throws Exception {
        return mvc.perform(put("/api/offerings/" + offering + "/attendance-warning-rules/" + type)
                .with(user(doctor).roles("DOCTOR")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions warnings(String who, String role) throws Exception {
        return mvc.perform(get("/api/offerings/" + offering + "/attendance-warnings").with(user(who).roles(role)));
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long newUser(String name, String email) {
        return id("INSERT INTO users (university_id, email, full_name, password_hash) VALUES (?, ?, ?, 'x') RETURNING user_id",
                name, email, name);
    }

    private long enroll(long student, long program, String status) {
        return id("INSERT INTO enrollment (student_user_id, offering_id, program_id, status) VALUES (?, ?, ?, ?) RETURNING enrollment_id",
                student, offering, program, status);
    }

    private long session(long assignment, LocalDate day) {
        LocalDateTime start = day.atTime(10, 0);
        return id("INSERT INTO attendance_session (group_id, method, state, teaching_assignment_id, starts_at, ends_at) "
                + "VALUES (?, 'MANUAL', 'FINISHED', ?, ?, ?) RETURNING session_id", lecture, assignment, start, start.plusHours(2));
    }

    private void mark(long session, long enrollment, String status) {
        jdbc.update("INSERT INTO attendance_record (session_id, enrollment_id, status, method, marked_at) VALUES (?, ?, ?, 'MANUAL', ?)",
                session, enrollment, status, LocalDateTime.now());
    }
}
