package com.edusync.edusync.backend.enrollment.service;

import com.edusync.edusync.backend.users.entity.User;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.CourseOfferingRepository;
import com.edusync.edusync.backend.enrollment.dto.CreateMembershipRequest;
import com.edusync.edusync.backend.enrollment.dto.EndPeriodRequest;
import com.edusync.edusync.backend.enrollment.dto.GroupMembershipResponse;
import com.edusync.edusync.backend.enrollment.dto.GroupRequest;
import com.edusync.edusync.backend.enrollment.dto.GroupResponse;
import com.edusync.edusync.backend.enrollment.dto.StudentRef;
import com.edusync.edusync.backend.enrollment.entity.Enrollment;
import com.edusync.edusync.backend.enrollment.entity.GroupMembership;
import com.edusync.edusync.backend.enrollment.entity.StudentGroup;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.enrollment.repository.EnrollmentRepository;
import com.edusync.edusync.backend.enrollment.repository.GroupMembershipRepository;
import com.edusync.edusync.backend.enrollment.repository.StudentGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private static final Set<String> GROUP_TYPES = Set.of("LECTURE", "PRACTICAL");
    private static final Set<String> DAYS = Set.of(
            "SATURDAY", "SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"
    );
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final StudentGroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseOfferingRepository offeringRepository;
    private final OfferingAccessService access;
    private final PeopleService people;

    public GroupService(
            StudentGroupRepository groupRepository,
            GroupMembershipRepository membershipRepository,
            EnrollmentRepository enrollmentRepository,
            CourseOfferingRepository offeringRepository,
            OfferingAccessService access,
            PeopleService people
    ) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.offeringRepository = offeringRepository;
        this.access = access;
        this.people = people;
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getGroups(Long offeringId) {
        offeringRepository.findById(offeringId).orElseThrow(AcademicErrors::offeringNotFound);
        access.requireAccess(offeringId);

        return toGroupResponses(groupRepository.findByOfferingIdOrderByGroupTypeAscLabelAsc(offeringId));
    }

    @Transactional
    public GroupResponse createGroup(Long offeringId, GroupRequest request) {
        offeringRepository.findByIdForUpdate(offeringId).orElseThrow(AcademicErrors::offeringNotFound);
        access.requireAccess(offeringId);

        String type = parseType(request.groupType());

        if (type == null) {
            throw AcademicErrors.validation("groupType", "Group type is required");
        }

        String label = cleanLabel(request.label());

        if (label == null) {
            throw AcademicErrors.validation("label", "Label is required");
        }

        LocalTime start = parseTime("startTime", request.startTime());
        LocalTime end = parseTime("endTime", request.endTime());
        checkTimes(start, end);

        if (groupRepository.existsByOfferingIdAndGroupTypeAndLabelIgnoreCase(offeringId, type, label)) {
            throw EnrollmentErrors.groupExists();
        }

        StudentGroup group = new StudentGroup();
        group.setOfferingId(offeringId);
        group.setGroupType(type);
        group.setLabel(label);
        group.setDayOfWeek(parseDay(request.dayOfWeek()));
        group.setStartTime(start);
        group.setEndTime(end);

        return toGroupResponses(List.of(groupRepository.save(group))).get(0);
    }

    @Transactional
    public GroupResponse updateGroup(Long groupId, GroupRequest request) {
        StudentGroup group = requireGroupForUpdate(groupId);

        String type = request.groupType() != null ? parseType(request.groupType()) : group.getGroupType();

        String label = group.getLabel();

        if (request.label() != null) {
            label = cleanLabel(request.label());

            if (label == null) {
                throw AcademicErrors.validation("label", "Label cannot be blank");
            }
        }

        LocalTime start = request.startTime() != null
                ? parseTime("startTime", request.startTime())
                : group.getStartTime();

        LocalTime end = request.endTime() != null
                ? parseTime("endTime", request.endTime())
                : group.getEndTime();

        checkTimes(start, end);

        if (groupRepository.existsByOfferingIdAndGroupTypeAndLabelIgnoreCaseAndGroupIdNot(
                group.getOfferingId(), type, label, groupId)) {
            throw EnrollmentErrors.groupExists();
        }

        if (request.dayOfWeek() != null) {
            group.setDayOfWeek(parseDay(request.dayOfWeek()));
        }

        if (!type.equals(group.getGroupType())
                && membershipRepository.existsOverlapForGroupTypeChange(groupId, type)) {
            throw EnrollmentErrors.alreadyMemberInAnotherGroup();
        }

        group.setGroupType(type);
        group.setLabel(label);
        group.setStartTime(start);
        group.setEndTime(end);

        return toGroupResponses(List.of(groupRepository.save(group))).get(0);
    }

    @Transactional(readOnly = true)
    public List<GroupMembershipResponse> getMembers(Long groupId, LocalDate at) {
        requireGroup(groupId);

        LocalDateTime moment = at == null ? LocalDateTime.now() : at.atStartOfDay();

        List<GroupMembership> rows = membershipRepository.findByGroupId(groupId)
                .stream()
                .filter(m -> !m.getStartsAt().isAfter(moment)
                        && (m.getEndsAt() == null || m.getEndsAt().isAfter(moment)))
                .toList();

        return toMembershipResponses(rows)
                .stream()
                .sorted(Comparator.comparing(
                        (GroupMembershipResponse r) -> r.student().universityId(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }

    @Transactional
    public GroupMembershipResponse addMember(Long groupId, CreateMembershipRequest request) {
        StudentGroup group = requireGroupForUpdate(groupId);

        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(request.enrollmentId())
                .orElseThrow(EnrollmentErrors::enrollmentNotFound);

        if (!group.getOfferingId().equals(enrollment.getOfferingId())) {
            throw EnrollmentErrors.wrongOffering();
        }
        if (!"ACTIVE".equals(enrollment.getStatus())) {
            throw AcademicErrors.validation(
                    "enrollmentId",
                    "Only ACTIVE enrollments can be added to a group"
            );
        }
        User student = people.requireUser(enrollment.getStudentUserId());
        if (!"ACTIVE".equals(student.getStatus())) {
            throw AcademicErrors.validation(
                    "enrollmentId",
                    "Cannot add a disabled student to a group"
            );
        }

        LocalDateTime startsAt = (
                request.startsAt() == null
                        ? LocalDate.now()
                        : request.startsAt()
        ).atStartOfDay();

        if (membershipRepository.existsOverlappingMembershipInSameGroup(
                enrollment.getEnrollmentId(),
                groupId,
                startsAt
        )) {
            throw EnrollmentErrors.alreadyMember();
        }

        if (membershipRepository.existsOverlappingMembershipInSameType(
                enrollment.getEnrollmentId(),
                groupId,
                group.getGroupType(),
                startsAt
        )) {
            throw EnrollmentErrors.alreadyMemberInAnotherGroup();
        }


        GroupMembership membership = new GroupMembership();
        membership.setEnrollmentId(enrollment.getEnrollmentId());
        membership.setGroupId(groupId);
        membership.setStartsAt(startsAt);

        return toMembershipResponses(List.of(membershipRepository.save(membership))).get(0);
    }

    /** The end date is exclusive: the student is a member until the day before it. */
    @Transactional
    public GroupMembershipResponse endMembership(
            Long membershipId,
            EndPeriodRequest request
    ) {
        Long groupId = membershipRepository.findGroupId(membershipId)
                .orElseThrow(EnrollmentErrors::membershipNotFound);
        StudentGroup group = requireGroupForUpdate(groupId);
        GroupMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(EnrollmentErrors::membershipNotFound);

        enrollmentRepository.findByIdForUpdate(membership.getEnrollmentId())
                .orElseThrow(EnrollmentErrors::enrollmentNotFound);

        LocalDateTime end = request.endsAt().atStartOfDay();

        if (!end.isAfter(membership.getStartsAt())) {
            throw AcademicErrors.validation(
                    "endsAt",
                    "End date must be after the start date"
            );
        }

        if (membershipRepository.existsOverlappingMembershipForUpdate(
                membership.getEnrollmentId(),
                membership.getMembershipId(),
                group.getGroupType(),
                membership.getStartsAt(),
                end
        )) {
            throw EnrollmentErrors.alreadyMemberInAnotherGroup();
        }

        membership.setEndsAt(end);

        return toMembershipResponses(
                List.of(membershipRepository.save(membership))
        ).get(0);
    }
    private StudentGroup requireGroupForUpdate(Long groupId) {
        Long offeringId = groupRepository.findOfferingId(groupId)
                .orElseThrow(EnrollmentErrors::groupNotFound);
        access.requireAccess(offeringId);
        offeringRepository.findByIdForUpdate(offeringId)
                .orElseThrow(AcademicErrors::offeringNotFound);
        return requireGroup(groupId);
    }

    private StudentGroup requireGroup(Long groupId) {
        StudentGroup group = groupRepository.findById(groupId)
                .orElseThrow(EnrollmentErrors::groupNotFound);

        access.requireAccess(group.getOfferingId());

        return group;
    }

    private String parseType(String value) {
        if (value == null) {
            return null;
        }

        String type = value.trim().toUpperCase(Locale.ROOT);

        if (!GROUP_TYPES.contains(type)) {
            throw AcademicErrors.validation("groupType", "Group type must be LECTURE or PRACTICAL");
        }

        return type;
    }

    private String parseDay(String value) {
        if (value == null) {
            return null;
        }

        String day = value.trim().toUpperCase(Locale.ROOT);

        if (!DAYS.contains(day)) {
            throw AcademicErrors.validation("dayOfWeek", "Day of week must be SATURDAY to FRIDAY");
        }

        return day;
    }

    private String cleanLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private LocalTime parseTime(String field, String value) {
        if (value == null) {
            return null;
        }

        try {
            return LocalTime.parse(value.trim(), TIME);
        } catch (DateTimeParseException e) {
            throw AcademicErrors.validation(field, "Time must look like 14:30");
        }
    }

    private void checkTimes(LocalTime start, LocalTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw AcademicErrors.validation("endTime", "End time must be after start time");
        }
    }

    private List<GroupResponse> toGroupResponses(List<StudentGroup> groups) {
        Map<Long, Integer> counts = new HashMap<>();

        if (!groups.isEmpty()) {
            membershipRepository.countCurrentByGroup(
                    groups.stream().map(StudentGroup::getGroupId).toList(),
                    LocalDateTime.now()
            ).forEach(row -> counts.put((Long) row[0], ((Long) row[1]).intValue()));
        }

        return groups.stream()
                .map(g -> new GroupResponse(
                        g.getGroupId(),
                        g.getOfferingId(),
                        g.getGroupType(),
                        g.getLabel(),
                        g.getDayOfWeek(),
                        g.getStartTime() == null ? null : g.getStartTime().format(TIME),
                        g.getEndTime() == null ? null : g.getEndTime().format(TIME),
                        counts.getOrDefault(g.getGroupId(), 0)
                ))
                .toList();
    }

    private List<GroupMembershipResponse> toMembershipResponses(List<GroupMembership> rows) {
        Map<Long, Enrollment> enrollments = enrollmentRepository
                .findAllById(rows.stream().map(GroupMembership::getEnrollmentId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Enrollment::getEnrollmentId, Function.identity()));

        Map<Long, StudentRef> students = people.students(
                enrollments.values().stream().map(Enrollment::getStudentUserId).collect(Collectors.toSet())
        );

        return rows.stream()
                .map(m -> new GroupMembershipResponse(
                        m.getMembershipId(),
                        m.getGroupId(),
                        m.getEnrollmentId(),
                        students.get(enrollments.get(m.getEnrollmentId()).getStudentUserId()),
                        m.getStartsAt().toLocalDate(),
                        m.getEndsAt() == null ? null : m.getEndsAt().toLocalDate()
                ))
                .toList();
    }
}
