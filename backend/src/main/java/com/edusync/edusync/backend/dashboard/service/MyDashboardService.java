package com.edusync.edusync.backend.dashboard.service;

import com.edusync.edusync.backend.academic.entity.AcademicTerm;
import com.edusync.edusync.backend.academic.repository.AcademicTermRepository;
import com.edusync.edusync.backend.academic.service.CurrentTermService;
import com.edusync.edusync.backend.dashboard.dto.MyEnrollmentResponse;
import com.edusync.edusync.backend.dashboard.dto.TeachingGroupResponse;
import com.edusync.edusync.backend.dashboard.repository.DashboardQueries;
import com.edusync.edusync.backend.dashboard.repository.DashboardQueries.EnrollmentRow;
import com.edusync.edusync.backend.dashboard.repository.DashboardQueries.GroupRow;
import com.edusync.edusync.backend.dashboard.repository.DashboardQueries.TeachingRow;
import com.edusync.edusync.backend.enrollment.dto.GroupResponse;
import com.edusync.edusync.backend.registration.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Home screens: GET /me/enrollments (student) and GET /me/teaching-groups (doctor).
 *
 * Which term: ?termId= if sent, else the current term (CurrentTermService).
 * No current term, or an unknown termId -> empty list (the contract lists only 200/401).
 *
 * Which groups / teaching count as "mine" (rules approved 27 Nov 2026):
 *  - the current term: memberships and assignments active right now;
 *  - any other term: the ones active at some point between its start and end dates.
 *
 * attendancePercent / avgAttendancePercent stay null until the attendance module exists.
 */
@Service
public class MyDashboardService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    // Used when a non-current term has no start/end date: look at all time
    private static final LocalDateTime EARLIEST = LocalDate.of(1, 1, 1).atStartOfDay();
    private static final LocalDateTime LATEST = LocalDate.of(9999, 12, 31).atStartOfDay();

    private final CurrentUserService currentUser;
    private final CurrentTermService currentTerm;
    private final AcademicTermRepository termRepository;
    private final DashboardQueries queries;

    public MyDashboardService(
            CurrentUserService currentUser,
            CurrentTermService currentTerm,
            AcademicTermRepository termRepository,
            DashboardQueries queries
    ) {
        this.currentUser = currentUser;
        this.currentTerm = currentTerm;
        this.termRepository = termRepository;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public List<MyEnrollmentResponse> myEnrollments(Long termId) {
        Optional<AcademicTerm> term = chooseTerm(termId);
        if (term.isEmpty()) {
            return List.of();
        }

        Window window = Window.of(term.get());
        List<EnrollmentRow> rows = queries.studentEnrollments(currentUser.get().getUserId(), term.get().getTermId());
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<Long> offeringIds = rows.stream().map(EnrollmentRow::offeringId).collect(Collectors.toSet());
        Set<Long> enrollmentIds = rows.stream().map(EnrollmentRow::enrollmentId).collect(Collectors.toSet());

        Map<Long, List<String>> doctors = queries.doctorNames(offeringIds, window.from, window.to);
        List<GroupRow> groupRows = queries.groupsOfEnrollments(enrollmentIds, window.from, window.to);
        Map<Long, Integer> members = groupRows.isEmpty() ? Map.of()
                : queries.memberCounts(groupRows.stream().map(GroupRow::groupId).collect(Collectors.toSet()),
                window.from, window.to);

        Map<Long, List<GroupResponse>> groupsByEnrollment = new HashMap<>();
        for (GroupRow g : groupRows) {
            groupsByEnrollment.computeIfAbsent(g.ownerId(), k -> new ArrayList<>()).add(toGroup(g, members));
        }

        return rows.stream()
                .map(r -> new MyEnrollmentResponse(
                        r.enrollmentId(),
                        r.offeringId(),
                        r.courseCode(),
                        r.courseTitle(),
                        CurrentTermService.label(r.termType(), r.academicYear()),
                        r.programName(),
                        doctors.getOrDefault(r.offeringId(), List.of()),
                        null,
                        groupsByEnrollment.getOrDefault(r.enrollmentId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeachingGroupResponse> myTeachingGroups(Long termId) {
        Optional<AcademicTerm> term = chooseTerm(termId);
        if (term.isEmpty()) {
            return List.of();
        }

        Window window = Window.of(term.get());
        List<TeachingRow> rows = queries.teachingGroups(
                currentUser.get().getUserId(), term.get().getTermId(), window.from, window.to);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> members = queries.memberCounts(
                rows.stream().map(r -> r.group().groupId()).collect(Collectors.toSet()),
                window.from, window.to);

        return rows.stream()
                .map(r -> new TeachingGroupResponse(
                        r.assignmentId(),
                        toGroup(r.group(), members),
                        r.group().offeringId(),
                        r.courseCode(),
                        r.courseTitle(),
                        CurrentTermService.label(r.termType(), r.academicYear()),
                        r.enrolledCount(),
                        r.capacity(),
                        null))
                .toList();
    }

    private Optional<AcademicTerm> chooseTerm(Long termId) {
        return termId == null ? currentTerm.current() : termRepository.findById(termId);
    }

    private static GroupResponse toGroup(GroupRow g, Map<Long, Integer> members) {
        return new GroupResponse(
                g.groupId(),
                g.offeringId(),
                g.groupType(),
                g.label(),
                g.dayOfWeek(),
                g.startTime() == null ? null : g.startTime().format(TIME),
                g.endTime() == null ? null : g.endTime().format(TIME),
                members.getOrDefault(g.groupId(), 0));
    }

    /** The time span whose memberships / assignments count, see the class comment. */
    private record Window(LocalDateTime from, LocalDateTime to) {
        static Window of(AcademicTerm term) {
            if (CurrentTermService.isCurrent(term)) {
                LocalDateTime now = LocalDateTime.now();
                return new Window(now, now);
            }
            return new Window(
                    term.getStartDate() == null ? EARLIEST : term.getStartDate().atStartOfDay(),
                    term.getEndDate() == null ? LATEST : term.getEndDate().atTime(LocalTime.MAX));
        }
    }
}
