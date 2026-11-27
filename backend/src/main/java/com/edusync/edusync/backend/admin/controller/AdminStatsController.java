package com.edusync.edusync.backend.admin.controller;

import com.edusync.edusync.backend.academic.entity.AcademicTerm;
import com.edusync.edusync.backend.academic.service.CurrentTermService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * GET /admin/stats: numbers for the admin home page (contract AdminStats).
 *
 *  - activeUsers ............ users with status ACTIVE
 *  - students/doctors/admins  ACTIVE users with that role (a user with two roles counts in both)
 *  - currentTermLabel ....... e.g. "Fall 2026/2027"; null between terms (CurrentTermService)
 *  - currentTermOfferings ... offerings of the current term (0 between terms)
 *  - rooms .................. all rooms
 *  - pendingRegistrations ... requests still waiting for a decision (SUBMITTED or ADVISOR_APPROVED)
 *  - openTickets ............ tickets OPEN or IN_PROGRESS (0 until the tickets module is used)
 */
@RestController
@RequestMapping("/api/admin/stats")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    public record AdminStats(long activeUsers, long students, long doctors, long admins, String currentTermLabel,
                             long currentTermOfferings, long rooms, long pendingRegistrations, long openTickets) {
    }

    private final JdbcTemplate jdbc;
    private final CurrentTermService currentTerm;

    public AdminStatsController(JdbcTemplate jdbc, CurrentTermService currentTerm) {
        this.jdbc = jdbc;
        this.currentTerm = currentTerm;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<AdminStats> stats() {
        Optional<AcademicTerm> term = currentTerm.current();
        return ResponseEntity.ok(new AdminStats(
                count("SELECT COUNT(*) FROM users WHERE status = 'ACTIVE'"),
                activeWithRole("STUDENT"),
                activeWithRole("DOCTOR"),
                activeWithRole("ADMIN"),
                term.map(t -> CurrentTermService.label(t.getTermType(), t.getAcademicYear())).orElse(null),
                term.map(t -> count("SELECT COUNT(*) FROM course_offering WHERE term_id = ?", t.getTermId())).orElse(0L),
                count("SELECT COUNT(*) FROM room"),
                count("SELECT COUNT(*) FROM registration_request WHERE status IN ('SUBMITTED', 'ADVISOR_APPROVED')"),
                count("SELECT COUNT(*) FROM ticket WHERE status IN ('OPEN', 'IN_PROGRESS')")));
    }

    private long activeWithRole(String role) {
        return count("""
                SELECT COUNT(DISTINCT u.user_id) FROM users u JOIN user_role r ON r.user_id = u.user_id
                WHERE u.status = 'ACTIVE' AND r.role = ?
                """, role);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
