package com.edusync.edusync.backend.attendance.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQL for attendance. Plain SQL because the main rules are easiest to read here:
 *
 *  - ROSTER of a session = students whose membership in the session's group was
 *    active when the session started (started on/before starts_at, not ended by then).
 *  - One ATTENDANCE_RECORD per (session, enrollment) (V7 unique rule): a QR scan and a
 *    manual mark write the same row, using INSERT ... ON CONFLICT.
 *
 * Times are LocalDateTime (DB columns have no timezone), like the rest of the backend.
 */
@Repository
public class AttendanceQueries {

    // "member of the session's group at the session start", used in several queries.
    // A plain string with spaces on both sides: text blocks drop the trailing space
    // before their closing quotes, which would glue "WHERE" to this text.
    private static final String MEMBER_AT_START =
            " m.group_id = s.group_id"
            + " AND m.starts_at <= s.starts_at"
            + " AND (m.ends_at IS NULL OR m.ends_at > s.starts_at) ";

    private final NamedParameterJdbcTemplate jdbc;

    public AttendanceQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ rows

    public record SessionRow(long sessionId, long groupId, Long roomId, String method, String state,
                             long teachingAssignmentId, LocalDateTime startsAt, LocalDateTime endsAt,
                             LocalDateTime qrWindowEndsAt, Integer lateAfterMinutes, long offeringId) {
    }

    public record Counts(int present, int late, int absent, int unmarked) {
        public static final Counts EMPTY = new Counts(0, 0, 0, 0);
    }

    public record RosterRow(long enrollmentId, long userId, String universityId, String fullName,
                            String fullNameAr, Long recordId, String status, String method,
                            LocalDateTime markedAt, boolean corrected) {
    }

    // ------------------------------------------------------------------ sessions

    private static final String SESSION_SELECT = """
            SELECT s.session_id, s.group_id, s.room_id, s.method, s.state, s.teaching_assignment_id,
                   s.starts_at, s.ends_at, s.qr_window_ends_at, s.late_after_minutes, g.offering_id
            FROM attendance_session s
            JOIN student_group g ON g.group_id = s.group_id
            """;

    public Optional<SessionRow> findSession(long sessionId) {
        return jdbc.query(SESSION_SELECT + " WHERE s.session_id = :id",
                Map.of("id", sessionId), (rs, i) -> sessionRow(rs)).stream().findFirst();
    }

    /**
     * Same as findSession, but locks the session row until the transaction ends.
     * exclusive = true for doctor actions (mark, finish); false (shared) for check-ins,
     * so many students can scan at once while "finish" waits for them.
     */
    public Optional<SessionRow> lockSession(long sessionId, boolean exclusive) {
        String lock = exclusive ? " FOR UPDATE OF s" : " FOR SHARE OF s";
        return jdbc.query(SESSION_SELECT + " WHERE s.session_id = :id" + lock,
                Map.of("id", sessionId), (rs, i) -> sessionRow(rs)).stream().findFirst();
    }

    public List<SessionRow> sessionsOfGroup(long groupId) {
        return jdbc.query(SESSION_SELECT + " WHERE s.group_id = :groupId ORDER BY s.starts_at DESC, s.session_id DESC",
                Map.of("groupId", groupId), (rs, i) -> sessionRow(rs));
    }

