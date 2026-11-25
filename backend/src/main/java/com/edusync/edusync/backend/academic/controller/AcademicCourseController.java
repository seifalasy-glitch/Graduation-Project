package com.edusync.edusync.backend.academic.controller;

import com.edusync.edusync.backend.academic.dto.AcademicCourseResponse;
import com.edusync.edusync.backend.academic.dto.CreateAcademicCourseRequest;
import com.edusync.edusync.backend.academic.dto.UpdateAcademicCourseRequest;
import com.edusync.edusync.backend.academic.service.AcademicCourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class AcademicCourseController {

    private final AcademicCourseService courseService;

    public AcademicCourseController(AcademicCourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ResponseEntity<List<AcademicCourseResponse>> getCourses(
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(courseService.getCourses(q));
    }

    @PostMapping
    public ResponseEntity<AcademicCourseResponse> createCourse(
            @Valid @RequestBody CreateAcademicCourseRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(courseService.createCourse(request));
    }

    @PatchMapping("/{courseId}")
    public ResponseEntity<AcademicCourseResponse> updateCourse(
            @PathVariable Long courseId,
            @Valid @RequestBody UpdateAcademicCourseRequest request
    ) {
        return ResponseEntity.ok(
                courseService.updateCourse(courseId, request)
        );
    }
}
