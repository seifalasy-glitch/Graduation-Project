package com.edusync.edusync.backend.academic.controller;

import com.edusync.edusync.backend.academic.dto.CourseOfferingResponse;
import com.edusync.edusync.backend.academic.dto.CreateCourseOfferingRequest;
import com.edusync.edusync.backend.academic.dto.UpdateCourseOfferingRequest;
import com.edusync.edusync.backend.academic.service.CourseOfferingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/offerings")
public class CourseOfferingController {

    private final CourseOfferingService offeringService;

    public CourseOfferingController(CourseOfferingService offeringService) {
        this.offeringService = offeringService;
    }

    @GetMapping
    public ResponseEntity<List<CourseOfferingResponse>> getOfferings(
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) Long courseId
    ) {
        return ResponseEntity.ok(
                offeringService.getOfferings(termId, courseId)
        );
    }

    @GetMapping("/{offeringId}")
    public ResponseEntity<CourseOfferingResponse> getOfferingById(
            @PathVariable Long offeringId
    ) {
        return ResponseEntity.ok(
                offeringService.getOfferingById(offeringId)
        );
    }

    @PostMapping
    public ResponseEntity<CourseOfferingResponse> createOffering(
            @Valid @RequestBody CreateCourseOfferingRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(offeringService.createOffering(request));
    }

    @PatchMapping("/{offeringId}")
    public ResponseEntity<CourseOfferingResponse> updateOffering(
            @PathVariable Long offeringId,
            @Valid @RequestBody UpdateCourseOfferingRequest request
    ) {
        return ResponseEntity.ok(
                offeringService.updateOffering(offeringId, request)
        );
    }
}
