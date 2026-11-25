package com.edusync.edusync.backend.enrollment.service;

import com.edusync.edusync.backend.academic.entity.AcademicProgram;
import com.edusync.edusync.backend.academic.entity.CourseOffering;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicProgramRepository;
import com.edusync.edusync.backend.academic.repository.CourseOfferingRepository;
import com.edusync.edusync.backend.enrollment.dto.CreateEnrollmentRequest;
import com.edusync.edusync.backend.enrollment.dto.EnrollmentResponse;
import com.edusync.edusync.backend.enrollment.dto.StudentRef;
import com.edusync.edusync.backend.enrollment.dto.UpdateEnrollmentRequest;
import com.edusync.edusync.backend.enrollment.entity.Enrollment;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.enrollment.repository.EnrollmentRepository;
import com.edusync.edusync.backend.users.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EnrollmentService {

    private static final String ACTIVE = "ACTIVE";
    private static final Set<String> STATUSES = Set.of(ACTIVE, "WITHDRAWN", "COMPLETED");

    private final EnrollmentRepository enrollmentRepository;
    private final CourseOfferingRepository offeringRepository;
    private final AcademicProgramRepository programRepository;
    private final PeopleService people;
    private final OfferingAccessService access;

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            CourseOfferingRepository offeringRepository,
            AcademicProgramRepository programRepository,
            PeopleService people,
            OfferingAccessService access
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.offeringRepository = offeringRepository;
        this.programRepository = programRepository;
        this.people = people;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getEnrollments(Long offeringId, Long programId, String status) {
        offeringRepository.findById(offeringId).orElseThrow(AcademicErrors::offeringNotFound);
        access.requireAccess(offeringId);

        String wantedStatus = status == null ? null : parseStatus(status);

        List<Enrollment> rows = enrollmentRepository.findByOfferingIdOrderByEnrollmentIdAsc(offeringId)
                .stream()
                .filter(e -> programId == null || programId.equals(e.getProgramId()))
                .filter(e -> wantedStatus == null || wantedStatus.equals(e.getStatus()))
                .toList();

        return toResponses(rows)
                .stream()
                .sorted(Comparator.comparing(
                        (EnrollmentResponse r) -> r.student().universityId(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }

    @Transactional
    public EnrollmentResponse enroll(Long offeringId, CreateEnrollmentRequest request) {
        CourseOffering offering = offeringRepository.findByIdForUpdate(offeringId)
                .orElseThrow(AcademicErrors::offeringNotFound);

        User student = people.requireUser(request.studentUserId());

        if (!people.hasRole(student.getUserId(), "STUDENT")) {
            throw EnrollmentErrors.notAStudent();
        }

        if (!ACTIVE.equals(student.getStatus())) {
            throw EnrollmentErrors.studentDisabled();
        }

        AcademicProgram program = programRepository.findById(request.programId())
                .orElseThrow(AcademicErrors::programNotFound);

        if (enrollmentRepository.existsByStudentUserIdAndOfferingId(student.getUserId(), offeringId)) {
            throw EnrollmentErrors.alreadyEnrolled();
        }

        ensureFreeSeat(offering);

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentUserId(student.getUserId());
        enrollment.setOfferingId(offeringId);
        enrollment.setProgramId(program.getProgramId());
        enrollment.setStatus(ACTIVE);
        enrollment.setSeatNo(cleanSeat(request.seatNo()));

        return toResponses(List.of(enrollmentRepository.save(enrollment))).get(0);
    }

    @Transactional
    public EnrollmentResponse update(Long enrollmentId, UpdateEnrollmentRequest request) {
        Long offeringId = enrollmentRepository.findOfferingId(enrollmentId)
                .orElseThrow(EnrollmentErrors::enrollmentNotFound);
        CourseOffering offering = offeringRepository.findByIdForUpdate(offeringId)
                .orElseThrow(AcademicErrors::offeringNotFound);
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(EnrollmentErrors::enrollmentNotFound);

        if (request.programId() != null) {
            AcademicProgram program = programRepository.findById(request.programId())
                    .orElseThrow(AcademicErrors::programNotFound);

            enrollment.setProgramId(program.getProgramId());
        }

        if (request.status() != null) {
            String status = parseStatus(request.status());

            if (ACTIVE.equals(status) && !ACTIVE.equals(enrollment.getStatus())) {

                User student = people.requireUser(enrollment.getStudentUserId());

                if (!ACTIVE.equals(student.getStatus())) {
                    throw EnrollmentErrors.studentDisabled();
                }

                ensureFreeSeat(offering);
            }

            enrollment.setStatus(status);
        }

        if (request.seatNo() != null) {
            enrollment.setSeatNo(cleanSeat(request.seatNo()));
        }

        return toResponses(List.of(enrollmentRepository.save(enrollment))).get(0);
    }

    private void ensureFreeSeat(CourseOffering offering) {
        if (offering.getCapacity() != null
                && enrollmentRepository.countByOfferingIdAndStatus(offering.getOfferingId(), ACTIVE)
                >= offering.getCapacity()) {
            throw EnrollmentErrors.offeringFull();
        }
    }

    private String parseStatus(String value) {
        String status = value.trim().toUpperCase(Locale.ROOT);

        if (!STATUSES.contains(status)) {
            throw AcademicErrors.validation("status", "Status must be ACTIVE, WITHDRAWN or COMPLETED");
        }

        return status;
    }

    private String cleanSeat(String seatNo) {
        if (seatNo == null || seatNo.isBlank()) {
            return null;
        }

        return seatNo.trim();
    }

    private List<EnrollmentResponse> toResponses(List<Enrollment> rows) {
        Map<Long, StudentRef> students = people.students(
                rows.stream().map(Enrollment::getStudentUserId).collect(Collectors.toSet())
        );

        Map<Long, String> programNames = programRepository
                .findAllById(rows.stream().map(Enrollment::getProgramId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AcademicProgram::getProgramId, AcademicProgram::getName));

        return rows.stream()
                .map(e -> new EnrollmentResponse(
                        e.getEnrollmentId(),
                        e.getOfferingId(),
                        students.get(e.getStudentUserId()),
                        e.getProgramId(),
                        programNames.get(e.getProgramId()),
                        e.getStatus(),
                        e.getSeatNo()
                ))
                .toList();
    }
}
