package com.edusync.edusync.backend.auth.service;

import com.edusync.edusync.backend.auth.dto.ChangePasswordRequest;
import com.edusync.edusync.backend.auth.dto.ForgotPasswordRequest;
import com.edusync.edusync.backend.auth.dto.LoginRequest;
import com.edusync.edusync.backend.auth.dto.LoginResponse;
import com.edusync.edusync.backend.auth.dto.MeResponse;
import com.edusync.edusync.backend.auth.entity.PasswordResetToken;
import com.edusync.edusync.backend.auth.repository.PasswordResetTokenRepository;
import com.edusync.edusync.backend.common.ApiException;
import com.edusync.edusync.backend.users.entity.User;
import com.edusync.edusync.backend.users.entity.UserRole;
import com.edusync.edusync.backend.users.repository.UserRepository;
import com.edusync.edusync.backend.users.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.edusync.edusync.backend.auth.dto.ResetPasswordRequest;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationProvider authenticationProvider;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ResetTokenService resetTokenService;
    private final EmailService emailService;
    private final UserDetailsService userDetailsService;
    private final long expiresInSeconds;

    public AuthService(
            AuthenticationProvider authenticationProvider,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            PasswordResetTokenRepository passwordResetTokenRepository,
            ResetTokenService resetTokenService,
            EmailService emailService,
            UserDetailsService userDetailsService,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        this.authenticationProvider = authenticationProvider;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.resetTokenService = resetTokenService;
        this.emailService = emailService;
        this.userDetailsService = userDetailsService;
        this.expiresInSeconds = expirationMs / 1000;
    }

    public LoginResponse login(LoginRequest request) {

        Authentication authentication =
                authenticationProvider.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email(),
                                request.password()
                        )
                );

        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();

        User user = userRepository
                .findByEmail(userDetails.getUsername())
                .orElseThrow();

        String token = jwtService.generateToken(userDetails, user);

        return buildLoginResponse(token, user);
    }

    @Transactional
    public LoginResponse changePassword(
            String email,
            ChangePasswordRequest request
    ) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid credentials")
                );

        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPasswordHash())) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "WRONG_PASSWORD",
                    "The current password is wrong"
            );
        }

        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPasswordHash())) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SAME_AS_OLD",
                    "New password must be different from current password"
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(request.newPassword())
        );

        user.setMustChangePassword(false);

        // Old tokens stop working (tokenVersion + 1), so we send a fresh one back
        user.setTokenVersion(user.getTokenVersion() + 1);

        User savedUser = userRepository.saveAndFlush(user);

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(savedUser.getEmail());

        String token = jwtService.generateToken(userDetails, savedUser);

        return buildLoginResponse(token, savedUser);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        String tokenHash = resetTokenService.hashToken(
                request.token()
        );

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_OR_EXPIRED_TOKEN"
                ));

        LocalDateTime now = LocalDateTime.now();

        if (resetToken.getUsedAt() != null
                || !resetToken.getExpiresAt().isAfter(now)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_OR_EXPIRED_TOKEN"
            );
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_OR_EXPIRED_TOKEN"
                ));

        user.setPasswordHash(
                passwordEncoder.encode(request.newPassword())
        );

        user.setMustChangePassword(false);

        user.setTokenVersion(user.getTokenVersion() + 1);

        resetToken.setUsedAt(now);

        userRepository.save(user);
        passwordResetTokenRepository.save(resetToken);
    }

    @Transactional(readOnly = true)
    public MeResponse getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized"
            );
        }

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized"
                ));

        return toUserResponse(user);
    }


    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {

        userRepository.findByEmail(request.email())
                .ifPresent(user -> {

                    String token = resetTokenService.generateToken();

                    String tokenHash = resetTokenService.hashToken(token);

                    LocalDateTime now = LocalDateTime.now();

                    PasswordResetToken resetToken = new PasswordResetToken();

                    resetToken.setUserId(user.getUserId());
                    resetToken.setTokenHash(tokenHash);
                    resetToken.setCreatedAt(now);
                    resetToken.setExpiresAt(now.plusMinutes(30));

                    passwordResetTokenRepository.save(resetToken);

                    // The API contract says this endpoint always answers 202,
                    // so a failed email is logged instead of becoming a 500
                    try {
                        emailService.sendPasswordResetEmail(
                                user.getEmail(),
                                token
                        );
                    } catch (Exception e) {
                        log.warn("Could not send the reset email to {}: {}",
                                user.getEmail(), e.getMessage());
                    }
                });
    }

    private LoginResponse buildLoginResponse(String token, User user) {
        return new LoginResponse(
                token,
                "Bearer",
                expiresInSeconds,
                Boolean.TRUE.equals(user.getMustChangePassword()),
                toUserResponse(user)
        );
    }

    private MeResponse toUserResponse(User user) {
        List<String> roles = userRoleRepository
                .findByUserId(user.getUserId())
                .stream()
                .map(UserRole::getRole)
                .toList();

        return new MeResponse(
                user.getUserId(),
                user.getUniversityId(),
                user.getEmail(),
                user.getFullName(),
                user.getFullNameAr(),
                roles,
                user.getStatus(),
                user.getMustChangePassword(),
                user.getCreatedAt()
        );
    }
}
