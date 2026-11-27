package com.edusync.edusync.backend.attendance.controller;

import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.WarningResponse;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.WarningRuleInput;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.WarningRuleResponse;
import com.edusync.edusync.backend.attendance.service.AttendanceWarningService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Attendance warnings (tag "Attendance Warnings"): rules, lists and the department's PDF. */
@RestController
@RequestMapping("/api")
public class AttendanceWarningController {

    private final AttendanceWarningService warnings;

    public AttendanceWarningController(AttendanceWarningService warnings) {
        this.warnings = warnings;
    }

    @GetMapping("/offerings/{offeringId}/attendance-warning-rules")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<List<WarningRuleResponse>> rules(@PathVariable long offeringId) {
        return ResponseEntity.ok(warnings.rules(offeringId));
    }

    @PutMapping("/offerings/{offeringId}/attendance-warning-rules/{groupType}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<WarningRuleResponse> saveRule(@PathVariable long offeringId, @PathVariable String groupType,
                                                        @Valid @RequestBody WarningRuleInput input) {
        return ResponseEntity.ok(warnings.saveRule(offeringId, groupType, input));
    }

    @GetMapping("/offerings/{offeringId}/attendance-warnings")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<List<WarningResponse>> ofOffering(@PathVariable long offeringId,
                                                            @RequestParam(required = false) String groupType) {
        return ResponseEntity.ok(warnings.warningsOfOffering(offeringId, groupType));
    }

    /** The department's Arabic absence list as a PDF download (built on demand, not stored). */
    @GetMapping("/offerings/{offeringId}/attendance-warnings/report")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<byte[]> report(@PathVariable long offeringId, @RequestParam String groupType) {
        AttendanceWarningService.Report report = warnings.report(offeringId, groupType);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.fileName()).build().toString())
                .body(report.pdf());
    }

    @GetMapping("/me/attendance-warnings")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<WarningResponse>> mine() {
        return ResponseEntity.ok(warnings.mine());
    }
}
