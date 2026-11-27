package com.edusync.edusync.backend.attendance.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Attendance phase B SQL: corrections, change history, totals and percentages.
 *
 * A "slot" = one student on one session's roster (member of the group when the
 * session started), with that student's mark or NULL. All totals are built from
 * slots of FINISHED sessions only, so a class still in progress never lowers a
 * percentage. Rule approved 27 Nov 2026; see docs/openapi.yaml.
 */
@Repository
public class AttendanceStatsQueries {

    // Plain string (not a text block) so the spaces around it are kept
    private static final String SLOTS_FROM =
            " FROM attendance_session s"
            + " JOIN student_group g ON g.group_id = s.group_id"
            + " JOIN group_membership m ON m.group_id = s.group_id"
            + "   AND m.starts_at <= s.starts_at AND (m.ends_at IS NULL OR m.ends_at > s.starts_at)"
            + " LEFT JOIN attendance_record r ON r.session_id = s.session_id AND r.enrollment_id = m.enrollment_id ";

    private static final String TOTALS =
            " COUNT(*) AS total,"
            + " COUNT(*) FILTER (WHERE r.status = 'PRESENT') AS present,"
            + " COUNT(*) FILTER (WHERE r.status = 'LATE') AS late,"
            + " COUNT(*) FILTER (WHERE r.status = 'ABSENT') AS absent,"
            + " COUNT(*) FILTER (WHERE r.status IS NULL) AS unmarked ";

    private final NamedParameterJdbcTemplate jdbc;

    public AttendanceStatsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Totals(int sessions, int present, int late, int absent, int unmarked) {
        public static final Totals NONE = new Totals(0, 0, 0, 0, 0);

        public int attended() {
            return present + late;
        }

        /** (present + late) / finished sessions x 100, 1 decimal; null when no session is finished yet. */
        public BigDecimal percent() {
            if (sessions == 0) {
                return null;
            }
            return BigDecimal.valueOf(attended() * 100L)
                    .divide(BigDecimal.valueOf(sessions), 1, RoundingMode.HALF_UP);
        }
    }

    public record RecordRow(long recordId, long sessionId, long enrollmentId, String status) {
    }

    public record ChangeRow(long changeId, long recordId, long doctorId, String doctorName, String oldStatus,
                            String newStatus, String reason, LocalDateTime changedAt) {
    }

    public record StudentRow(long enrollmentId, long userId, String universityId, String fullName, String fullNameAr) {
    }

    public record MySessionRow(long sessionId, LocalDateTime startsAt, String groupType, String groupLabel,
                               String status) {
    }

    // ================================================================ records + corrections