    public boolean hasUnfinishedSession(long groupId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM attendance_session WHERE group_id = :groupId AND state <> 'FINISHED')",
                Map.of("groupId", groupId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public long insertSession(long groupId, Long roomId, String method, String state, long teachingAssignmentId,
                              LocalDateTime startsAt, LocalDateTime qrWindowEndsAt, Integer lateAfterMinutes) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO attendance_session (group_id, room_id, method, state, teaching_assignment_id,
                                                starts_at, qr_window_ends_at, late_after_minutes)
                VALUES (:groupId, :roomId, :method, :state, :assignmentId, :startsAt, :qrEnds, :late)
                """,
                new MapSqlParameterSource()
                        .addValue("groupId", groupId).addValue("roomId", roomId)
                        .addValue("method", method).addValue("state", state)
                        .addValue("assignmentId", teachingAssignmentId).addValue("startsAt", startsAt)
                        .addValue("qrEnds", qrWindowEndsAt).addValue("late", lateAfterMinutes),
                key, new String[]{"session_id"});
        return key.getKey().longValue();
    }

    public void finishSession(long sessionId, LocalDateTime endsAt) {
        jdbc.update("UPDATE attendance_session SET state = 'FINISHED', ends_at = :endsAt WHERE session_id = :id",
                Map.of("id", sessionId, "endsAt", endsAt));
    }

    /** sessionId -> present / late / absent / unmarked (unmarked = roster size - marked). */
    public Map<Long, Counts> counts(Collection<Long> sessionIds) {
        Map<Long, Counts> result = new HashMap<>();
        if (sessionIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                SELECT s.session_id,
                       (SELECT COUNT(*) FROM group_membership m WHERE """ + MEMBER_AT_START + """
                       ) AS roster,
                       COUNT(r.record_id) FILTER (WHERE r.status = 'PRESENT') AS present,
                       COUNT(r.record_id) FILTER (WHERE r.status = 'LATE')    AS late,
                       COUNT(r.record_id) FILTER (WHERE r.status = 'ABSENT')  AS absent,
                       COUNT(r.record_id)                                     AS marked
                FROM attendance_session s
                LEFT JOIN attendance_record r ON r.session_id = s.session_id
                WHERE s.session_id IN (:ids)
                GROUP BY s.session_id, s.group_id, s.starts_at
                """,
                Map.of("ids", sessionIds),
                rs -> {
                    int unmarked = Math.max(0, rs.getInt("roster") - rs.getInt("marked"));
                    result.put(rs.getLong("session_id"),
                            new Counts(rs.getInt("present"), rs.getInt("late"), rs.getInt("absent"), unmarked));
                });
        return result;
    }

    // ------------------------------------------------------------------ who may do what

    /** The doctor's teaching assignment on the group that is active at :now, if any. */
    public Optional<Long> currentAssignment(long groupId, long doctorId, LocalDateTime now) {
        return jdbc.query("""
                SELECT assignment_id FROM teaching_assignment
                WHERE group_id = :groupId AND doctor_user_id = :doctorId
                  AND starts_at <= :now AND (ends_at IS NULL OR ends_at > :now)
                ORDER BY starts_at DESC LIMIT 1
                """,
                Map.of("groupId", groupId, "doctorId", doctorId, "now", now),
                (rs, i) -> rs.getLong(1)).stream().findFirst();
    }

    /** True if the doctor teaches or ever taught the group (enough to READ its sessions). */
    public boolean everTaught(long groupId, long doctorId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM teaching_assignment WHERE group_id = :groupId AND doctor_user_id = :doctorId)",
                Map.of("groupId", groupId, "doctorId", doctorId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    // ------------------------------------------------------------------ roster

    public List<RosterRow> roster(long sessionId) {
        return jdbc.query("""
                SELECT e.enrollment_id, u.user_id, u.university_id, u.full_name, u.full_name_ar,
                       r.record_id, r.status, r.method, r.marked_at,
                       EXISTS (SELECT 1 FROM attendance_change c WHERE c.record_id = r.record_id) AS corrected
                FROM attendance_session s
                JOIN group_membership m ON """ + MEMBER_AT_START + """
                JOIN enrollment e ON e.enrollment_id = m.enrollment_id
                JOIN users u      ON u.user_id = e.student_user_id
                LEFT JOIN attendance_record r ON r.session_id = s.session_id AND r.enrollment_id = e.enrollment_id
                WHERE s.session_id = :id
                ORDER BY u.university_id
                """,
                Map.of("id", sessionId),
                (rs, i) -> new RosterRow(
                        rs.getLong("enrollment_id"), rs.getLong("user_id"), rs.getString("university_id"),
                        rs.getString("full_name"), rs.getString("full_name_ar"),
                        (Long) rs.getObject("record_id", Long.class), rs.getString("status"), rs.getString("method"),
                        rs.getObject("marked_at", LocalDateTime.class), rs.getBoolean("corrected")));
    }

    public Optional<RosterRow> rosterEntry(long sessionId, long enrollmentId) {
        return roster(sessionId).stream().filter(r -> r.enrollmentId() == enrollmentId).findFirst();
    }

    /** The student's (not withdrawn) enrollment that is on the session's roster, if any. */
    public Optional<Long> studentOnRoster(long sessionId, long studentUserId) {
        return jdbc.query("""
                SELECT e.enrollment_id
                FROM attendance_session s
                JOIN group_membership m ON """ + MEMBER_AT_START + """
                JOIN enrollment e ON e.enrollment_id = m.enrollment_id
                WHERE s.session_id = :id AND e.student_user_id = :studentId AND e.status <> 'WITHDRAWN'
                LIMIT 1
                """,
                Map.of("id", sessionId, "studentId", studentUserId),
                (rs, i) -> rs.getLong(1)).stream().findFirst();
    }

    // ------------------------------------------------------------------ records

    /** Manual mark: creates the record or overwrites it (method becomes MANUAL). */
    public void upsertManual(long sessionId, long enrollmentId, String status, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO attendance_record (session_id, enrollment_id, status, method, marked_at)
                VALUES (:sessionId, :enrollmentId, :status, 'MANUAL', :now)
                ON CONFLICT (session_id, enrollment_id)
                DO UPDATE SET status = EXCLUDED.status, method = 'MANUAL', marked_at = EXCLUDED.marked_at
                """,
                Map.of("sessionId", sessionId, "enrollmentId", enrollmentId, "status", status, "now", now));
    }

    /** QR scan: only if the student has no record yet. Returns false if one already existed. */
    public boolean insertQrIfAbsent(long sessionId, long enrollmentId, String status, LocalDateTime now) {
        int rows = jdbc.update("""
                INSERT INTO attendance_record (session_id, enrollment_id, status, method, marked_at)
                VALUES (:sessionId, :enrollmentId, :status, 'QR', :now)
                ON CONFLICT (session_id, enrollment_id) DO NOTHING
                """,
                Map.of("sessionId", sessionId, "enrollmentId", enrollmentId, "status", status, "now", now));
        return rows == 1;
    }

    /** Finish with "mark unmarked as absent": ABSENT for every roster student without a record. */
    public int markUnmarkedAbsent(long sessionId, LocalDateTime now) {
        return jdbc.update("""
                INSERT INTO attendance_record (session_id, enrollment_id, status, method, marked_at)
                SELECT s.session_id, e.enrollment_id, 'ABSENT', 'AUTO_ABSENT', :now
                FROM attendance_session s
                JOIN group_membership m ON """ + MEMBER_AT_START + """
                JOIN enrollment e ON e.enrollment_id = m.enrollment_id AND e.status <> 'WITHDRAWN'
                WHERE s.session_id = :id
                ON CONFLICT (session_id, enrollment_id) DO NOTHING
                """,
                Map.of("id", sessionId, "now", now));
    }

    // ------------------------------------------------------------------ QR challenges

    public void insertChallenge(long sessionId, String tokenHash, LocalDateTime expiresAt) {
        jdbc.update("INSERT INTO qr_challenge (session_id, token_hash, expires_at) VALUES (:id, :hash, :exp)",
                Map.of("id", sessionId, "hash", tokenHash, "exp", expiresAt));
    }

    /** Old codes are useless once expired; keeps the table small. */
    public void deleteExpiredChallenges(long sessionId, LocalDateTime now) {
        jdbc.update("DELETE FROM qr_challenge WHERE session_id = :id AND expires_at <= :now",
                Map.of("id", sessionId, "now", now));
    }

    /** Session of a QR code that has not expired yet. */
    public Optional<Long> sessionOfValidChallenge(String tokenHash, LocalDateTime now) {
        return jdbc.query("SELECT session_id FROM qr_challenge WHERE token_hash = :hash AND expires_at > :now",
                Map.of("hash", tokenHash, "now", now), (rs, i) -> rs.getLong(1)).stream().findFirst();
    }

    // ------------------------------------------------------------------ small lookups

    public Optional<Long> groupOffering(long groupId) {
        return jdbc.query("SELECT offering_id FROM student_group WHERE group_id = :id",
                Map.of("id", groupId), (rs, i) -> rs.getLong(1)).stream().findFirst();
    }

    /** Room's subnet; Optional.empty() = no such room, Optional.of("") = room without subnet. */
    public Optional<String> roomSubnet(long roomId) {
        return jdbc.query("SELECT COALESCE(subnet, '') FROM room WHERE room_id = :id",
                Map.of("id", roomId), (rs, i) -> rs.getString(1)).stream().findFirst();
    }

    public String courseCode(long offeringId) {
        return jdbc.query("""
                SELECT c.course_code FROM course_offering o JOIN course c ON c.course_id = o.course_id
                WHERE o.offering_id = :id
                """, Map.of("id", offeringId), (rs, i) -> rs.getString(1)).stream().findFirst().orElse(null);
    }

    private static SessionRow sessionRow(ResultSet rs) throws SQLException {
        return new SessionRow(
                rs.getLong("session_id"), rs.getLong("group_id"), rs.getObject("room_id", Long.class),
                rs.getString("method"), rs.getString("state"), rs.getLong("teaching_assignment_id"),
                rs.getObject("starts_at", LocalDateTime.class), rs.getObject("ends_at", LocalDateTime.class),
                rs.getObject("qr_window_ends_at", LocalDateTime.class),
                rs.getObject("late_after_minutes", Integer.class), rs.getLong("offering_id"));
    }
}
