package com.edusync.edusync.backend.dashboard.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only SQL for the home screens. Plain SQL because each screen joins 4-5 tables;
 * every list is loaded with a fixed number of queries (no query per row).
 *
 * "Active in the window [from, to]" means: started on or before `to`, and not ended
 * by `from` (ends_at is NULL or after `from`). For "now", from = to = now.
 */
@Repository
public class DashboardQueries {

    // Enrollments counted / shown: ACTIVE and COMPLETED. WITHDRAWN is hidden (rule approved 27 Nov 2026).

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record EnrollmentRow(long enrollmentId, long offeringId, String courseCode, String courseTitle,
                                String termType, String academicYear, String programName) {
    }

    public record GroupRow(long ownerId, long groupId, long offeringId, String groupType, String label,
                           String dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    public record TeachingRow(long assignmentId, GroupRow group, String courseCode, String courseTitle,
                              String termType, String academicYear, Integer capacity, int enrolledCount) {
    }

    public List<EnrollmentRow> studentEnrollments(long studentId, long termId) {
        return jdbc.query("""
                SELECT e.enrollment_id, e.offering_id, c.course_code, c.title,
                       t.term_type, t.academic_year, p.name AS program_name
                FROM enrollment e
                JOIN course_offering o ON o.offering_id = e.offering_id
                JOIN course c          ON c.course_id   = o.course_id
                JOIN academic_term t   ON t.term_id     = o.term_id
                LEFT JOIN academic_program p ON p.program_id = e.program_id
                WHERE e.student_user_id = :studentId
                  AND o.term_id = :termId
                  AND e.status IN ('ACTIVE', 'COMPLETED')
                ORDER BY c.course_code, o.section
                """,
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("termId", termId),
                (rs, i) -> new EnrollmentRow(
                        rs.getLong("enrollment_id"), rs.getLong("offering_id"),
                        rs.getString("course_code"), rs.getString("title"),
                        rs.getString("term_type"), rs.getString("academic_year"),
                        rs.getString("program_name")));
    }

    /** offeringId -> names of doctors teaching any of its groups in the window (A-Z, no repeats). */
    public Map<Long, List<String>> doctorNames(Collection<Long> offeringIds, LocalDateTime from, LocalDateTime to) {
        Map<Long, List<String>> result = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT g.offering_id, u.full_name
                FROM teaching_assignment a
                JOIN student_group g ON g.group_id = a.group_id
                JOIN users u         ON u.user_id  = a.doctor_user_id
                WHERE g.offering_id IN (:ids)
                  AND a.starts_at <= :to AND (a.ends_at IS NULL OR a.ends_at > :from)
                ORDER BY u.full_name
                """,
                window(from, to).addValue("ids", offeringIds),
                rs -> {
                    result.computeIfAbsent(rs.getLong("offering_id"), k -> new ArrayList<>())
                            .add(rs.getString("full_name"));
                });
        return result;
    }

    /** The student's groups per enrollment in the window. GroupRow.ownerId = enrollmentId. */
    public List<GroupRow> groupsOfEnrollments(Collection<Long> enrollmentIds, LocalDateTime from, LocalDateTime to) {
        return jdbc.query("""
                SELECT DISTINCT m.enrollment_id AS owner_id, g.group_id, g.offering_id, g.group_type, g.label,
                       g.day_of_week, g.start_time, g.end_time
                FROM group_membership m
                JOIN student_group g ON g.group_id = m.group_id
                WHERE m.enrollment_id IN (:ids)
                  AND m.starts_at <= :to AND (m.ends_at IS NULL OR m.ends_at > :from)
                ORDER BY g.group_type, g.label
                """,
                window(from, to).addValue("ids", enrollmentIds),
                (rs, i) -> groupRow(rs));
    }

    /**
     * groupId -> students who were members in the window. A WITHDRAWN student is not counted
     * (withdrawing ends memberships, see EnrollmentService.update; this filter is a safety net).
     */
    public Map<Long, Integer> memberCounts(Collection<Long> groupIds, LocalDateTime from, LocalDateTime to) {
        Map<Long, Integer> result = new HashMap<>();
        jdbc.query("""
                SELECT m.group_id, COUNT(DISTINCT m.enrollment_id) AS members
                FROM group_membership m
                JOIN enrollment e ON e.enrollment_id = m.enrollment_id
                WHERE m.group_id IN (:ids)
                  AND e.status IN ('ACTIVE', 'COMPLETED')
                  AND m.starts_at <= :to AND (m.ends_at IS NULL OR m.ends_at > :from)
                GROUP BY m.group_id
                """,
                window(from, to).addValue("ids", groupIds),
                rs -> {
                    result.put(rs.getLong("group_id"), rs.getInt("members"));
                });
        return result;
    }

    /** The doctor's teaching assignments in the term, active in the window. GroupRow.ownerId = assignmentId. */
    public List<TeachingRow> teachingGroups(long doctorId, long termId, LocalDateTime from, LocalDateTime to) {
        return jdbc.query("""
                SELECT a.assignment_id AS owner_id, g.group_id, g.offering_id, g.group_type, g.label,
                       g.day_of_week, g.start_time, g.end_time,
                       c.course_code, c.title, t.term_type, t.academic_year, o.capacity,
                       (SELECT COUNT(*) FROM enrollment e
                        WHERE e.offering_id = o.offering_id AND e.status IN ('ACTIVE', 'COMPLETED')) AS enrolled
                FROM teaching_assignment a
                JOIN student_group g   ON g.group_id    = a.group_id
                JOIN course_offering o ON o.offering_id = g.offering_id
                JOIN course c          ON c.course_id   = o.course_id
                JOIN academic_term t   ON t.term_id     = o.term_id
                WHERE a.doctor_user_id = :doctorId
                  AND o.term_id = :termId
                  AND a.starts_at <= :to AND (a.ends_at IS NULL OR a.ends_at > :from)
                ORDER BY c.course_code, g.group_type, g.label
                """,
                window(from, to).addValue("doctorId", doctorId).addValue("termId", termId),
                (rs, i) -> new TeachingRow(
                        rs.getLong("owner_id"), groupRow(rs),
                        rs.getString("course_code"), rs.getString("title"),
                        rs.getString("term_type"), rs.getString("academic_year"),
                        (Integer) rs.getObject("capacity"), rs.getInt("enrolled")));
    }

    private static MapSqlParameterSource window(LocalDateTime from, LocalDateTime to) {
        return new MapSqlParameterSource().addValue("from", from).addValue("to", to);
    }

    private static GroupRow groupRow(ResultSet rs) throws SQLException {
        return new GroupRow(
                rs.getLong("owner_id"), rs.getLong("group_id"), rs.getLong("offering_id"),
                rs.getString("group_type"), rs.getString("label"), rs.getString("day_of_week"),
                toLocalTime(rs.getTime("start_time")), toLocalTime(rs.getTime("end_time")));
    }

    private static LocalTime toLocalTime(Time time) {
        return time == null ? null : time.toLocalTime();
    }
}
