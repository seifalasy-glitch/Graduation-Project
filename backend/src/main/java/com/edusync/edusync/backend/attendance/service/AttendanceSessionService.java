package com.edusync.edusync.backend.attendance.service;

import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.attendance.AttendanceErrors;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.CreateSessionRequest;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.FinishResponse;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.QrChallengeResponse;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.RosterEntry;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.SessionResponse;
import com.edusync.edusync.backend.attendance.repository.AttendanceQueries;
import com.edusync.edusync.backend.attendance.repository.AttendanceQueries.Counts;
import com.edusync.edusync.backend.attendance.repository.AttendanceQueries.RosterRow;
import com.edusync.edusync.backend.attendance.repository.AttendanceQueries.SessionRow;
import com.edusync.edusync.backend.auth.service.ResetTokenService;
import com.edusync.edusync.backend.common.DateTimeUtil;
import com.edusync.edusync.backend.enrollment.dto.StudentRef;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.registration.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Doctor side of attendance (phase A): open a session, rotating QR, roster,
 * manual marks, finish. Students scan in AttendanceCheckInService.
 *
 * Who may do what:
 *  - open / QR / mark / finish: the doctor must teach the group RIGHT NOW (else NOT_YOUR_GROUP).
 *  - read (list, get, roster): an admin, or a doctor who teaches or taught the group.
 *    Anyone else gets 404 SESSION_NOT_FOUND / GROUP_NOT_FOUND (the session stays invisible).
 *
 * Session state is stored as QR_OPEN, MANUAL_ONLY or FINISHED. A QR_OPEN session whose
 * QR window has passed is shown and treated as MANUAL_ONLY (see effectiveState).
 */
@Service
public class AttendanceSessionService {

    static final Set<String> METHODS = Set.of("LOCAL_WIFI_QR", "INTERNET_QR", "MANUAL");
    static final Set<String> STATUSES = Set.of("PRESENT", "LATE", "ABSENT");

    private final AttendanceQueries queries;
    private final CurrentUserService currentUser;
    private final ResetTokenService tokens;   // random token + SHA-256 hash (only the hash is stored)
    private final int qrRotationSeconds;

    public AttendanceSessionService(
            AttendanceQueries queries,
            CurrentUserService currentUser,
            ResetTokenService tokens,
            @Value("${attendance.qr-rotation-seconds:15}") int qrRotationSeconds
    ) {
        this.queries = queries;
        this.currentUser = currentUser;
        this.tokens = tokens;
        this.qrRotationSeconds = qrRotationSeconds;
    }

    // ================================================================ open

    @Transactional
    public SessionResponse open(CreateSessionRequest request) {
        String method = request.method().trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw AcademicErrors.validation("method", "method must be LOCAL_WIFI_QR, INTERNET_QR or MANUAL");
        }
        boolean qr = !"MANUAL".equals(method);

        Integer window = request.qrWindowMinutes();
        if (qr && (window == null || window < 1 || window > 60)) {
            throw AcademicErrors.validation("qrWindowMinutes", "qrWindowMinutes (1-60) is required for QR sessions");
        }
        if (!qr && window != null) {
            throw AcademicErrors.validation("qrWindowMinutes", "Leave qrWindowMinutes out for MANUAL sessions");
        }
        if (request.lateAfterMinutes() != null && request.lateAfterMinutes() < 0) {
            throw AcademicErrors.validation("lateAfterMinutes", "lateAfterMinutes cannot be negative");
        }

        long groupId = request.groupId();
        queries.groupOffering(groupId).orElseThrow(EnrollmentErrors::groupNotFound);

        if (request.roomId() != null) {
            String subnet = queries.roomSubnet(request.roomId()).orElseThrow(AcademicErrors::roomNotFound);
            if ("LOCAL_WIFI_QR".equals(method) && !SubnetMatcher.isValid(subnet)) {
                throw AcademicErrors.validation("roomId",
                        "LOCAL_WIFI_QR needs a room with a valid subnet (e.g. 10.20.5.0/24)");
            }
        } else if ("LOCAL_WIFI_QR".equals(method)) {
            throw AcademicErrors.validation("roomId", "LOCAL_WIFI_QR needs a roomId");
        }

        LocalDateTime now = LocalDateTime.now();
        long assignmentId = queries.currentAssignment(groupId, me(), now)
                .orElseThrow(AttendanceErrors::notYourGroup);

        if (queries.hasUnfinishedSession(groupId)) {
            throw AttendanceErrors.sessionAlreadyOpen();
        }

