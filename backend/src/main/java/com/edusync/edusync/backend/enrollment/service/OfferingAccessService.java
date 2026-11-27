package com.edusync.edusync.backend.enrollment.service;

import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.enrollment.repository.EnrollmentRepository;
import com.edusync.edusync.backend.enrollment.repository.TeachingAssignmentRepository;
import com.edusync.edusync.backend.users.entity.User;
import com.edusync.edusync.backend.users.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class OfferingAccessService {

    private final UserRepository userRepository;
    private final TeachingAssignmentRepository assignmentRepository;
    private final EnrollmentRepository enrollmentRepository;

    public OfferingAccessService(
            UserRepository userRepository,
            TeachingAssignmentRepository assignmentRepository,
            EnrollmentRepository enrollmentRepository
    ) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * Who may READ one offering (GET /offerings/{id}), from openapi.yaml:
     * admins always; doctors while teaching one of its groups; students enrolled in it.
     * A user with several roles passes if any of them allows it.
     */
    public boolean canRead(Long offeringId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (hasRole(authentication, "ROLE_ADMIN")) {
            return true;
        }

        Long userId = userRepository.findByEmail(authentication.getName())
                .map(User::getUserId)
                .orElse(null);

        if (userId == null) {
            return false;
        }

        if (hasRole(authentication, "ROLE_DOCTOR")
                && assignmentRepository.countTeachingOffering(userId, offeringId, LocalDateTime.now()) > 0) {
            return true;
        }

        return hasRole(authentication, "ROLE_STUDENT")
                && enrollmentRepository.existsByStudentUserIdAndOfferingId(userId, offeringId);
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    /** Admin: always. Doctor: only while teaching a group of this offering. */
    public void requireAccess(Long offeringId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean admin = authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        if (admin) {
            return;
        }

        Long userId = userRepository.findByEmail(authentication.getName())
                .map(User::getUserId)
                .orElseThrow(EnrollmentErrors::notYourCourse);

        if (assignmentRepository.countTeachingOffering(userId, offeringId, LocalDateTime.now()) == 0) {
            throw EnrollmentErrors.notYourCourse();
        }
    }
}
