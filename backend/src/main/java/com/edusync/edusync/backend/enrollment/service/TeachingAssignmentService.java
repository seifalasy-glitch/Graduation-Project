package com.edusync.edusync.backend.enrollment.service;

import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.enrollment.dto.CreateTeachingAssignmentRequest;
import com.edusync.edusync.backend.enrollment.dto.DoctorRef;
import com.edusync.edusync.backend.enrollment.dto.EndPeriodRequest;
import com.edusync.edusync.backend.enrollment.dto.TeachingAssignmentResponse;
import com.edusync.edusync.backend.enrollment.entity.StudentGroup;
import com.edusync.edusync.backend.enrollment.entity.TeachingAssignment;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.enrollment.repository.StudentGroupRepository;
import com.edusync.edusync.backend.enrollment.repository.TeachingAssignmentRepository;
import com.edusync.edusync.backend.users.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeachingAssignmentService {

    private final TeachingAssignmentRepository assignmentRepository;
    private final StudentGroupRepository groupRepository;
    private final OfferingAccessService access;
    private final PeopleService people;

    public TeachingAssignmentService(
            TeachingAssignmentRepository assignmentRepository,
            StudentGroupRepository groupRepository,
            OfferingAccessService access,
            PeopleService people
    ) {
        this.assignmentRepository = assignmentRepository;
        this.groupRepository = groupRepository;
        this.access = access;
        this.people = people;
    }

    @Transactional(readOnly = true)
    public List<TeachingAssignmentResponse> getAssignments(Long groupId) {
        StudentGroup group = requireGroup(groupId);
        access.requireAccess(group.getOfferingId());

        return toResponses(assignmentRepository.findByGroupIdOrderByStartsAtDesc(groupId));
    }

    @Transactional
    public TeachingAssignmentResponse assign(Long groupId, CreateTeachingAssignmentRequest request) {
        requireGroup(groupId);

        User doctor = people.requireUser(request.doctorUserId());

        if (!people.hasRole(doctor.getUserId(), "DOCTOR")) {
            throw EnrollmentErrors.notADoctor();
        }

        if (!"ACTIVE".equals(doctor.getStatus())) {
            throw EnrollmentErrors.doctorDisabled();
        }

        LocalDateTime start = (request.startsAt() == null ? LocalDate.now() : request.startsAt()).atStartOfDay();

        if (assignmentRepository.countCurrent(groupId, doctor.getUserId(), start) > 0) {
            throw EnrollmentErrors.alreadyAssigned();
        }

        TeachingAssignment assignment = new TeachingAssignment();
        assignment.setGroupId(groupId);
        assignment.setDoctorUserId(doctor.getUserId());
        assignment.setStartsAt(start);

        return toResponses(List.of(assignmentRepository.save(assignment))).get(0);
    }

    @Transactional
    public TeachingAssignmentResponse end(Long assignmentId, EndPeriodRequest request) {
        TeachingAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(EnrollmentErrors::assignmentNotFound);

        LocalDateTime end = request.endsAt().atStartOfDay();

        if (!end.isAfter(assignment.getStartsAt())) {
            throw AcademicErrors.validation("endsAt", "End date must be after the start date");
        }

        assignment.setEndsAt(end);

        return toResponses(List.of(assignmentRepository.save(assignment))).get(0);
    }

    private StudentGroup requireGroup(Long groupId) {
        return groupRepository.findById(groupId).orElseThrow(EnrollmentErrors::groupNotFound);
    }

    private List<TeachingAssignmentResponse> toResponses(List<TeachingAssignment> rows) {
        Map<Long, DoctorRef> doctors = people.doctors(
                rows.stream().map(TeachingAssignment::getDoctorUserId).collect(Collectors.toSet())
        );

        return rows.stream()
                .map(a -> new TeachingAssignmentResponse(
                        a.getAssignmentId(),
                        a.getGroupId(),
                        doctors.get(a.getDoctorUserId()),
                        a.getStartsAt().toLocalDate(),
                        a.getEndsAt() == null ? null : a.getEndsAt().toLocalDate()
                ))
                .toList();
    }
}
