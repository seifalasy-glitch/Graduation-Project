package com.edusync.edusync.backend.registration.controller;

import com.edusync.edusync.backend.registration.dto.StudentProfileRequest;
import com.edusync.edusync.backend.registration.dto.StudentProfileResponse;
import com.edusync.edusync.backend.registration.service.StudentProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/students/{userId}/profile")
@PreAuthorize("hasRole('ADMIN')")
public class StudentProfileController {

    private final StudentProfileService profileService;

    public StudentProfileController(StudentProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<StudentProfileResponse> get(@PathVariable Long userId) {
        return ResponseEntity.ok(profileService.get(userId));
    }

    @PutMapping
    public ResponseEntity<StudentProfileResponse> save(
            @PathVariable Long userId,
            @Valid @RequestBody StudentProfileRequest request
    ) {
        return ResponseEntity.ok(profileService.save(userId, request));
    }
}
