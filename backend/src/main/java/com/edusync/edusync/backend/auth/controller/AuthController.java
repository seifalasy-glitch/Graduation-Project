package com.edusync.edusync.backend.auth.controller;

import com.edusync.edusync.backend.auth.dto.ChangePasswordRequest;
import com.edusync.edusync.backend.auth.dto.LoginRequest;
import com.edusync.edusync.backend.auth.dto.LoginResponse;
import com.edusync.edusync.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.edusync.edusync.backend.auth.dto.MeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import com.edusync.edusync.backend.auth.dto.ForgotPasswordRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.edusync.edusync.backend.auth.dto.ResetPasswordRequest;
import com.edusync.edusync.backend.auth.ratelimit.ForgotPasswordRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ForgotPasswordRateLimiter forgotPasswordRateLimiter;

    public AuthController(AuthService authService, ForgotPasswordRateLimiter forgotPasswordRateLimiter) {
        this.authService = authService;
        this.forgotPasswordRateLimiter=forgotPasswordRateLimiter;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(
                authentication.getName(),
                request
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getCurrentUser() {
        return ResponseEntity.ok(
                authService.getCurrentUser()
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = httpRequest.getRemoteAddr();
        if (!forgotPasswordRateLimiter.isAllowed(ipAddress)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED"
            );
        }
        authService.forgotPassword(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);

        return ResponseEntity.noContent().build();
    }

}