    public Optional<RecordRow> findRecord(long sessionId, long enrollmentId) {
        return jdbc.query("""
                SELECT record_id, session_id, enrollment_id, status FROM attendance_record
                WHERE session_id = :s AND enrollment_id = :e
                """, Map.of("s", sessionId, "e", enrollmentId), (rs, i) -> new RecordRow(
                rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4))).stream().findFirst();
    }

    public Optional<RecordRow> findRecord(long recordId) {
        return jdbc.query("SELECT record_id, session_id, enrollment_id, status FROM attendance_record WHERE record_id = :id",
                Map.of("id", recordId), (rs, i) -> new RecordRow(
                        rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4))).stream().findFirst();
    }

    /** Correction of a student who was never marked: the record is created by hand (MANUAL). */
    public long insertManualRecord(long sessionId, long enrollmentId, String status, LocalDateTime now) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO attendance_record (session_id, enrollment_id, status, method, marked_at)
                VALUES (:s, :e, :status, 'MANUAL', :now)
                """, new MapSqlParameterSource().addValue("s", sessionId).addValue("e", enrollmentId)
                .addValue("status", status).addValue("now", now), key, new String[]{"record_id"});
        return key.getKey().longValue();
    }

    /** Only the status changes; method and marked_at keep saying how the mark was first made. */
    public void updateStatus(long recordId, String status) {
        jdbc.update("UPDATE attendance_record SET status = :status WHERE record_id = :id",
                Map.of("id", recordId, "status", status));
    }

    public void insertChange(long recordId, String oldStatus, String newStatus, String reason, long doctorId,
                             LocalDateTime now) {
        jdbc.update("""
                INSERT INTO attendance_change (record_id, old_status, new_status, reason_code, doctor_id, changed_at)
                VALUES (:id, :old, :new, :reason, :doctor, :now)
                """, new MapSqlParameterSource().addValue("id", recordId).addValue("old", oldStatus)
                .addValue("new", newStatus).addValue("reason", reason).addValue("doctor", doctorId)
                .addValue("now", now));
    }

    /** Oldest first (contract). */
    public List<ChangeRow> changes(long recordId) {
        return jdbc.query("""
                SELECT c.change_id, c.record_id, c.doctor_id, u.full_name, c.old_status, c.new_status,
                       c.reason_code, c.changed_at
                FROM attendance_change c JOIN users u ON u.user_id = c.doctor_id
                WHERE c.record_id = :id
                ORDER BY c.changed_at, c.change_id
                """, Map.of("id", recordId), (rs, i) -> new ChangeRow(
                rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getObject(8, LocalDateTime.class)));
    }

    // ================================================================ totals

    /** enrollmentId -> totals over finished sessions (any group of the enrollment's offering). */
    public Map<Long, Totals> totalsByEnrollment(Collection<Long> enrollmentIds) {
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        return totalsBy("m.enrollment_id", "m.enrollment_id IN (:ids)", Map.of("ids", enrollmentIds));
    }

    /** enrollmentId -> totals over finished sessions of the given groups only. */
    public Map<Long, Totals> totalsByEnrollmentInGroups(Collection<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return totalsBy("m.enrollment_id", "s.group_id IN (:ids)", Map.of("ids", groupIds));
    }

    /** groupId -> totals over all roster slots of its finished sessions (for the group's average). */
    public Map<Long, Totals> totalsByGroup(Collection<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return totalsBy("s.group_id", "s.group_id IN (:ids)", Map.of("ids", groupIds));
    }

    private Map<Long, Totals> totalsBy(String key, String where, Map<String, ?> params) {
        Map<Long, Totals> result = new HashMap<>();
        jdbc.query("SELECT " + key + " AS k," + TOTALS + SLOTS_FROM
                        + " WHERE s.state = 'FINISHED' AND " + where + " GROUP BY " + key,
                params,
                rs -> {
                    result.put(rs.getLong("k"), new Totals(rs.getInt("total"), rs.getInt("present"),
                            rs.getInt("late"), rs.getInt("absent"), rs.getInt("unmarked")));
                });
        return result;
    }

    // ================================================================ who / what is visible

    public boolean offeringExists(long offeringId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM course_offering WHERE offering_id = :id)",
                Map.of("id", offeringId), Boolean.class));
    }

    public List<Long> groupsOfOffering(long offeringId) {
        return jdbc.queryForList("SELECT group_id FROM student_group WHERE offering_id = :id ORDER BY group_id",
                Map.of("id", offeringId), Long.class);
    }

    /** Groups of the offering the doctor teaches or taught ("doctors see only the groups they teach"). */
    public List<Long> doctorGroupsOfOffering(long offeringId, long doctorId) {
        return jdbc.queryForList("""
                SELECT DISTINCT g.group_id FROM student_group g
                JOIN teaching_assignment a ON a.group_id = g.group_id
                WHERE g.offering_id = :o AND a.doctor_user_id = :d
                ORDER BY g.group_id
                """, Map.of("o", offeringId, "d", doctorId), Long.class);
    }

    /** Students (not WITHDRAWN) who were ever members of the groups: one summary row each. */
    public List<StudentRow> studentsOfGroups(Collection<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT DISTINCT e.enrollment_id, u.user_id, u.university_id, u.full_name, u.full_name_ar
                FROM group_membership m
                JOIN enrollment e ON e.enrollment_id = m.enrollment_id
                JOIN users u      ON u.user_id = e.student_user_id
                WHERE m.group_id IN (:ids) AND e.status IN ('ACTIVE', 'COMPLETED')
                ORDER BY u.university_id
                """, Map.of("ids", groupIds), (rs, i) -> new StudentRow(
                rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)));
    }

    /** The student's enrollment in the offering (any status), if any. */
    public Optional<Long> enrollmentOf(long studentId, long offeringId) {
        return jdbc.queryForList("""
                SELECT enrollment_id FROM enrollment WHERE student_user_id = :s AND offering_id = :o
                """, Map.of("s", studentId, "o", offeringId), Long.class).stream().findFirst();
    }

    /** Every session (any state) the enrollment was on the roster of, newest first, with its mark. */
    public List<MySessionRow> sessionsOfEnrollment(long enrollmentId) {
        return jdbc.query("SELECT s.session_id, s.starts_at, g.group_type, g.label, r.status" + SLOTS_FROM
                        + " WHERE m.enrollment_id = :e ORDER BY s.starts_at DESC, s.session_id DESC",
                Map.of("e", enrollmentId), (rs, i) -> new MySessionRow(
                        rs.getLong(1), rs.getObject(2, LocalDateTime.class), rs.getString(3), rs.getString(4),
                        rs.getString(5)));
    }
}
