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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Attendance phase B: corrections, change history, course summary, /me/attendance and the
 * percentages on the home screens. Sessions are inserted directly so the numbers are exact.
 *
 *   Lecture A (doctor d1)                 Lab L1 (doctor d2)
 *   #1 FINISHED: s1 PRESENT, s2 LATE      #4 FINISHED: s1 PRESENT, s2 ABSENT
 *   #2 FINISHED: s1 ABSENT,  s2 (none)
 *   #3 QR_OPEN (in progress): s1 PRESENT  <- never counted in totals
 *
 * Expected (finished only, % = (present + late) / sessions):
 *   whole course: s1 2/3 = 66.7, s2 1/3 = 33.3;  lecture only: s1 50.0, s2 50.0;
 *   lecture A average: 2 attended of 4 slots = 50.0
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class AttendanceReportTest {

    static final String D1 = "rep.d1@sci.asu.edu.eg", D2 = "rep.d2@sci.asu.edu.eg", D3 = "rep.d3@sci.asu.edu.eg";
    static final String A1 = "rep.a1@sci.asu.edu.eg", S1 = "rep.s1@sci.asu.edu.eg", S2 = "rep.s2@sci.asu.edu.eg";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    long offering, otherOffering, lecture, lab, otherGroup, e1, e2, s2Session, openSession;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        long program = id("INSERT INTO academic_program (code, name, active) VALUES ('REP', 'Rep', true) RETURNING program_id");
        long term = id("INSERT INTO academic_term (academic_year, term_type, start_date, end_date) "
                + "VALUES ('2093/2094', 'FALL', CURRENT_DATE - 30, CURRENT_DATE + 30) RETURNING term_id");
        long course = id("INSERT INTO course (course_code, title, credit_hours) VALUES ('REP101', 'Reports', 3) RETURNING course_id");
        long course2 = id("INSERT INTO course (course_code, title, credit_hours) VALUES ('REP102', 'Other', 3) RETURNING course_id");
        offering = id("INSERT INTO course_offering (course_id, term_id, section) VALUES (?, ?, '1') RETURNING offering_id", course, term);
        otherOffering = id("INSERT INTO course_offering (course_id, term_id, section) VALUES (?, ?, '1') RETURNING offering_id", course2, term);
        lecture = group(offering, "LECTURE", "A");
        lab = group(offering, "PRACTICAL", "L1");
        otherGroup = group(otherOffering, "LECTURE", "A");

        long d1 = newUser("rep.d1", D1), d2 = newUser("rep.d2", D2);
        newUser("rep.d3", D3);
        newUser("rep.a1", A1);
        e1 = enroll(newUser("rep.s1", S1), program);
        e2 = enroll(newUser("rep.s2", S2), program);

        LocalDateTime lastMonth = now.minusDays(20);
        for (long e : new long[]{e1, e2}) {
            jdbc.update("INSERT INTO group_membership (enrollment_id, group_id, starts_at) VALUES (?, ?, ?)", e, lecture, lastMonth);
            jdbc.update("INSERT INTO group_membership (enrollment_id, group_id, starts_at) VALUES (?, ?, ?)", e, lab, lastMonth);
        }
        long t1 = id("INSERT INTO teaching_assignment (group_id, doctor_user_id, starts_at) VALUES (?, ?, ?) RETURNING assignment_id", lecture, d1, lastMonth);
        long t2 = id("INSERT INTO teaching_assignment (group_id, doctor_user_id, starts_at) VALUES (?, ?, ?) RETURNING assignment_id", lab, d2, lastMonth);

        long s1 = session(lecture, t1, now.minusDays(3), "FINISHED");
        record(s1, e1, "PRESENT");
        record(s1, e2, "LATE");
        s2Session = session(lecture, t1, now.minusDays(2), "FINISHED");
        record(s2Session, e1, "ABSENT");
        openSession = session(lecture, t1, now.minusMinutes(5), "QR_OPEN");
        record(openSession, e1, "PRESENT");
        long s4 = session(lab, t2, now.minusDays(1), "FINISHED");
        record(s4, e1, "PRESENT");
        record(s4, e2, "ABSENT");
    }

    // ================================================================ /me/attendance

    @Test
    void myAttendance_countsFinishedSessions_listsAll() throws Exception {
        mvc.perform(get("/api/me/attendance").param("offeringId", "" + offering).with(user(S1).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(2))
                .andExpect(jsonPath("$.late").value(0))
                .andExpect(jsonPath("$.absent").value(1))
                .andExpect(jsonPath("$.attendancePercent").value(66.7))
                .andExpect(jsonPath("$.sessions", hasSize(4)))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(openSession))   // newest first
                .andExpect(jsonPath("$.sessions[3].groupType").value("LECTURE"))
                .andExpect(jsonPath("$.sessions[3].status").value("PRESENT"));
    }

    @Test
    void myAttendance_courseNotEnrolled_isNotFound() throws Exception {
        mvc.perform(get("/api/me/attendance").param("offeringId", "" + otherOffering).with(user(S1).roles("STUDENT")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OFFERING_NOT_FOUND"));
    }

    // ================================================================ summary

    @Test
    void summary_admin_wholeCourse() throws Exception {
        summary(A1, "ADMIN", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].student.universityId").value("rep.s1"))
                .andExpect(jsonPath("$[0].sessionsTotal").value(3))
                .andExpect(jsonPath("$[0].attendancePercent").value(66.7))
                .andExpect(jsonPath("$[1].sessionsTotal").value(3))
                .andExpect(jsonPath("$[1].late").value(1))
                .andExpect(jsonPath("$[1].absent").value(1))
                .andExpect(jsonPath("$[1].unmarked").value(1))
                .andExpect(jsonPath("$[1].attendancePercent").value(33.3));
    }

    @Test
    void summary_doctor_seesOnlyOwnGroups() throws Exception {
        summary(D1, "DOCTOR", null)
                .andExpect(jsonPath("$[0].sessionsTotal").value(2))
                .andExpect(jsonPath("$[0].attendancePercent").value(50.0))
                .andExpect(jsonPath("$[1].attendancePercent").value(50.0));

        summary(D1, "DOCTOR", lab)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_YOUR_GROUP"));
        summary(D1, "DOCTOR", otherGroup)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        summary(D3, "DOCTOR", null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OFFERING_NOT_FOUND"));
    }

    // ================================================================ corrections

    @Test
    void correction_ofUnmarkedStudent_thenAgain_buildsHistory() throws Exception {
        correct(D1, s2Session, e2, "PRESENT", "QR_FAILED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.method").value("MANUAL"))
                .andExpect(jsonPath("$.corrected").value(true));
        long recordId = jdbc.queryForObject(
                "SELECT record_id FROM attendance_record WHERE session_id = ? AND enrollment_id = ?", Long.class, s2Session, e2);

        correct(D1, s2Session, e2, "ABSENT", "MANUAL_MARKING_ERROR").andExpect(status().isOk());

        mvc.perform(get("/api/attendance/records/" + recordId + "/changes").with(user(D1).roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].oldStatus").value(nullValue()))
                .andExpect(jsonPath("$[0].newStatus").value("PRESENT"))
                .andExpect(jsonPath("$[0].reason").value("QR_FAILED"))
                .andExpect(jsonPath("$[0].doctor.fullName").value("rep.d1"))
                .andExpect(jsonPath("$[1].oldStatus").value("PRESENT"))
                .andExpect(jsonPath("$[1].newStatus").value("ABSENT"));

        // the correction changes the numbers
        summary(A1, "ADMIN", null).andExpect(jsonPath("$[1].unmarked").value(0)).andExpect(jsonPath("$[1].absent").value(2));

        // history: admin yes, a doctor who never taught the group no
        mvc.perform(get("/api/attendance/records/" + recordId + "/changes").with(user(A1).roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/attendance/records/" + recordId + "/changes").with(user(D2).roles("DOCTOR")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RECORD_NOT_FOUND"));
    }

    @Test
    void correction_errors() throws Exception {
        correct(D1, openSession, e1, "ABSENT", "QR_FAILED")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_NOT_FINISHED"));
        correct(D1, s2Session, e1, "ABSENT", "QR_FAILED")                         // already ABSENT
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        correct(D1, s2Session, e1, "PRESENT", "I_FELT_LIKE_IT")
                .andExpect(status().isBadRequest());
        correct(D2, s2Session, e1, "PRESENT", "QR_FAILED")                         // d2 teaches the lab, not A
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_YOUR_GROUP"));
        correct(D1, s2Session, 999999, "PRESENT", "QR_FAILED")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_IN_GROUP"));
    }

    // ================================================================ home screens

    @Test
    void homeScreens_showAttendancePercent() throws Exception {
        String mine = mvc.perform(get("/api/me/enrollments").with(user(S1).roles("STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Object percent = JsonPath.read(mine, "$[?(@.offeringId == " + offering + ")].attendancePercent");
        org.junit.jupiter.api.Assertions.assertEquals("[66.7]", percent.toString());

        mvc.perform(get("/api/me/teaching-groups").with(user(D1).roles("DOCTOR")))
                .andExpect(jsonPath("$[?(@.group.groupId == " + lecture + ")].avgAttendancePercent", contains(50.0)));
    }

    // ================================================================ helpers

    private ResultActions summary(String who, String role, Long groupId) throws Exception {
        var request = get("/api/offerings/" + offering + "/attendance-summary").with(user(who).roles(role));
        if (groupId != null) {
            request = request.param("groupId", "" + groupId);
        }
        return mvc.perform(request);
    }

    private ResultActions correct(String doctor, long session, long enrollment, String status, String reason) throws Exception {
        return mvc.perform(post("/api/attendance/sessions/" + session + "/records/" + enrollment + "/correction")
                .with(user(doctor).roles("DOCTOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newStatus\":\"" + status + "\",\"reason\":\"" + reason + "\"}"));
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long group(long offering, String type, String label) {
        return id("INSERT INTO student_group (offering_id, group_type, label) VALUES (?, ?, ?) RETURNING group_id", offering, type, label);
    }

    private long newUser(String name, String email) {
        return id("INSERT INTO users (university_id, email, full_name, password_hash) VALUES (?, ?, ?, 'x') RETURNING user_id",
                name, email, name);
    }

    private long enroll(long student, long program) {
        return id("INSERT INTO enrollment (student_user_id, offering_id, program_id, status) VALUES (?, ?, ?, 'ACTIVE') RETURNING enrollment_id",
                student, offering, program);
    }

    private long session(long group, long assignment, LocalDateTime startsAt, String state) {
        LocalDateTime endsAt = "FINISHED".equals(state) ? startsAt.plusHours(2) : null;
        return id("INSERT INTO attendance_session (group_id, method, state, teaching_assignment_id, starts_at, ends_at, qr_window_ends_at) "
                        + "VALUES (?, 'INTERNET_QR', ?, ?, ?, ?, ?) RETURNING session_id",
                group, state, assignment, startsAt, endsAt, startsAt.plusMinutes(30));
    }

    private void record(long session, long enrollment, String status) {
        jdbc.update("INSERT INTO attendance_record (session_id, enrollment_id, status, method, marked_at) VALUES (?, ?, ?, 'QR', ?)",
                session, enrollment, status, LocalDateTime.now());
    }
}
