package com.edusync.edusync.backend.registration.controller;

import com.edusync.edusync.backend.registration.dto.PlanCourseRequest;
import com.edusync.edusync.backend.registration.dto.PlanCourseResponse;
import com.edusync.edusync.backend.registration.service.PlanCourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PlanCourseController {

    private final PlanCourseService planService;

    public PlanCourseController(PlanCourseService planService) {
        this.planService = planService;
    }

    @GetMapping("/programs/{programId}/plan-courses")
    public ResponseEntity<List<PlanCourseResponse>> list(
            @PathVariable Long programId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String termType
    ) {
        return ResponseEntity.ok(planService.list(programId, level, termType));
    }

    @PostMapping("/programs/{programId}/plan-courses")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanCourseResponse> add(
            @PathVariable Long programId,
            @Valid @RequestBody PlanCourseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.add(programId, request));
    }

    @DeleteMapping("/plan-courses/{planCourseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable Long planCourseId) {
        planService.remove(planCourseId);
        return ResponseEntity.noContent().build();
    }
}
