package com.edusync.edusync.backend.academic.controller;

import com.edusync.edusync.backend.academic.dto.AcademicProgramResponse;
import com.edusync.edusync.backend.academic.dto.CreateAcademicProgramRequest;
import com.edusync.edusync.backend.academic.dto.UpdateAcademicProgramRequest;
import com.edusync.edusync.backend.academic.service.AcademicProgramService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/programs")
public class AcademicProgramController {

    private final AcademicProgramService programService;

    public AcademicProgramController(AcademicProgramService programService) {
        this.programService = programService;
    }

    @GetMapping
    public ResponseEntity<List<AcademicProgramResponse>> getPrograms(
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        return ResponseEntity.ok(programService.getPrograms(activeOnly));
    }

    @PostMapping
    public ResponseEntity<AcademicProgramResponse> createProgram(
            @Valid @RequestBody CreateAcademicProgramRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(programService.createProgram(request));
    }

    @PatchMapping("/{programId}")
    public ResponseEntity<AcademicProgramResponse> updateProgram(
            @PathVariable Long programId,
            @RequestBody UpdateAcademicProgramRequest request
    ) {
        return ResponseEntity.ok(
                programService.updateProgram(programId, request)
        );
    }
}
