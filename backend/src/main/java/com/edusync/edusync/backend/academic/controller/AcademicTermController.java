package com.edusync.edusync.backend.academic.controller;

import com.edusync.edusync.backend.academic.dto.AcademicTermResponse;
import com.edusync.edusync.backend.academic.dto.CreateAcademicTermRequest;
import com.edusync.edusync.backend.academic.dto.UpdateAcademicTermRequest;
import com.edusync.edusync.backend.academic.service.AcademicTermService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/terms")
public class AcademicTermController {

    private final AcademicTermService termService;

    public AcademicTermController(AcademicTermService termService) {
        this.termService = termService;
    }

    @GetMapping
    public ResponseEntity<List<AcademicTermResponse>> getAllTerms() {
        return ResponseEntity.ok(termService.getAllTerms());
    }

    @PostMapping
    public ResponseEntity<AcademicTermResponse> createTerm(
            @Valid @RequestBody CreateAcademicTermRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(termService.createTerm(request));
    }

    @PatchMapping("/{termId}")
    public ResponseEntity<AcademicTermResponse> updateTerm(
            @PathVariable Long termId,
            @Valid @RequestBody UpdateAcademicTermRequest request
    ) {
        return ResponseEntity.ok(termService.updateTerm(termId, request));
    }
}
