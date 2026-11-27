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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Attendance phase A end to end: real controller, security and SQL on the
 * Testcontainers database. Each test is rolled back.
 *
 * setUp(): one lecture group "A" with students s1 and s2 (member since last week),
 * s3 enrolled but in another group, doctor d1 teaching group A now, doctor d2 teaching
 * nothing, admin a1. Room R1 with subnet 10.20.5.0/24.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class AttendanceFlowTest {

    static final String D1 = "att.d1@sci.asu.edu.eg", D2 = "att.d2@sci.asu.edu.eg", A1 = "att.a1@sci.asu.edu.eg";
    static final String S1 = "att.s1@sci.asu.edu.eg", S2 = "att.s2@sci.asu.edu.eg", S3 = "att.s3@sci.asu.edu.eg";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    long group;
    long room;
    long e1, e2;

    @BeforeEach
    void setUp() {
        LocalDateTime lastWeek = LocalDateTime.now().minusDays(7);
        long program = id("INSERT INTO academic_program (code, name, active) VALUES ('ATT', 'Att', true) RETURNING program_id");
        long term = id("INSERT INTO academic_term (academic_year, term_type, start_date, end_date) "
                + "VALUES ('2092/2093', 'FALL', CURRENT_DATE - 30, CURRENT_DATE + 30) RETURNING term_id");
        long course = id("INSERT INTO course (course_code, title, credit_hours) VALUES ('ATT101', 'Attendance 101', 3) RETURNING course_id");
        long offering = id("INSERT INTO course_offering (course_id, term_id, section) VALUES (?, ?, '1') RETURNING offering_id", course, term);
        group = id("INSERT INTO student_group (offering_id, group_type, label) VALUES (?, 'LECTURE', 'A') RETURNING group_id", offering);
        long otherGroup = id("INSERT INTO student_group (offering_id, group_type, label) VALUES (?, 'LECTURE', 'B') RETURNING group_id", offering);
        room = id("INSERT INTO room (room_code, name, subnet) VALUES ('R1', 'Room 1', '10.20.5.0/24') RETURNING room_id");

        long d1 = newUser("att.d1", D1);
        newUser("att.d2", D2);
        newUser("att.a1", A1);
        e1 = enroll(newUser("att.s1", S1), offering, program);
        e2 = enroll(newUser("att.s2", S2), offering, program);
        long e3 = enroll(newUser("att.s3", S3), offering, program);

        jdbc.update("INSERT INTO group_membership (enrollment_id, group_id, starts_at) VALUES (?, ?, ?)", e1, group, lastWeek);
        jdbc.update("INSERT INTO group_membership (enrollment_id, group_id, starts_at) VALUES (?, ?, ?)", e2, group, lastWeek);
        jdbc.update("INSERT INTO group_membership (enrollment_id, group_id, starts_at) VALUES (?, ?, ?)", e3, otherGroup, lastWeek);
        jdbc.update("INSERT INTO teaching_assignment (group_id, doctor_user_id, starts_at) VALUES (?, ?, ?)", group, d1, lastWeek);
    }

    // ================================================================ open

    @Test
    void open_qrSession_thenSecondOpenIsRefused() throws Exception {
        open(D1, "{\"groupId\":" + group + ",\"method\":\"INTERNET_QR\",\"qrWindowMinutes\":10}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("QR_OPEN"))
                .andExpect(jsonPath("$.method").value("INTERNET_QR"))
                .andExpect(jsonPath("$.qrWindowEndsAt").isNotEmpty())
                .andExpect(jsonPath("$.unmarkedCount").value(2));

        open(D1, "{\"groupId\":" + group + ",\"method\":\"MANUAL\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_OPEN"));
    }

    @Test
    void open_byDoctorNotTeachingTheGroup_isForbidden() throws Exception {
        open(D2, "{\"groupId\":" + group + ",\"method\":\"MANUAL\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_YOUR_GROUP"));
    }

    @Test
    void open_badRequests_areValidationErrors() throws Exception {
        open(D1, "{\"groupId\":" + group + ",\"method\":\"INTERNET_QR\"}")                       // no window
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        open(D1, "{\"groupId\":" + group + ",\"method\":\"MANUAL\",\"qrWindowMinutes\":5}")      // window on MANUAL
                .andExpect(status().isBadRequest());
        open(D1, "{\"groupId\":" + group + ",\"method\":\"LOCAL_WIFI_QR\",\"qrWindowMinutes\":5}") // no room
                .andExpect(status().isBadRequest());
        open(D1, "{\"groupId\":" + group + ",\"method\":\"LOCAL_QR\",\"qrWindowMinutes\":5}")      // old name
                .andExpect(status().isBadRequest());
    }

    // ================================================================ QR + check-in

    @Test
    void checkIn_present_thenAlreadyMarked_andOtherErrors() throws Exception {
        long session = openQr("INTERNET_QR", null, null);
        String token = qr(session);

        checkIn(S1, token, "1.2.3.4")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.courseCode").value("ATT101"));

        checkIn(S1, token, "1.2.3.4")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MARKED"));
        checkIn(S3, token, "1.2.3.4")                                  // enrolled, but in group B
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_IN_GROUP"));
        checkIn(S2, "made-up-token", "1.2.3.4")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QR_EXPIRED"));
    }

    @Test
    void checkIn_afterLateMinutes_isLate() throws Exception {
        long session = openQr("INTERNET_QR", null, 0);                 // late right after the start
        checkIn(S1, qr(session), "1.2.3.4").andExpect(jsonPath("$.status").value("LATE"));
    }

    @Test
    void checkIn_expiredCode_isRefused() throws Exception {
        long session = openQr("INTERNET_QR", null, null);
        String token = qr(session);
        jdbc.update("UPDATE qr_challenge SET expires_at = expires_at - INTERVAL '1 minute' WHERE session_id = ?", session);

        checkIn(S1, token, "1.2.3.4").andExpect(jsonPath("$.code").value("QR_EXPIRED"));
    }

    @Test
    void localWifiQr_checksTheRoomSubnet() throws Exception {
        long session = openQr("LOCAL_WIFI_QR", room, null);
        String token = qr(session);

        checkIn(S2, token, "192.168.1.5")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NETWORK_NOT_ALLOWED"));
        checkIn(S1, token, "10.20.5.7")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRESENT"));
    }

    @Test
    void afterQrWindow_sessionIsManualOnly_andScanningIsClosed() throws Exception {
        long session = openQr("INTERNET_QR", null, null);
        String token = qr(session);
        jdbc.update("UPDATE attendance_session SET qr_window_ends_at = LOCALTIMESTAMP - INTERVAL '1 second' WHERE session_id = ?", session);

        mvc.perform(get("/api/attendance/sessions/" + session).with(user(D1).roles("DOCTOR")))
                .andExpect(jsonPath("$.state").value("MANUAL_ONLY"));
        checkIn(S1, token, "1.2.3.4").andExpect(jsonPath("$.code").value("WINDOW_CLOSED"));
        mvc.perform(get("/api/attendance/sessions/" + session + "/qr").with(user(D1).roles("DOCTOR")))
                .andExpect(jsonPath("$.code").value("WINDOW_CLOSED"));
    }

    @Test
    void qr_onManualSession_isRefused() throws Exception {
        long session = sessionId(open(D1, "{\"groupId\":" + group + ",\"method\":\"MANUAL\"}"));
        mvc.perform(get("/api/attendance/sessions/" + session + "/qr").with(user(D1).roles("DOCTOR")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_A_QR_SESSION"));
    }

    // ================================================================ roster, manual marks, finish

    @Test
    void roster_manualMarks_oneRecordPerStudent_andFinish() throws Exception {
        long session = openQr("INTERNET_QR", null, null);
        checkIn(S1, qr(session), "1.2.3.4");

        mvc.perform(get("/api/attendance/sessions/" + session + "/roster").with(user(D1).roles("DOCTOR")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].student.universityId").value("att.s1"))
                .andExpect(jsonPath("$[0].status").value("PRESENT"))
                .andExpect(jsonPath("$[0].method").value("QR"))
                .andExpect(jsonPath("$[1].status").value(nullValue()));      // s2 unmarked

        // The doctor overrides s1's QR mark: still ONE record, now MANUAL
        mark(session, e1, "ABSENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABSENT"))
                .andExpect(jsonPath("$.method").value("MANUAL"));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance_record WHERE session_id = ? AND enrollment_id = ?", Integer.class, session, e1));

        // Finish and mark the unmarked (s2) absent
        mvc.perform(post("/api/attendance/sessions/" + session + "/finish").with(user(D1).roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"markUnmarkedAsAbsent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.absentMarkedCount").value(1))
                .andExpect(jsonPath("$.session.state").value("FINISHED"))
                .andExpect(jsonPath("$.session.endsAt").isNotEmpty())
                .andExpect(jsonPath("$.session.absentCount").value(2))
                .andExpect(jsonPath("$.session.unmarkedCount").value(0));

        mark(session, e2, "PRESENT")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION_FINISHED"));
        mvc.perform(post("/api/attendance/sessions/" + session + "/finish").with(user(D1).roles("DOCTOR")))
                .andExpect(jsonPath("$.code").value("SESSION_FINISHED"));

        // After finishing, a new session for the group can be opened
        open(D1, "{\"groupId\":" + group + ",\"method\":\"MANUAL\"}").andExpect(status().isCreated());
    }

    @Test
    void finish_withoutBody_leavesUnmarkedAsUnmarked() throws Exception {
        long session = sessionId(open(D1, "{\"groupId\":" + group + ",\"method\":\"MANUAL\"}"));
        mvc.perform(post("/api/attendance/sessions/" + session + "/finish").with(user(D1).roles("DOCTOR")))
                .andExpect(jsonPath("$.absentMarkedCount").value(0))
                .andExpect(jsonPath("$.session.unmarkedCount").value(2));
    }

    @Test
    void mark_studentNotInGroup_andBadStatus() throws Exception {
        long session = sessionId(open(D1, "{\"groupId\":" + group + ",\"method\":\"MANUAL\"}"));
        mark(session, 999999, "PRESENT")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_IN_GROUP"));
        mark(session, e1, "SICK").andExpect(status().isBadRequest());
    }

    // ================================================================ who can read

    @Test
    void reading_adminYes_otherDoctorNo_studentNo() throws Exception {
        long session = sessionId(open(D1, "{\"groupId\":" + group + ",\"method\":\"MANUAL\"}"));

        mvc.perform(get("/api/attendance/sessions/" + session).with(user(A1).roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/groups/" + group + "/attendance-sessions").with(user(A1).roles("ADMIN")))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/attendance/sessions/" + session).with(user(D2).roles("DOCTOR")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        mvc.perform(get("/api/attendance/sessions/" + session + "/roster").with(user(S1).roles("STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/attendance/check-in").with(user(D1).roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    // ================================================================ helpers

    private ResultActions open(String doctor, String body) throws Exception {
        return mvc.perform(post("/api/attendance/sessions").with(user(doctor).roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long openQr(String method, Long roomId, Integer lateAfter) throws Exception {
        String body = "{\"groupId\":" + group + ",\"method\":\"" + method + "\",\"qrWindowMinutes\":10"
                + (roomId == null ? "" : ",\"roomId\":" + roomId)
                + (lateAfter == null ? "" : ",\"lateAfterMinutes\":" + lateAfter) + "}";
        return sessionId(open(D1, body).andExpect(status().isCreated()));
    }

    private long sessionId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.sessionId")).longValue();
    }

    private String qr(long session) throws Exception {
        String json = mvc.perform(get("/api/attendance/sessions/" + session + "/qr").with(user(D1).roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    private ResultActions checkIn(String student, String token, String ip) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/attendance/check-in").with(user(student).roles("STUDENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"" + token + "\"}")
                .with(r -> { r.setRemoteAddr(ip); return r; });
        return mvc.perform(request);
    }

    private ResultActions mark(long session, long enrollment, String status) throws Exception {
        return mvc.perform(put("/api/attendance/sessions/" + session + "/records/" + enrollment)
                .with(user(D1).roles("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long newUser(String name, String email) {
        return id("INSERT INTO users (university_id, email, full_name, password_hash) VALUES (?, ?, ?, 'x') RETURNING user_id",
                name, email, name);
    }

    private long enroll(long student, long offering, long program) {
        return id("INSERT INTO enrollment (student_user_id, offering_id, program_id, status) VALUES (?, ?, ?, 'ACTIVE') RETURNING enrollment_id",
                student, offering, program);
    }
}
