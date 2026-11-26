package com.edusync.edusync.backend.registration.service;

import com.edusync.edusync.backend.registration.dto.RegistrationResponse;
import com.edusync.edusync.backend.registration.entity.RegistrationRequest;
import com.edusync.edusync.backend.registration.entity.StudentProfile;
import com.edusync.edusync.backend.registration.exception.RegistrationErrors;
import com.edusync.edusync.backend.registration.repository.RegistrationRequestRepository;
import com.edusync.edusync.backend.registration.repository.StudentProfileRepository;
import com.edusync.edusync.backend.users.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RegistrationQueryService {

    private final RegistrationRequestRepository requestRepository;
    private final StudentProfileRepository profileRepository;
    private final CurrentUserService currentUser;
    private final RegistrationMapper mapper;

    public RegistrationQueryService(
            RegistrationRequestRepository requestRepository,
            StudentProfileRepository profileRepository,
            CurrentUserService currentUser,
            RegistrationMapper mapper
    ) {
        this.requestRepository = requestRepository;
        this.profileRepository = profileRepository;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public RegistrationResponse getOne(Long requestId) {
        RegistrationRequest request = requestRepository.findById(requestId)
                .orElseThrow(RegistrationErrors::requestNotFound);

        if (!canRead(request)) {
            throw RegistrationErrors.requestNotFound();
        }

        return mapper.toResponse(request);
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> advisorList(Long termId, String status) {
        User me = currentUser.get();

        List<RegistrationRequest> rows = requestRepository.findByAdvisorId(me.getUserId()).stream()
                .filter(r -> !RegistrationService.DRAFT.equals(r.getStatus()))
                .filter(r -> termId == null || termId.equals(r.getTermId()))
                .filter(r -> status == null || status.equals(r.getStatus()))
                .toList();

        return mapper.toResponses(sorted(rows));
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> adminList(Long termId, String status, Long programId) {
        List<RegistrationRequest> rows = requestRepository.findByStatusNot(RegistrationService.DRAFT).stream()
                .filter(r -> termId == null || termId.equals(r.getTermId()))
                .filter(r -> status == null || status.equals(r.getStatus()))
                .toList();

        if (programId != null) {
            Map<Long, StudentProfile> profiles = profileRepository
                    .findAllById(rows.stream().map(RegistrationRequest::getStudentUserId).collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.toMap(StudentProfile::getUserId, Function.identity()));

            rows = rows.stream()
                    .filter(r -> {
                        StudentProfile profile = profiles.get(r.getStudentUserId());
                        return profile != null && programId.equals(profile.getProgramId());
                    })
                    .toList();
        }

        return mapper.toResponses(sorted(rows));
    }

    private boolean canRead(RegistrationRequest request) {
        if (currentUser.isAdmin()) {
            return true;
        }

        User me = currentUser.get();

        if (me.getUserId().equals(request.getStudentUserId())) {
            return true;
        }

        return me.getUserId().equals(request.getAdvisorId())
                && !RegistrationService.DRAFT.equals(request.getStatus());
    }

    private List<RegistrationRequest> sorted(List<RegistrationRequest> rows) {
        return rows.stream()
                .sorted(Comparator
                        .comparing(RegistrationRequest::getSubmittedAt,
                                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                        .thenComparing(RegistrationRequest::getRequestId, Comparator.reverseOrder()))
                .toList();
    }
}
