package com.edusync.edusync.backend.registration.controller;

import com.edusync.edusync.backend.registration.dto.AvailableOfferingResponse;
import com.edusync.edusync.backend.registration.dto.RegistrationDecisionRequest;
import com.edusync.edusync.backend.registration.dto.RegistrationResponse;
import com.edusync.edusync.backend.registration.dto.SaveRegistrationRequest;
import com.edusync.edusync.backend.registration.dto.SubmitRegistrationRequest;
import com.edusync.edusync.backend.registration.service.RegistrationDecisionService;
import com.edusync.edusync.backend.registration.service.RegistrationQueryService;
import com.edusync.edusync.backend.registration.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class RegistrationController {

    private static final Set<String> STATUSES =
            Set.of("DRAFT", "SUBMITTED", "ADVISOR_APPROVED", "APPROVED", "RETURNED");

    private final RegistrationService registrationService;
    private final RegistrationQueryService queryService;
    private final RegistrationDecisionService decisionService;

    public RegistrationController(
            RegistrationService registrationService,
            RegistrationQueryService queryService,
            RegistrationDecisionService decisionService
    ) {
        this.registrationService = registrationService;
        this.queryService = queryService;
        this.decisionService = decisionService;
    }

    @GetMapping("/me/registration/available")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<AvailableOfferingResponse>> available(@RequestParam Long termId) {
        return ResponseEntity.ok(registrationService.available(termId));
    }

    @GetMapping("/me/registration")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<RegistrationResponse> getMine(@RequestParam Long termId) {
        return ResponseEntity.ok(registrationService.getMine(termId));
    }

    @PutMapping("/me/registration")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<RegistrationResponse> save(@Valid @RequestBody SaveRegistrationRequest request) {
        return ResponseEntity.ok(registrationService.save(request));
    }

    @PostMapping("/me/registration/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<RegistrationResponse> submit(@Valid @RequestBody SubmitRegistrationRequest request) {
        return ResponseEntity.ok(registrationService.submit(request.termId()));
    }

    @GetMapping("/advisor/registrations")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<RegistrationResponse>> advisorList(
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(queryService.advisorList(termId, parseStatus(status)));
    }

    @GetMapping("/admin/registrations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RegistrationResponse>> adminList(
            @RequestParam(required = false) Long termId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long programId
    ) {
        return ResponseEntity.ok(queryService.adminList(termId, parseStatus(status), programId));
    }

    @GetMapping("/registrations/{requestId}")
    @PreAuthorize("hasAnyRole('STUDENT','DOCTOR','ADMIN')")
    public ResponseEntity<RegistrationResponse> getOne(@PathVariable Long requestId) {
        return ResponseEntity.ok(queryService.getOne(requestId));
    }

    @PostMapping("/registrations/{requestId}/advisor-decision")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<RegistrationResponse> advisorDecision(
            @PathVariable Long requestId,
            @Valid @RequestBody RegistrationDecisionRequest request
    ) {
        return ResponseEntity.ok(decisionService.advisorDecide(requestId, request));
    }

    @PostMapping("/registrations/{requestId}/admin-decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistrationResponse> adminDecision(
            @PathVariable Long requestId,
            @Valid @RequestBody RegistrationDecisionRequest request
    ) {
        return ResponseEntity.ok(decisionService.adminDecide(requestId, request));
    }

    private String parseStatus(String status) {
        if (status == null) {
            return null;
        }

        String value = status.trim().toUpperCase(Locale.ROOT);

        if (!STATUSES.contains(value)) {
            throw com.edusync.edusync.backend.academic.exception.AcademicErrors.validation(
                    "status", "Status must be DRAFT, SUBMITTED, ADVISOR_APPROVED, APPROVED or RETURNED");
        }

        return value;
    }
}
