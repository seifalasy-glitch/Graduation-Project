package com.edusync.edusync.backend.enrollment.service;

import com.edusync.edusync.backend.enrollment.dto.DoctorRef;
import com.edusync.edusync.backend.enrollment.dto.StudentRef;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.users.entity.User;
import com.edusync.edusync.backend.users.repository.UserRepository;
import com.edusync.edusync.backend.users.repository.UserRoleRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PeopleService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public PeopleService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(EnrollmentErrors::userNotFound);
    }

    public boolean hasRole(Long userId, String role) {
        return userRoleRepository.findByUserId(userId)
                .stream()
                .anyMatch(userRole -> role.equals(userRole.getRole()));
    }

    public Map<Long, StudentRef> students(Collection<Long> userIds) {
        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(
                        User::getUserId,
                        user -> new StudentRef(
                                user.getUserId(),
                                user.getUniversityId(),
                                user.getFullName(),
                                user.getFullNameAr()
                        )
                ));
    }

    public Map<Long, DoctorRef> doctors(Collection<Long> userIds) {
        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(
                        User::getUserId,
                        user -> new DoctorRef(user.getUserId(), user.getFullName())
                ));
    }
}
