package com.edusync.edusync.backend.attendance.service;

import com.edusync.edusync.backend.attendance.AttendanceErrors;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.CheckInResponse;
import com.edusync.edusync.backend.attendance.repository.AttendanceQueries;
import com.edusync.edusync.backend.attendance.repository.AttendanceQueries.SessionRow;
import com.edusync.edusync.backend.auth.service.ResetTokenService;
import com.edusync.edusync.backend.common.DateTimeUtil;
import com.edusync.edusync.backend.registration.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * POST /attendance/check-in: a student scans the QR on the doctor's screen.
 *
 * Checks, in this order (first failure wins):
 *  1. the code exists and is not older than 15 s ............ else QR_EXPIRED (409)
 *  2. the session's QR window is still open .................. else WINDOW_CLOSED (409)
 *  3. the student is on the session's roster (not withdrawn) . else NOT_IN_GROUP (403)
 *  4. LOCAL_WIFI_QR only: the student's IP is in the room subnet else NETWORK_NOT_ALLOWED (403)
 *  5. the student has no mark yet ............................ else ALREADY_MARKED (409)
 * Status: LATE if the scan is more than lateAfterMinutes after the start, else PRESENT.
 */
@Service
public class AttendanceCheckInService {

    private final AttendanceQueries queries;
    private final CurrentUserService currentUser;
    private final ResetTokenService tokens;

    public AttendanceCheckInService(AttendanceQueries queries, CurrentUserService currentUser, ResetTokenService tokens) {
        this.queries = queries;
        this.currentUser = currentUser;
        this.tokens = tokens;
    }

    /** @param clientIp the request's remote address (see AttendanceController) */
    @Transactional
    public CheckInResponse checkIn(String qrToken, String clientIp) {
        LocalDateTime now = LocalDateTime.now();

        long sessionId = queries.sessionOfValidChallenge(tokens.hashToken(qrToken.trim()), now)
                .orElseThrow(AttendanceErrors::qrExpired);

        // Shared lock: many students can scan together; "finish" waits until they are done
        SessionRow session = queries.lockSession(sessionId, false).orElseThrow(AttendanceErrors::qrExpired);
        if (!"QR_OPEN".equals(AttendanceSessionService.effectiveState(session, now))) {
            throw AttendanceErrors.windowClosed();
        }

        long enrollmentId = queries.studentOnRoster(sessionId, currentUser.get().getUserId())
                .orElseThrow(AttendanceErrors::notInGroup);

        if ("LOCAL_WIFI_QR".equals(session.method())) {
            String subnet = session.roomId() == null ? "" : queries.roomSubnet(session.roomId()).orElse("");
            if (!SubnetMatcher.contains(subnet, clientIp)) {
                throw AttendanceErrors.networkNotAllowed();
            }
        }

        boolean late = session.lateAfterMinutes() != null
                && now.isAfter(session.startsAt().plusMinutes(session.lateAfterMinutes()));
        String status = late ? "LATE" : "PRESENT";

        if (!queries.insertQrIfAbsent(sessionId, enrollmentId, status, now)) {
            throw AttendanceErrors.alreadyMarked();
        }

        return new CheckInResponse(sessionId, queries.courseCode(session.offeringId()), status,
                DateTimeUtil.toInstant(now));
    }
}
