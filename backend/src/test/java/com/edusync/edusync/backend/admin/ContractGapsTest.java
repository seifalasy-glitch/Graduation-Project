package com.edusync.edusync.backend.admin;

import com.edusync.edusync.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The three gaps found by the contract check (27 Nov 2026):
 * Offering.enrolledCount, GET /admin/stats, and reset-password returning only temporaryPassword.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class ContractGapsTest {

    static final String ADMIN = "gap.admin@sci.asu.edu.eg";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mailSender;   // same mock as the warning tests -> one shared test context

    @Test
    void offerings_showActiveEnrollmentsOnly() throws Exception {
        long program = id("INSERT INTO academic_program (code, name, active) VALUES ('GAP', 'Gap', true) RETURNING program_id");
        long term = id("INSERT INTO academic_term (academic_year, term_type, start_date, end_date) VALUES ('2096/2097', 'FALL', NULL, NULL) RETURNING term_id");
        long course = id("INSERT INTO course (course_code, title, credit_hours) VALUES ('GAP101', 'Gap', 3) RETURNING course_id");
        long offering = id("INSERT INTO course_offering (course_id, term_id, section, capacity) VALUES (?, ?, '1', 30) RETURNING offering_id", course, term);
        enroll(offering, program, "ACTIVE");
        enroll(offering, program, "ACTIVE");
        enroll(offering, program, "WITHDRAWN");
        enroll(offering, program, "COMPLETED");

        mvc.perform(get("/api/offerings").param("termId", "" + term).with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enrolledCount").value(2));
        mvc.perform(get("/api/offerings/" + offering).with(user(ADMIN).roles("ADMIN")))
                .andExpect(jsonPath("$.enrolledCount").value(2));
    }

    @Test
    void adminStats_countsWhatTheContractSays() throws Exception {
        Map<String, Object> before = stats();

        long student = newUser("gap.s1", "ACTIVE");
        role(student, "STUDENT");
        role(newUser("gap.s2", "DISABLED"), "STUDENT");       // disabled: not counted
        role(newUser("gap.d1", "ACTIVE"), "DOCTOR");
        jdbc.update("INSERT INTO room (room_code, name) VALUES ('GAP-R1', 'Gap room')");
        long term = id("INSERT INTO academic_term (academic_year, term_type, start_date, end_date) VALUES ('2097/2098', 'SPRING', ?, ?) RETURNING term_id",
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(5));
        long course = id("INSERT INTO course (course_code, title, credit_hours) VALUES ('GAP201', 'Gap', 3) RETURNING course_id");
        jdbc.update("INSERT INTO course_offering (course_id, term_id, section) VALUES (?, ?, '1')", course, term);
        jdbc.update("INSERT INTO course_offering (course_id, term_id, section) VALUES (?, ?, '2')", course, term);
        jdbc.update("INSERT INTO registration_request (student_user_id, term_id, status) VALUES (?, ?, 'SUBMITTED')", student, term);
        long other = newUser("gap.s3", "DISABLED");            // one request per student and term
        jdbc.update("INSERT INTO registration_request (student_user_id, term_id, status) VALUES (?, ?, 'DRAFT')", other, term);

        Map<String, Object> after = stats();
        assertEquals(2, delta(before, after, "activeUsers"));          // s1 + d1
        assertEquals(1, delta(before, after, "students"));
        assertEquals(1, delta(before, after, "doctors"));
        assertEquals(1, delta(before, after, "rooms"));
        assertEquals(1, delta(before, after, "pendingRegistrations")); // DRAFT is not waiting
        assertEquals("Spring 2097/2098", after.get("currentTermLabel"));
        assertEquals(2, ((Number) after.get("currentTermOfferings")).intValue());
    }

    @Test
    void adminStats_isForAdminsOnly() throws Exception {
        mvc.perform(get("/api/admin/stats").with(user("gap.doc@sci.asu.edu.eg").roles("DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetPassword_returnsOnlyTheTemporaryPassword() throws Exception {
        long target = newUser("gap.reset", "ACTIVE");
        mvc.perform(post("/api/admin/users/" + target + "/reset-password").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$", not(hasKey("userId"))));
    }

    // ---------------- helpers ----------------

    private Map<String, Object> stats() throws Exception {
        String json = mvc.perform(get("/api/admin/stats").with(user(ADMIN).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$");
    }

    private static long delta(Map<String, Object> before, Map<String, Object> after, String key) {
        return ((Number) after.get(key)).longValue() - ((Number) before.get(key)).longValue();
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long newUser(String name, String status) {
        return id("INSERT INTO users (university_id, email, full_name, password_hash, status) VALUES (?, ?, ?, 'x', ?) RETURNING user_id",
                name, name + "@sci.asu.edu.eg", name, status);
    }

    private void role(long user, String role) {
        jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", user, role);
    }

    private void enroll(long offering, long program, String status) {
        long student = newUser("gap.e" + System.nanoTime(), "ACTIVE");
        jdbc.update("INSERT INTO enrollment (student_user_id, offering_id, program_id, status) VALUES (?, ?, ?, ?)",
                student, offering, program, status);
    }
}
