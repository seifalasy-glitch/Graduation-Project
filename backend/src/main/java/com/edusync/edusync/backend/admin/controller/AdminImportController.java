package com.edusync.edusync.backend.admin.controller;

import com.edusync.edusync.backend.admin.dto.BulkImportResult;
import com.edusync.edusync.backend.admin.service.UserImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/imports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminImportController {

    private static final MediaType EXCEL = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final UserImportService userImportService;

    public AdminImportController(UserImportService userImportService) {
        this.userImportService = userImportService;
    }

    @GetMapping("/users/template")
    public ResponseEntity<byte[]> downloadUsersTemplate() {
        return ResponseEntity.ok()
                .contentType(EXCEL)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"users-template.xlsx\"")
                .body(userImportService.buildTemplate());
    }

    @PostMapping(value = "/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkImportResult> importUsers(
            @RequestParam("file") MultipartFile file,
            @RequestParam("mode") String mode
    ) {
        return ResponseEntity.ok(userImportService.importUsers(file, mode));
    }
}