        long sessionId;
        try {
            sessionId = queries.insertSession(groupId, request.roomId(), method,
                    qr ? "QR_OPEN" : "MANUAL_ONLY", assignmentId, now,
                    qr ? now.plusMinutes(window) : null, request.lateAfterMinutes());
        } catch (DuplicateKeyException e) {
            // Two "open" clicks at the same moment: the V7 unique index lets only one through
            throw AttendanceErrors.sessionAlreadyOpen();
        }
        return toResponse(queries.findSession(sessionId).orElseThrow());
    }

    // ================================================================ read

    @Transactional(readOnly = true)
    public List<SessionResponse> sessionsOfGroup(long groupId) {
        queries.groupOffering(groupId).orElseThrow(EnrollmentErrors::groupNotFound);
        if (!canRead(groupId)) {
            throw EnrollmentErrors.groupNotFound();
        }
        List<SessionRow> rows = queries.sessionsOfGroup(groupId);
        Map<Long, Counts> counts = queries.counts(rows.stream().map(SessionRow::sessionId).toList());
        return rows.stream().map(s -> toResponse(s, counts.getOrDefault(s.sessionId(), Counts.EMPTY))).toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse get(long sessionId) {
        return toResponse(readable(sessionId));
    }

    @Transactional(readOnly = true)
    public List<RosterEntry> roster(long sessionId) {
        readable(sessionId);
        return queries.roster(sessionId).stream().map(AttendanceSessionService::toEntry).toList();
    }

    // ================================================================ QR

    /** A new code every call (the screen calls every 15 s). Only its hash is saved. */
    @Transactional
    public QrChallengeResponse newQr(long sessionId) {
        SessionRow session = queries.findSession(sessionId).orElseThrow(AttendanceErrors::sessionNotFound);
        LocalDateTime now = LocalDateTime.now();
        requireTeachingNow(session, now);

        if ("MANUAL".equals(session.method())) {
            throw AttendanceErrors.notAQrSession();
        }
        if (!"QR_OPEN".equals(effectiveState(session, now))) {
            throw AttendanceErrors.windowClosed();
        }

        String token = tokens.generateToken();
        LocalDateTime expiresAt = now.plusSeconds(qrRotationSeconds);
        queries.deleteExpiredChallenges(sessionId, now);
        queries.insertChallenge(sessionId, tokens.hashToken(token), expiresAt);
        return new QrChallengeResponse(token, DateTimeUtil.toInstant(expiresAt));
    }

    // ================================================================ manual mark

    @Transactional
    public RosterEntry mark(long sessionId, long enrollmentId, String statusText) {
        String status = statusText.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw AcademicErrors.validation("status", "status must be PRESENT, LATE or ABSENT");
        }

        SessionRow session = queries.lockSession(sessionId, true).orElseThrow(AttendanceErrors::sessionNotFound);
        LocalDateTime now = LocalDateTime.now();
        requireTeachingNow(session, now);

        if ("FINISHED".equals(session.state())) {
            throw AttendanceErrors.sessionFinished();
        }
        queries.rosterEntry(sessionId, enrollmentId).orElseThrow(AttendanceErrors::notInGroup);

        queries.upsertManual(sessionId, enrollmentId, status, now);
        return toEntry(queries.rosterEntry(sessionId, enrollmentId).orElseThrow());
    }

    // ================================================================ finish

    @Transactional
    public FinishResponse finish(long sessionId, boolean markUnmarkedAsAbsent) {
        SessionRow session = queries.lockSession(sessionId, true).orElseThrow(AttendanceErrors::sessionNotFound);
        LocalDateTime now = LocalDateTime.now();
        requireTeachingNow(session, now);

        if ("FINISHED".equals(session.state())) {
            throw AttendanceErrors.sessionFinished();
        }

        int absentMarked = markUnmarkedAsAbsent ? queries.markUnmarkedAbsent(sessionId, now) : 0;

        // ends_at must be after starts_at (DB check), even if finished in the same instant
        LocalDateTime endsAt = now.isAfter(session.startsAt()) ? now : session.startsAt().plusSeconds(1);
        queries.finishSession(sessionId, endsAt);

        return new FinishResponse(toResponse(queries.findSession(sessionId).orElseThrow()), absentMarked);
    }

    // ================================================================ helpers

    /** QR_OPEN becomes MANUAL_ONLY once the QR window has passed. */
    static String effectiveState(SessionRow session, LocalDateTime now) {
        if ("QR_OPEN".equals(session.state())
                && session.qrWindowEndsAt() != null && !now.isBefore(session.qrWindowEndsAt())) {
            return "MANUAL_ONLY";
        }
        return session.state();
    }

    private SessionRow readable(long sessionId) {
        SessionRow session = queries.findSession(sessionId).orElseThrow(AttendanceErrors::sessionNotFound);
        if (!canRead(session.groupId())) {
            throw AttendanceErrors.sessionNotFound();
        }
        return session;
    }

    private boolean canRead(long groupId) {
        return currentUser.isAdmin() || queries.everTaught(groupId, me());
    }

    private void requireTeachingNow(SessionRow session, LocalDateTime now) {
        if (queries.currentAssignment(session.groupId(), me(), now).isEmpty()) {
            throw AttendanceErrors.notYourGroup();
        }
    }

    private long me() {
        return currentUser.get().getUserId();
    }

    private SessionResponse toResponse(SessionRow s) {
        return toResponse(s, queries.counts(List.of(s.sessionId())).getOrDefault(s.sessionId(), Counts.EMPTY));
    }

    private static SessionResponse toResponse(SessionRow s, Counts c) {
        return new SessionResponse(
                s.sessionId(), s.groupId(), s.roomId(), s.teachingAssignmentId(), s.method(),
                effectiveState(s, LocalDateTime.now()),
                DateTimeUtil.toInstant(s.startsAt()), DateTimeUtil.toInstant(s.qrWindowEndsAt()),
                DateTimeUtil.toInstant(s.endsAt()), s.lateAfterMinutes(),
                c.present(), c.late(), c.absent(), c.unmarked());
    }

    static RosterEntry toEntry(RosterRow r) {
        return new RosterEntry(
                r.enrollmentId(), r.recordId(),
                new StudentRef(r.userId(), r.universityId(), r.fullName(), r.fullNameAr()),
                r.status(), r.method(), DateTimeUtil.toInstant(r.markedAt()), r.corrected());
    }
}
