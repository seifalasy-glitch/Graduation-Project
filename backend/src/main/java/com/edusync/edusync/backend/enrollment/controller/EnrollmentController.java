package com.edusync.edusync.backend.enrollment.controller;

import com.edusync.edusync.backend.enrollment.dto.CreateEnrollmentRequest;
import com.edusync.edusync.backend.enrollment.dto.EnrollmentResponse;
import com.edusync.edusync.backend.enrollment.dto.UpdateEnrollmentRequest;
import com.edusync.edusync.backend.enrollment.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/offerings/{offeringId}/enrollments")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<List<EnrollmentResponse>> getEnrollments(
            @PathVariable Long offeringId,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(enrollmentService.getEnrollments(offeringId, programId, status));
    }

    @PostMapping("/offerings/{offeringId}/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnrollmentResponse> enroll(
            @PathVariable Long offeringId,
            @Valid @RequestBody CreateEnrollmentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(enrollmentService.enroll(offeringId, request));
    }

    @PatchMapping("/enrollments/{enrollmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnrollmentResponse> update(
            @PathVariable Long enrollmentId,
            @RequestBody UpdateEnrollmentRequest request
    ) {
        return ResponseEntity.ok(enrollmentService.update(enrollmentId, request));
    }
}
