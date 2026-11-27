package com.edusync.edusync.backend.attendance.controller;

import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.CheckInRequest;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.CheckInResponse;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.CreateSessionRequest;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.FinishRequest;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.FinishResponse;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.MarkRequest;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.QrChallengeResponse;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.RosterEntry;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.SessionResponse;
import com.edusync.edusync.backend.attendance.service.AttendanceCheckInService;
import com.edusync.edusync.backend.attendance.service.AttendanceSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Attendance phase A (tags "Attendance - Doctor" / "Attendance - Student"). Roles = x-roles in openapi.yaml. */
@RestController
@RequestMapping("/api")
public class AttendanceController {

    private final AttendanceSessionService sessions;
    private final AttendanceCheckInService checkIns;

    public AttendanceController(AttendanceSessionService sessions, AttendanceCheckInService checkIns) {
        this.sessions = sessions;
        this.checkIns = checkIns;
    }

    @PostMapping("/attendance/sessions")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<SessionResponse> open(@Valid @RequestBody CreateSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessions.open(request));
    }

    @GetMapping("/groups/{groupId}/attendance-sessions")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<List<SessionResponse>> sessionsOfGroup(@PathVariable long groupId) {
        return ResponseEntity.ok(sessions.sessionsOfGroup(groupId));
    }

    @GetMapping("/attendance/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<SessionResponse> get(@PathVariable long sessionId) {
        return ResponseEntity.ok(sessions.get(sessionId));
    }

    @GetMapping("/attendance/sessions/{sessionId}/qr")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<QrChallengeResponse> qr(@PathVariable long sessionId) {
        return ResponseEntity.ok(sessions.newQr(sessionId));
    }

    @GetMapping("/attendance/sessions/{sessionId}/roster")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<List<RosterEntry>> roster(@PathVariable long sessionId) {
        return ResponseEntity.ok(sessions.roster(sessionId));
    }

    @PutMapping("/attendance/sessions/{sessionId}/records/{enrollmentId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<RosterEntry> mark(@PathVariable long sessionId, @PathVariable long enrollmentId,
                                           @Valid @RequestBody MarkRequest request) {
        return ResponseEntity.ok(sessions.mark(sessionId, enrollmentId, request.status()));
    }

    @PostMapping("/attendance/sessions/{sessionId}/finish")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<FinishResponse> finish(@PathVariable long sessionId,
                                                 @RequestBody(required = false) FinishRequest request) {
        boolean absent = request != null && Boolean.TRUE.equals(request.markUnmarkedAsAbsent());
        return ResponseEntity.ok(sessions.finish(sessionId, absent));
    }

    /**
     * The student's IP is the request's remote address. If the backend is later put behind
     * a proxy (nginx), set server.forward-headers-strategy=native so this is the real
     * student IP and not the proxy's.
     */
    @PostMapping("/attendance/check-in")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody CheckInRequest request,
                                                   HttpServletRequest http) {
        return ResponseEntity.ok(checkIns.checkIn(request.qrToken(), http.getRemoteAddr()));
    }
}
