package com.edusync.edusync.backend.admin.controller;

import com.edusync.edusync.backend.admin.dto.CreateUserRequest;
import com.edusync.edusync.backend.admin.dto.CreateUserResponse;
import com.edusync.edusync.backend.admin.dto.UserPageResponse;
import com.edusync.edusync.backend.admin.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.edusync.edusync.backend.admin.dto.ResetPasswordResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.edusync.edusync.backend.admin.dto.UserResponse;
import org.springframework.web.bind.annotation.PathVariable;
import com.edusync.edusync.backend.admin.dto.UpdateUserRequest;
import org.springframework.web.bind.annotation.PatchMapping;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                adminUserService.getUserById(userId)
        );
    }

    @GetMapping
    public ResponseEntity<UserPageResponse> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UserPageResponse response = adminUserService.getUsers(
                role,
                status,
                q,
                page,
                size
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        CreateUserResponse response = adminUserService.createUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(
                adminUserService.updateUser(userId, request)
        );
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                adminUserService.resetPassword(userId)
        );
    }

}
