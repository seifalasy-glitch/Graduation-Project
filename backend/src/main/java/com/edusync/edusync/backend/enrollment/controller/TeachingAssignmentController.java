package com.edusync.edusync.backend.enrollment.controller;

import com.edusync.edusync.backend.enrollment.dto.CreateTeachingAssignmentRequest;
import com.edusync.edusync.backend.enrollment.dto.EndPeriodRequest;
import com.edusync.edusync.backend.enrollment.dto.TeachingAssignmentResponse;
import com.edusync.edusync.backend.enrollment.service.TeachingAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TeachingAssignmentController {

    private final TeachingAssignmentService assignmentService;

    public TeachingAssignmentController(TeachingAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/groups/{groupId}/teaching-assignments")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<List<TeachingAssignmentResponse>> getAssignments(@PathVariable Long groupId) {
        return ResponseEntity.ok(assignmentService.getAssignments(groupId));
    }

    @PostMapping("/groups/{groupId}/teaching-assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeachingAssignmentResponse> assign(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateTeachingAssignmentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(assignmentService.assign(groupId, request));
    }

    @PatchMapping("/teaching-assignments/{assignmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeachingAssignmentResponse> end(
            @PathVariable Long assignmentId,
            @Valid @RequestBody EndPeriodRequest request
    ) {
        return ResponseEntity.ok(assignmentService.end(assignmentId, request));
    }
}
