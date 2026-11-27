package com.edusync.edusync.backend.attendance;

import com.edusync.edusync.backend.common.ApiException;
import org.springframework.http.HttpStatus;

/** Attendance error codes from docs/openapi.yaml. Status rule: 403 not allowed, 404 not found, 409 state blocks it. */
public final class AttendanceErrors {

    private AttendanceErrors() {
    }

    public static ApiException sessionNotFound() {
        return of(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Attendance session not found");
    }

    public static ApiException notYourGroup() {
        return of(HttpStatus.FORBIDDEN, "NOT_YOUR_GROUP", "You do not currently teach this group");
    }

    public static ApiException sessionAlreadyOpen() {
        return of(HttpStatus.CONFLICT, "SESSION_ALREADY_OPEN",
                "This group already has an unfinished attendance session. Finish it first");
    }

    public static ApiException notAQrSession() {
        return of(HttpStatus.CONFLICT, "NOT_A_QR_SESSION", "This session uses manual attendance, not QR");
    }

    public static ApiException windowClosed() {
        return of(HttpStatus.CONFLICT, "WINDOW_CLOSED", "The QR scanning time for this session is over");
    }

    public static ApiException sessionFinished() {
        return of(HttpStatus.CONFLICT, "SESSION_FINISHED",
                "Attendance for this session is finished. Use a correction instead");
    }

    public static ApiException notInGroup() {
        return of(HttpStatus.FORBIDDEN, "NOT_IN_GROUP", "This student is not in the session's group");
    }

    public static ApiException qrExpired() {
        return of(HttpStatus.CONFLICT, "QR_EXPIRED", "This QR code has expired. Scan the new one on the screen");
    }

    public static ApiException alreadyMarked() {
        return of(HttpStatus.CONFLICT, "ALREADY_MARKED", "Your attendance for this session is already recorded");
    }

    public static ApiException networkNotAllowed() {
        return of(HttpStatus.FORBIDDEN, "NETWORK_NOT_ALLOWED",
                "Connect to the classroom Wi-Fi to scan this QR code");
    }

    public static ApiException sessionNotFinished() {
        return of(HttpStatus.CONFLICT, "SESSION_NOT_FINISHED",
                "Finish the session first; until then mark the student normally");
    }

    public static ApiException recordNotFound() {
        return of(HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND", "Attendance record not found");
    }

    private static ApiException of(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
