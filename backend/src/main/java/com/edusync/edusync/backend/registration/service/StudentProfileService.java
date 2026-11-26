package com.edusync.edusync.backend.registration.service;

import com.edusync.edusync.backend.academic.entity.AcademicProgram;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicProgramRepository;
import com.edusync.edusync.backend.enrollment.dto.DoctorRef;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.enrollment.service.PeopleService;
import com.edusync.edusync.backend.registration.dto.StudentProfileRequest;
import com.edusync.edusync.backend.registration.dto.StudentProfileResponse;
import com.edusync.edusync.backend.registration.entity.StudentProfile;
import com.edusync.edusync.backend.registration.exception.RegistrationErrors;
import com.edusync.edusync.backend.registration.repository.StudentProfileRepository;
import com.edusync.edusync.backend.users.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProfileService {

    private final StudentProfileRepository profileRepository;
    private final AcademicProgramRepository programRepository;
    private final PeopleService people;

    public StudentProfileService(
            StudentProfileRepository profileRepository,
            AcademicProgramRepository programRepository,
            PeopleService people
    ) {
        this.profileRepository = profileRepository;
        this.programRepository = programRepository;
        this.people = people;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse get(Long userId) {
        people.requireUser(userId);

        return toResponse(profileRepository.findById(userId)
                .orElseThrow(RegistrationErrors::profileNotFound));
    }

    @Transactional
    public StudentProfileResponse save(Long userId, StudentProfileRequest request) {
        User student = people.requireUser(userId);

        if (!people.hasRole(student.getUserId(), "STUDENT")) {
            throw EnrollmentErrors.notAStudent();
        }

        AcademicProgram program = programRepository.findById(request.programId())
                .orElseThrow(AcademicErrors::programNotFound);

        if (request.advisorId() != null) {
            User advisor = people.requireUser(request.advisorId());

            if (!people.hasRole(advisor.getUserId(), "DOCTOR")) {
                throw EnrollmentErrors.notADoctor();
            }

            if (!"ACTIVE".equals(advisor.getStatus())) {
                throw EnrollmentErrors.doctorDisabled();
            }
        }

        StudentProfile profile = profileRepository.findById(userId).orElseGet(() -> {
            StudentProfile created = new StudentProfile();
            created.setUserId(userId);
            return created;
        });

        profile.setProgramId(program.getProgramId());
        profile.setLevel(request.level());
        profile.setMaxCreditHours(request.maxCreditHours());
        profile.setAdvisorId(request.advisorId());

        return toResponse(profileRepository.save(profile));
    }

    private StudentProfileResponse toResponse(StudentProfile profile) {
        String programName = profile.getProgramId() == null
                ? null
                : programRepository.findById(profile.getProgramId()).map(AcademicProgram::getName).orElse(null);

        DoctorRef advisor = profile.getAdvisorId() == null
                ? null
                : people.doctors(java.util.List.of(profile.getAdvisorId())).get(profile.getAdvisorId());

        return new StudentProfileResponse(
                profile.getUserId(),
                profile.getProgramId(),
                programName,
                profile.getLevel(),
                profile.getMaxCreditHours(),
                advisor
        );
    }
}
