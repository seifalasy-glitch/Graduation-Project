package com.edusync.edusync.backend.enrollment.service;

import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
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

    public OfferingAccessService(
            UserRepository userRepository,
            TeachingAssignmentRepository assignmentRepository
    ) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
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
