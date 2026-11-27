package com.edusync.edusync.backend.attendance.service;

import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.attendance.AttendanceErrors;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.AttendanceChangeResponse;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.CorrectionRequest;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.RosterEntry;
import com.edusync.edusync.backend.attendance.repository.AttendanceQueries;
import com.edusync.edusync.backend.attendance.repository.AttendanceQueries.SessionRow;
import com.edusync.edusync.backend.attendance.repository.AttendanceStatsQueries;
import com.edusync.edusync.backend.attendance.repository.AttendanceStatsQueries.RecordRow;
import com.edusync.edusync.backend.common.DateTimeUtil;
import com.edusync.edusync.backend.enrollment.dto.DoctorRef;
import com.edusync.edusync.backend.registration.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Changing a mark AFTER the session is finished (before that, the normal manual mark is used).
 * Every correction saves old value, new value, doctor, reason and time in ATTENDANCE_CHANGE;
 * the roster then shows corrected = true.
 *
 * Errors: VALIDATION_ERROR (400; also when the new status equals the current one),
 * NOT_YOUR_GROUP (403), NOT_IN_GROUP (403), SESSION_NOT_FOUND (404), SESSION_NOT_FINISHED (409).
 */
@Service
public class AttendanceCorrectionService {

    static final Set<String> REASONS = Set.of(
            "QR_FAILED", "MANUAL_MARKING_ERROR", "STUDENT_ATTENDANCE_VERIFIED_LATER", "TECHNICAL_ISSUE");

    private final AttendanceQueries queries;
    private final AttendanceStatsQueries stats;
    private final CurrentUserService currentUser;

    public AttendanceCorrectionService(AttendanceQueries queries, AttendanceStatsQueries stats,
                                       CurrentUserService currentUser) {
        this.queries = queries;
        this.stats = stats;
        this.currentUser = currentUser;
    }

    @Transactional
    public RosterEntry correct(long sessionId, long enrollmentId, CorrectionRequest request) {
        String newStatus = request.newStatus().trim().toUpperCase(Locale.ROOT);
        String reason = request.reason().trim().toUpperCase(Locale.ROOT);
        if (!AttendanceSessionService.STATUSES.contains(newStatus)) {
            throw AcademicErrors.validation("newStatus", "newStatus must be PRESENT, LATE or ABSENT");
        }
        if (!REASONS.contains(reason)) {
            throw AcademicErrors.validation("reason",
                    "reason must be QR_FAILED, MANUAL_MARKING_ERROR, STUDENT_ATTENDANCE_VERIFIED_LATER or TECHNICAL_ISSUE");
        }

        SessionRow session = queries.lockSession(sessionId, true).orElseThrow(AttendanceErrors::sessionNotFound);
        LocalDateTime now = LocalDateTime.now();
        long doctorId = currentUser.get().getUserId();
        if (queries.currentAssignment(session.groupId(), doctorId, now).isEmpty()) {
            throw AttendanceErrors.notYourGroup();
        }
        if (!"FINISHED".equals(session.state())) {
            throw AttendanceErrors.sessionNotFinished();
        }
        queries.rosterEntry(sessionId, enrollmentId).orElseThrow(AttendanceErrors::notInGroup);

        RecordRow existing = stats.findRecord(sessionId, enrollmentId).orElse(null);
        long recordId;
        if (existing == null) {
            recordId = stats.insertManualRecord(sessionId, enrollmentId, newStatus, now);
        } else {
            if (newStatus.equals(existing.status())) {
                throw AcademicErrors.validation("newStatus", "The student is already " + newStatus);
            }
            recordId = existing.recordId();
            stats.updateStatus(recordId, newStatus);
        }
        stats.insertChange(recordId, existing == null ? null : existing.status(), newStatus, reason, doctorId, now);

        return AttendanceSessionService.toEntry(queries.rosterEntry(sessionId, enrollmentId).orElseThrow());
    }

    /** Admin, or a doctor who teaches / taught the group; anyone else gets RECORD_NOT_FOUND. */
    @Transactional(readOnly = true)
    public List<AttendanceChangeResponse> changes(long recordId) {
        RecordRow record = stats.findRecord(recordId).orElseThrow(AttendanceErrors::recordNotFound);
        SessionRow session = queries.findSession(record.sessionId()).orElseThrow(AttendanceErrors::recordNotFound);
        if (!currentUser.isAdmin() && !queries.everTaught(session.groupId(), currentUser.get().getUserId())) {
            throw AttendanceErrors.recordNotFound();
        }
        return stats.changes(recordId).stream()
                .map(c -> new AttendanceChangeResponse(
                        c.changeId(), c.recordId(), new DoctorRef(c.doctorId(), c.doctorName()),
                        c.oldStatus(), c.newStatus(), c.reason(), DateTimeUtil.toInstant(c.changedAt())))
                .toList();
    }
}
