package com.edusync.edusync.backend.attendance.dto;

import com.edusync.edusync.backend.enrollment.dto.StudentRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Request / response shapes of the attendance endpoints (names = schemas in docs/openapi.yaml). */
public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    /** CreateSessionRequest. Ranges are checked in AttendanceSessionService (one VALIDATION_ERROR per field). */
    public record CreateSessionRequest(
            @NotNull(message = "groupId is required") Long groupId,
            Long roomId,
            @NotBlank(message = "method is required") String method,
            Integer qrWindowMinutes,
            Integer lateAfterMinutes
    ) {
    }

    /** AttendanceSession. state is the effective state (QR_OPEN turns MANUAL_ONLY when the QR window passes). */
    public record SessionResponse(
            Long sessionId,
            Long groupId,
            Long roomId,
            Long teachingAssignmentId,
            String method,
            String state,
            Instant startsAt,
            Instant qrWindowEndsAt,
            Instant endsAt,
            Integer lateAfterMinutes,
            int presentCount,
            int lateCount,
            int absentCount,
            int unmarkedCount
    ) {
    }

    /** QrChallenge */
    public record QrChallengeResponse(String token, Instant expiresAt) {
    }

    /** RosterEntry. status / method / markedAt null = unmarked. */
    public record RosterEntry(
            Long enrollmentId,
            Long recordId,
            StudentRef student,
            String status,
            String method,
            Instant markedAt,
            boolean corrected
    ) {
    }

    /** MarkRequest */
    public record MarkRequest(@NotBlank(message = "status is required") String status) {
    }

    /** FinishRequest (body optional) */
    public record FinishRequest(Boolean markUnmarkedAsAbsent) {
    }

    /** FinishResponse */
    public record FinishResponse(SessionResponse session, int absentMarkedCount) {
    }

    /** CheckInRequest */
    public record CheckInRequest(@NotBlank(message = "qrToken is required") String qrToken) {
    }

    /** CheckInResponse */
    public record CheckInResponse(Long sessionId, String courseCode, String status, Instant markedAt) {
    }
}
