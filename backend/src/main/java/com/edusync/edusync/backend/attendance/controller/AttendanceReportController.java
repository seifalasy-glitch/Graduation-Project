package com.edusync.edusync.backend.attendance.controller;

import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.AttendanceChangeResponse;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.CorrectionRequest;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.MyAttendance;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.RosterEntry;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.SummaryRow;
import com.edusync.edusync.backend.attendance.service.AttendanceCorrectionService;
import com.edusync.edusync.backend.attendance.service.AttendanceReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Attendance phase B: corrections, change history, course summary, my attendance. Roles = x-roles. */
@RestController
@RequestMapping("/api")
public class AttendanceReportController {

    private final AttendanceCorrectionService corrections;
    private final AttendanceReportService reports;

    public AttendanceReportController(AttendanceCorrectionService corrections, AttendanceReportService reports) {
        this.corrections = corrections;
        this.reports = reports;
    }

    @PostMapping("/attendance/sessions/{sessionId}/records/{enrollmentId}/correction")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<RosterEntry> correct(@PathVariable long sessionId, @PathVariable long enrollmentId,
                                              @Valid @RequestBody CorrectionRequest request) {
        return ResponseEntity.ok(corrections.correct(sessionId, enrollmentId, request));
    }

    @GetMapping("/attendance/records/{recordId}/changes")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<List<AttendanceChangeResponse>> changes(@PathVariable long recordId) {
        return ResponseEntity.ok(corrections.changes(recordId));
    }

    @GetMapping("/offerings/{offeringId}/attendance-summary")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<List<SummaryRow>> summary(@PathVariable long offeringId,
                                                    @RequestParam(required = false) Long groupId) {
        return ResponseEntity.ok(reports.summary(offeringId, groupId));
    }

    @GetMapping("/me/attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<MyAttendance> mine(@RequestParam long offeringId) {
        return ResponseEntity.ok(reports.mine(offeringId));
    }
}
