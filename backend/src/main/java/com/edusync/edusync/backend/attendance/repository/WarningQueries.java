package com.edusync.edusync.backend.attendance.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SQL for attendance warnings (phase C).
 *
 * Absences of a student for one group type of a course (rules approved 27 Nov 2026):
 *   absenceCount = ABSENT marks + unmarked slots + one for every 2 LATE marks,
 *   over FINISHED sessions only, for students who are not WITHDRAWN.
 * Week number of a session = (session date - term start_date) / 7 + 1.
 */
@Repository
public class WarningQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public WarningQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RuleRow(long ruleId, long offeringId, String groupType, boolean enabled, Integer absenceLimit,
                          LocalDateTime updatedAt, Long updatedBy) {
    }

    /** Current absences of one student for one group type. */
    public record Absences(long enrollmentId, int absent, int late, List<Integer> absentWeeks) {
        public int absenceCount() {
            return absent + late / 2;   // every 2 LATE = 1 absence
        }
    }

    public record WarningRow(long warningId, long enrollmentId, long offeringId, String groupType, Integer absenceLimit,
                             LocalDateTime issuedAt, LocalDateTime emailedAt, long userId, String universityId,
                             String fullName, String fullNameAr, String programName, String courseCode) {
    }

    public record GroupInfo(String label, String dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }

    public record CourseInfo(String courseCode, String title, String termType, String academicYear) {
    }

    // ================================================================ rules

    public List<String> groupTypesOfOffering(long offeringId) {
        return jdbc.queryForList(
                "SELECT DISTINCT group_type FROM student_group WHERE offering_id = :o ORDER BY group_type",
                Map.of("o", offeringId), String.class);
    }

    public List<RuleRow> rules(long offeringId) {
        return jdbc.query("SELECT * FROM attendance_warning_rule WHERE offering_id = :o ORDER BY group_type",
                Map.of("o", offeringId), (rs, i) -> rule(rs));
    }

    public Optional<RuleRow> rule(long offeringId, String groupType) {
        return jdbc.query("SELECT * FROM attendance_warning_rule WHERE offering_id = :o AND group_type = :t",
                Map.of("o", offeringId, "t", groupType), (rs, i) -> rule(rs)).stream().findFirst();
    }

    /** Insert or update the rule of (offering, group type); returns it. */
    public RuleRow saveRule(long offeringId, String groupType, boolean enabled, Integer absenceLimit,
                            long doctorId, LocalDateTime now) {
        return jdbc.query("""
                INSERT INTO attendance_warning_rule (offering_id, group_type, enabled, absence_limit, updated_at, updated_by)
                VALUES (:o, :t, :enabled, :limit, :now, :doctor)
                ON CONFLICT (offering_id, group_type)
                DO UPDATE SET enabled = EXCLUDED.enabled, absence_limit = EXCLUDED.absence_limit,
                              updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by
                RETURNING *
                """, new MapSqlParameterSource().addValue("o", offeringId).addValue("t", groupType)
                .addValue("enabled", enabled).addValue("limit", absenceLimit).addValue("now", now)
                .addValue("doctor", doctorId), (rs, i) -> rule(rs)).get(0);
    }

    // ================================================================ absences

    /**
     * enrollmentId -> current absences for (offering, group type). Only students with at
     * least one slot in a finished session appear. enrollmentIds = null means everyone.
     */
    public Map<Long, Absences> absences(long offeringId, String groupType, Collection<Long> enrollmentIds) {
        Map<Long, Absences> result = new HashMap<>();
        if (enrollmentIds != null && enrollmentIds.isEmpty()) {
            return result;
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("o", offeringId).addValue("t", groupType);
        String onlySome = "";
        if (enrollmentIds != null) {
            onlySome = " AND m.enrollment_id IN (:ids)";
            params.addValue("ids", enrollmentIds);
        }
        jdbc.query("SELECT m.enrollment_id,"
                        + " COUNT(*) FILTER (WHERE r.status = 'ABSENT' OR r.status IS NULL) AS absent,"
                        + " COUNT(*) FILTER (WHERE r.status = 'LATE') AS late,"
                        + " ARRAY_AGG(DISTINCT (FLOOR((s.starts_at::date - t.start_date) / 7.0) + 1)::int)"
                        + "   FILTER (WHERE (r.status = 'ABSENT' OR r.status IS NULL) AND t.start_date IS NOT NULL) AS weeks"
                        + " FROM attendance_session s"
                        + " JOIN student_group g    ON g.group_id = s.group_id"
                        + " JOIN course_offering o  ON o.offering_id = g.offering_id"
                        + " JOIN academic_term t    ON t.term_id = o.term_id"
                        + " JOIN group_membership m ON m.group_id = s.group_id"
                        + "   AND m.starts_at <= s.starts_at AND (m.ends_at IS NULL OR m.ends_at > s.starts_at)"
                        + " JOIN enrollment e       ON e.enrollment_id = m.enrollment_id AND e.status <> 'WITHDRAWN'"
                        + " LEFT JOIN attendance_record r ON r.session_id = s.session_id AND r.enrollment_id = m.enrollment_id"
                        + " WHERE s.state = 'FINISHED' AND g.offering_id = :o AND g.group_type = :t" + onlySome
                        + " GROUP BY m.enrollment_id",
                params,
                rs -> {
                    long id = rs.getLong("enrollment_id");
                    result.put(id, new Absences(id, rs.getInt("absent"), rs.getInt("late"), weeks(rs.getArray("weeks"))));
                });
        return result;
    }

    // ================================================================ warnings

    /** Creates the warning once per (enrollment, rule); returns its id, or empty if it already existed. */
    public Optional<Long> insertWarningOnce(long enrollmentId, long ruleId, int absenceCount, int lateCount,
                                            LocalDateTime now) {
        return jdbc.query("""
                INSERT INTO attendance_warning (enrollment_id, rule_id, absence_count, late_count, issued_at)
                VALUES (:e, :r, :abs, :late, :now)
                ON CONFLICT (enrollment_id, rule_id) DO NOTHING
                RETURNING warning_id
                """, new MapSqlParameterSource().addValue("e", enrollmentId).addValue("r", ruleId)
                .addValue("abs", absenceCount).addValue("late", lateCount).addValue("now", now),
                (rs, i) -> rs.getLong(1)).stream().findFirst();
    }

    public void markEmailed(long warningId, LocalDateTime when) {
        jdbc.update("UPDATE attendance_warning SET emailed_at = :when WHERE warning_id = :id",
                Map.of("id", warningId, "when", when));
    }

    private static final String WARNING_SELECT = """
            SELECT w.warning_id, w.enrollment_id, rl.offering_id, rl.group_type, rl.absence_limit,
                   w.issued_at, w.emailed_at, u.user_id, u.university_id, u.full_name, u.full_name_ar,
                   p.name AS program_name, c.course_code
            FROM attendance_warning w
            JOIN attendance_warning_rule rl ON rl.rule_id = w.rule_id
            JOIN enrollment e      ON e.enrollment_id = w.enrollment_id
            JOIN users u           ON u.user_id = e.student_user_id
            LEFT JOIN academic_program p ON p.program_id = e.program_id
            JOIN course_offering o ON o.offering_id = rl.offering_id
            JOIN course c          ON c.course_id = o.course_id
            """;

    /** Warnings of a course (optionally one group type), students not withdrawn. */
    public List<WarningRow> warningsOfOffering(long offeringId, String groupType) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("o", offeringId);
        String type = "";
        if (groupType != null) {
            type = " AND rl.group_type = :t";
            params.addValue("t", groupType);
        }
        return jdbc.query(WARNING_SELECT + " WHERE rl.offering_id = :o AND e.status <> 'WITHDRAWN'" + type,
                params, (rs, i) -> warning(rs));
    }

    public List<WarningRow> warningsOfStudent(long studentId) {
        return jdbc.query(WARNING_SELECT + " WHERE e.student_user_id = :s ORDER BY w.issued_at DESC, w.warning_id DESC",
                Map.of("s", studentId), (rs, i) -> warning(rs));
    }

    // ================================================================ small lookups

    /** [offeringId, groupType] of a session's group. */
    public Optional<Object[]> sessionCourseAndType(long sessionId) {
        return jdbc.query("""
                SELECT g.offering_id, g.group_type FROM attendance_session s
                JOIN student_group g ON g.group_id = s.group_id WHERE s.session_id = :id
                """, Map.of("id", sessionId), (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2)})
                .stream().findFirst();
    }

    /** The student's latest group of this type in the course (the one shown on the list). */
    public Map<Long, GroupInfo> latestGroups(Collection<Long> enrollmentIds, String groupType) {
        Map<Long, GroupInfo> result = new HashMap<>();
        if (enrollmentIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT DISTINCT ON (m.enrollment_id) m.enrollment_id, g.label, g.day_of_week, g.start_time, g.end_time
                FROM group_membership m JOIN student_group g ON g.group_id = m.group_id
                WHERE m.enrollment_id IN (:ids) AND g.group_type = :t
                ORDER BY m.enrollment_id, m.starts_at DESC
                """, new MapSqlParameterSource().addValue("ids", enrollmentIds).addValue("t", groupType),
                rs -> {
                    result.put(rs.getLong("enrollment_id"), new GroupInfo(rs.getString("label"),
                            rs.getString("day_of_week"), rs.getObject("start_time", LocalTime.class),
                            rs.getObject("end_time", LocalTime.class)));
                });
        return result;
    }

    public Optional<CourseInfo> course(long offeringId) {
        return jdbc.query("""
                SELECT c.course_code, c.title, t.term_type, t.academic_year
                FROM course_offering o JOIN course c ON c.course_id = o.course_id
                JOIN academic_term t ON t.term_id = o.term_id
                WHERE o.offering_id = :o
                """, Map.of("o", offeringId), (rs, i) -> new CourseInfo(rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4))).stream().findFirst();
    }

    /** Email address and full name of the student of an enrollment. */
    public Optional<String[]> studentContact(long enrollmentId) {
        return jdbc.query("""
                SELECT u.email, u.full_name FROM enrollment e JOIN users u ON u.user_id = e.student_user_id
                WHERE e.enrollment_id = :e
                """, Map.of("e", enrollmentId), (rs, i) -> new String[]{rs.getString(1), rs.getString(2)})
                .stream().findFirst();
    }

    public Optional<String> userName(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return jdbc.queryForList("SELECT full_name FROM users WHERE user_id = :id", Map.of("id", userId), String.class)
                .stream().findFirst();
    }

    /** True if the doctor teaches a group of the course right now (needed to change its rules). */
    public boolean teachesOfferingNow(long offeringId, long doctorId, LocalDateTime now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM teaching_assignment a JOIN student_group g ON g.group_id = a.group_id
                               WHERE g.offering_id = :o AND a.doctor_user_id = :d
                                 AND a.starts_at <= :now AND (a.ends_at IS NULL OR a.ends_at > :now))
                """, Map.of("o", offeringId, "d", doctorId, "now", now), Boolean.class));
    }

    /** True if the doctor teaches or taught a group of the course (enough to read rules and warnings). */
    public boolean everTaughtOffering(long offeringId, long doctorId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM teaching_assignment a JOIN student_group g ON g.group_id = a.group_id
                               WHERE g.offering_id = :o AND a.doctor_user_id = :d)
                """, Map.of("o", offeringId, "d", doctorId), Boolean.class));
    }

    // ================================================================ mapping

    private static RuleRow rule(ResultSet rs) throws SQLException {
        return new RuleRow(rs.getLong("rule_id"), rs.getLong("offering_id"), rs.getString("group_type"),
                rs.getBoolean("enabled"), rs.getObject("absence_limit", Integer.class),
                rs.getObject("updated_at", LocalDateTime.class), rs.getObject("updated_by", Long.class));
    }

    private static WarningRow warning(ResultSet rs) throws SQLException {
        return new WarningRow(rs.getLong("warning_id"), rs.getLong("enrollment_id"), rs.getLong("offering_id"),
                rs.getString("group_type"), rs.getObject("absence_limit", Integer.class),
                rs.getObject("issued_at", LocalDateTime.class), rs.getObject("emailed_at", LocalDateTime.class),
                rs.getLong("user_id"), rs.getString("university_id"), rs.getString("full_name"),
                rs.getString("full_name_ar"), rs.getString("program_name"), rs.getString("course_code"));
    }

    private static List<Integer> weeks(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        List<Integer> weeks = new ArrayList<>(Arrays.stream((Object[]) array.getArray())
                .filter(Objects::nonNull).map(o -> ((Number) o).intValue()).toList());
        weeks.sort(null);
        return weeks;
    }
}
