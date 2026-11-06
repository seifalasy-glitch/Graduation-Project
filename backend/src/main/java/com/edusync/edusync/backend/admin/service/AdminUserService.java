package com.edusync.edusync.backend.admin.service;

import com.edusync.edusync.backend.admin.dto.CreateUserRequest;
import com.edusync.edusync.backend.admin.dto.CreateUserResponse;
import com.edusync.edusync.backend.admin.dto.PageMeta;
import com.edusync.edusync.backend.admin.dto.UpdateUserRequest;
import com.edusync.edusync.backend.admin.dto.UserPageResponse;
import com.edusync.edusync.backend.admin.dto.UserResponse;
import com.edusync.edusync.backend.admin.exception.DuplicateUserException;
import com.edusync.edusync.backend.users.entity.User;
import com.edusync.edusync.backend.users.entity.UserRole;
import com.edusync.edusync.backend.users.repository.UserRepository;
import com.edusync.edusync.backend.users.repository.UserRoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.edusync.edusync.backend.admin.dto.ResetPasswordResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Service
public class AdminUserService {

    private static final List<String> ALLOWED_ROLES =
            List.of("STUDENT", "DOCTOR", "ADMIN");

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;

    public AdminUserService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            TemporaryPasswordGenerator passwordGenerator
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        boolean securityChanged = false;

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        boolean updatingSelf = authentication != null
                && user.getEmail().equalsIgnoreCase(authentication.getName());
        if (updatingSelf) {
            if (request.status() != null
                    && "DISABLED".equals(request.status())) {
                throw new IllegalArgumentException(
                        "You cannot disable your own account"
                );
            }
            if (request.roles() != null
                    && !request.roles().contains("ADMIN")) {
                throw new IllegalArgumentException(
                        "You cannot remove your own ADMIN role"
                );
            }
        }
        if (request.email() != null) {

            String email = request.email()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (email.isBlank()) {
                throw new IllegalArgumentException("Invalid email");
            }

            if (!email.equals(user.getEmail())) {

                if (userRepository.findByEmail(email).isPresent()) {
                    throw new DuplicateUserException(
                            "Email already exists"
                    );
                }

                user.setEmail(email);
                securityChanged = true;
            }
        }

        if (request.fullName() != null) {

            String fullName = request.fullName().trim();

            if (fullName.isBlank()) {
                throw new IllegalArgumentException("Invalid full name");
            }

            user.setFullName(fullName);
        }

        if (request.fullNameAr() != null) {
            user.setFullNameAr(request.fullNameAr().trim());
        }

        if (request.status() != null) {

            if (!List.of("ACTIVE", "DISABLED")
                    .contains(request.status())) {
                throw new IllegalArgumentException("Invalid status");
            }

            if (!request.status().equals(user.getStatus())) {
                user.setStatus(request.status());
                securityChanged = true;
            }
        }

        if (request.roles() != null) {

            List<String> roles = request.roles()
                    .stream()
                    .map(String::trim)
                    .distinct()
                    .toList();

            if (roles.isEmpty() ||
                    roles.stream().anyMatch(
                            role -> !ALLOWED_ROLES.contains(role)
                    )) {
                throw new IllegalArgumentException("Invalid roles");
            }

            List<UserRole> existingUserRoles =
                    userRoleRepository.findByUserId(userId);

            List<String> currentRoles = existingUserRoles
                    .stream()
                    .map(UserRole::getRole)
                    .toList();

            if (!new HashSet<>(currentRoles)
                    .equals(new HashSet<>(roles))) {

                securityChanged = true;

                userRoleRepository.deleteAll(existingUserRoles);
                userRoleRepository.flush();

                for (String role : roles) {

                    UserRole userRole = new UserRole();
                    userRole.setUserId(userId);
                    userRole.setRole(role);

                    userRoleRepository.save(userRole);
                }

                userRoleRepository.flush();
            }
        }

        if (securityChanged) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        User savedUser = userRepository.saveAndFlush(user);

        return toUserResponse(savedUser);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        String temporaryPassword = passwordGenerator.generate();

        user.setPasswordHash(
                passwordEncoder.encode(temporaryPassword)
        );

        user.setMustChangePassword(true);

        user.setTokenVersion(user.getTokenVersion() + 1);

        userRepository.saveAndFlush(user);

        return new ResetPasswordResponse(
                user.getUserId(),
                temporaryPassword
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserPageResponse getUsers(
            String role,
            String status,
            String q,
            int page,
            int size
    ) {

        if (page < 0) {
            throw new IllegalArgumentException("Invalid page number");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid page size");
        }

        if (role != null && !ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("Invalid role");
        }

        if (status != null &&
                !List.of("ACTIVE", "DISABLED").contains(status)) {
            throw new IllegalArgumentException("Invalid status");
        }

        String search = (q == null || q.isBlank())
                ? ""
                : q.trim();

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "userId")
        );

        Page<User> users = userRepository.searchUsers(
                role == null ? "" : role,
                status == null ? "" : status,
                search,
                pageable
        );

        List<UserResponse> items = users.getContent()
                .stream()
                .map(this::toUserResponse)
                .toList();

        PageMeta pageMeta = new PageMeta(
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages()
        );

        return new UserPageResponse(items, pageMeta);
    }

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {

        String universityId = request.universityId().trim();

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        String fullName = request.fullName().trim();

        String fullNameAr = request.fullNameAr() == null
                ? null
                : request.fullNameAr().trim();

        List<String> roles = request.roles()
                .stream()
                .map(String::trim)
                .distinct()
                .toList();

        if (roles.isEmpty() ||
                roles.stream().anyMatch(
                        role -> !ALLOWED_ROLES.contains(role)
                )) {
            throw new IllegalArgumentException("Invalid roles");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateUserException(
                    "Email already exists"
            );
        }

        if (userRepository.findByUniversityId(universityId).isPresent()) {
            throw new DuplicateUserException(
                    "University ID already exists"
            );
        }

        String temporaryPassword = passwordGenerator.generate();

        User user = new User();

        user.setUniversityId(universityId);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setFullNameAr(fullNameAr);

        user.setPasswordHash(
                passwordEncoder.encode(temporaryPassword)
        );

        user.setStatus("ACTIVE");
        user.setMustChangePassword(true);
        user.setTokenVersion(0);
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.saveAndFlush(user);

        for (String role : roles) {

            UserRole userRole = new UserRole();

            userRole.setUserId(savedUser.getUserId());
            userRole.setRole(role);

            userRoleRepository.save(userRole);
        }

        userRoleRepository.flush();

        return new CreateUserResponse(
                toUserResponse(savedUser),
                temporaryPassword
        );
    }

    private UserResponse toUserResponse(User user) {

        List<String> roles = userRoleRepository
                .findByUserId(user.getUserId())
                .stream()
                .map(UserRole::getRole)
                .toList();

        return new UserResponse(
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
