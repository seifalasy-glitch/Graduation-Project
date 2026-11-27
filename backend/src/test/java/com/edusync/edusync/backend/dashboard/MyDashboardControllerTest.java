package com.edusync.edusync.backend.dashboard;

import com.edusync.edusync.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /me/enrollments and GET /me/teaching-groups through the real controller and
 * security, on the throwaway Testcontainers database. Each test is rolled back.
 *
 * Data built in setUp():
 *   current term (today inside it) + an old term (last year).
 *   ALG101 in the current term: lecture L1 (Sunday 10:00-12:00), labs B1 and B2.
 *     s1 (ACTIVE): in L1 now, left B1 yesterday, in B2 now.
 *     s2 (WITHDRAWN): hidden from "my courses" and not counted as enrolled.
 *     d1 teaches L1 now; d2 taught B1 but stopped yesterday.
 *   DROP102 in the current term: s1 WITHDRAWN -> hidden.
 *   OLD100 in the old term: s1 COMPLETED, was in lecture O1 during that term.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class MyDashboardControllerTest {

    private static final String S1 = "dash.s1@sci.asu.edu.eg";
    private static final String D1 = "dash.d1@sci.asu.edu.eg";
    private static final String D2 = "dash.d2@sci.asu.edu.eg";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    long oldTerm;
    long l1;

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime lastMonth = now.minusMonths(1);

        long program = id("INSERT INTO academic_program (code, name, active) VALUES ('DASH', 'Dash Program', true) RETURNING program_id");
        long current = term("2090/2091", "FALL", today.minusDays(30), today.plusDays(30));
        oldTerm = term("2089/2090", "FALL", today.minusYears(1).minusDays(30), today.minusYears(1).plusDays(30));

        long s1 = user("dash.s1", S1);
        long s2 = user("dash.s2", "dash.s2@sci.asu.edu.eg");
        long d1 = user("dash.d1", D1);
        long d2 = user("dash.d2", D2);

        // ALG101, current term
        long alg = offering(course("ALG101", "Algorithms"), current, 30);
        l1 = group(alg, "LECTURE", "L1", "SUNDAY", "10:00", "12:00");
        long b1 = group(alg, "LAB", "B1", "MONDAY", "08:00", "10:00");
        long b2 = group(alg, "LAB", "B2", "TUESDAY", "08:00", "10:00");
        long e1 = enrollment(s1, alg, program, "ACTIVE");
        long e2 = enrollment(s2, alg, program, "WITHDRAWN");
        membership(e1, l1, lastMonth, null);
        membership(e1, b1, lastMonth, yesterday);        // left B1 yesterday
        membership(e1, b2, yesterday, null);
        membership(e2, l1, lastMonth, null);             // withdrawn, but membership still open
        teaching(l1, d1, lastMonth, null);
        teaching(b1, d2, lastMonth, yesterday);          // d2 stopped yesterday

        // DROP102, current term: withdrawn
        long drop = offering(course("DROP102", "Dropped"), current, null);
        enrollment(s1, drop, program, "WITHDRAWN");

        // OLD100, last year's term: completed
        long old = offering(course("OLD100", "Old Course"), oldTerm, null);
        long o1 = group(old, "LECTURE", "O1", "SUNDAY", "08:00", "10:00");
        long eOld = enrollment(s1, old, program, "COMPLETED");
        LocalDateTime oldStart = today.minusYears(1).minusDays(30).atStartOfDay();
        membership(eOld, o1, oldStart, oldStart.plusDays(60));
        teaching(o1, d1, oldStart, oldStart.plusDays(60));
    }

    // ------------------------------------------------------------ student

    @Test
    @WithMockUser(username = S1, roles = "STUDENT")
    void student_seesCurrentTermCourses_withCurrentGroupsAndDoctors() throws Exception {
        mvc.perform(get("/api/me/enrollments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))                          // DROP102 (withdrawn) hidden
                .andExpect(jsonPath("$[0].courseCode").value("ALG101"))
                .andExpect(jsonPath("$[0].courseTitle").value("Algorithms"))
                .andExpect(jsonPath("$[0].termLabel").value("Fall 2090/2091"))
                .andExpect(jsonPath("$[0].programName").value("Dash Program"))
                .andExpect(jsonPath("$[0].doctorNames", contains("dash.d1")))   // d2 stopped yesterday
                .andExpect(jsonPath("$[0].attendancePercent").value(nullValue()))
                .andExpect(jsonPath("$[0].groups[*].label", contains("B2", "L1"))) // left B1 yesterday
                .andExpect(jsonPath("$[0].groups[1].dayOfWeek").value("SUNDAY"))
                .andExpect(jsonPath("$[0].groups[1].startTime").value("10:00"))
                .andExpect(jsonPath("$[0].groups[1].memberCount").value(1));     // s2 withdrawn: not counted
    }

    @Test
    @WithMockUser(username = S1, roles = "STUDENT")
    void student_oldTerm_showsCompletedCourse_withGroupsOfThatTerm() throws Exception {
        mvc.perform(get("/api/me/enrollments").param("termId", String.valueOf(oldTerm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].courseCode").value("OLD100"))
                .andExpect(jsonPath("$[0].groups[*].label", contains("O1")))
                .andExpect(jsonPath("$[0].doctorNames", contains("dash.d1")));
    }

    @Test
    @WithMockUser(username = S1, roles = "STUDENT")
    void student_unknownTerm_isEmptyList() throws Exception {
        mvc.perform(get("/api/me/enrollments").param("termId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = S1, roles = "STUDENT")
    void noCurrentTerm_isEmptyList() throws Exception {
        // Move every term out of today -> we are "between terms"
        jdbc.update("UPDATE academic_term SET start_date = start_date - 1000, end_date = end_date - 1000");

        mvc.perform(get("/api/me/enrollments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = D1, roles = "DOCTOR")
    void doctor_cannotCallStudentEndpoint() throws Exception {
        mvc.perform(get("/api/me/enrollments"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------ doctor

    @Test
    @WithMockUser(username = D1, roles = "DOCTOR")
    void doctor_seesGroupsTaughtNow() throws Exception {
        mvc.perform(get("/api/me/teaching-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].group.groupId").value(l1))
                .andExpect(jsonPath("$[0].group.label").value("L1"))
                .andExpect(jsonPath("$[0].group.memberCount").value(1))         // s2 withdrawn: not counted
                .andExpect(jsonPath("$[0].courseCode").value("ALG101"))
                .andExpect(jsonPath("$[0].termLabel").value("Fall 2090/2091"))
                .andExpect(jsonPath("$[0].enrolledCount").value(1))           // s2 is withdrawn
                .andExpect(jsonPath("$[0].capacity").value(30))
                .andExpect(jsonPath("$[0].avgAttendancePercent").value(nullValue()));
    }

    @Test
    @WithMockUser(username = D2, roles = "DOCTOR")
    void doctor_whoStoppedTeaching_seesNothingThisTerm() throws Exception {
        mvc.perform(get("/api/me/teaching-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = D1, roles = "DOCTOR")
    void doctor_oldTerm_seesGroupsTaughtThen() throws Exception {
        mvc.perform(get("/api/me/teaching-groups").param("termId", String.valueOf(oldTerm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].courseCode").value("OLD100"))
                .andExpect(jsonPath("$[0].enrolledCount").value(1));         // COMPLETED counts
    }

    @Test
    @WithMockUser(username = S1, roles = "STUDENT")
    void student_cannotCallDoctorEndpoint() throws Exception {
        mvc.perform(get("/api/me/teaching-groups"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ helpers

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long term(String year, String type, LocalDate start, LocalDate end) {
        return id("INSERT INTO academic_term (academic_year, term_type, start_date, end_date) VALUES (?, ?, ?, ?) RETURNING term_id",
                year, type, start, end);
    }

    private long user(String name, String email) {
        return id("INSERT INTO users (university_id, email, full_name, password_hash) VALUES (?, ?, ?, 'x') RETURNING user_id",
                name, email, name);
    }

    private long course(String code, String title) {
        return id("INSERT INTO course (course_code, title, credit_hours) VALUES (?, ?, 3) RETURNING course_id", code, title);
    }

    private long offering(long course, long term, Integer capacity) {
        return id("INSERT INTO course_offering (course_id, term_id, section, capacity) VALUES (?, ?, '1', ?) RETURNING offering_id",
                course, term, capacity);
    }

    private long group(long offering, String type, String label, String day, String start, String end) {
        return id("INSERT INTO student_group (offering_id, group_type, label, day_of_week, start_time, end_time) "
                        + "VALUES (?, ?, ?, ?, ?::time, ?::time) RETURNING group_id",
                offering, type, label, day, start, end);
    }

    private long enrollment(long student, long offering, long program, String status) {
        return id("INSERT INTO enrollment (student_user_id, offering_id, program_id, status) VALUES (?, ?, ?, ?) RETURNING enrollment_id",
                student, offering, program, status);
    }

    private void membership(long enrollment, long group, LocalDateTime from, LocalDateTime to) {
        jdbc.update("INSERT INTO group_membership (enrollment_id, group_id, starts_at, ends_at) VALUES (?, ?, ?, ?)",
                enrollment, group, from, to);
    }

    private void teaching(long group, long doctor, LocalDateTime from, LocalDateTime to) {
        jdbc.update("INSERT INTO teaching_assignment (group_id, doctor_user_id, starts_at, ends_at) VALUES (?, ?, ?, ?)",
                group, doctor, from, to);
    }
}
