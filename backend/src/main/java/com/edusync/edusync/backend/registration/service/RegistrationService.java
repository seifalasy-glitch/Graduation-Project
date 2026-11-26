package com.edusync.edusync.backend.registration.service;

import com.edusync.edusync.backend.academic.entity.AcademicTerm;
import com.edusync.edusync.backend.academic.entity.CourseOffering;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicTermRepository;
import com.edusync.edusync.backend.enrollment.entity.StudentGroup;
import com.edusync.edusync.backend.enrollment.entity.TeachingAssignment;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.enrollment.service.PeopleService;
import com.edusync.edusync.backend.registration.dto.AvailableOfferingResponse;
import com.edusync.edusync.backend.registration.dto.RegistrationResponse;
import com.edusync.edusync.backend.registration.dto.SaveRegistrationRequest;
import com.edusync.edusync.backend.registration.dto.ScheduleSlot;
import com.edusync.edusync.backend.registration.entity.ProgramPlanCourse;
import com.edusync.edusync.backend.registration.entity.RegistrationItem;
import com.edusync.edusync.backend.registration.entity.RegistrationRequest;
import com.edusync.edusync.backend.registration.entity.StudentProfile;
import com.edusync.edusync.backend.registration.exception.RegistrationErrors;
import com.edusync.edusync.backend.registration.repository.ProgramPlanCourseRepository;
import com.edusync.edusync.backend.registration.repository.RegistrationItemRepository;
import com.edusync.edusync.backend.registration.repository.RegistrationOfferingRepository;
import com.edusync.edusync.backend.registration.repository.RegistrationRequestRepository;
import com.edusync.edusync.backend.registration.repository.RegistrationScheduleRepository;
import com.edusync.edusync.backend.registration.repository.RegistrationUserLockRepository;
import com.edusync.edusync.backend.registration.repository.RegistrationSeatRepository;
import com.edusync.edusync.backend.registration.repository.RegistrationTeachingRepository;
import com.edusync.edusync.backend.registration.repository.StudentProfileRepository;
import com.edusync.edusync.backend.users.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RegistrationService {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String RETURNED = "RETURNED";
    public static final String PENDING = "PENDING";

    private static final int UNLIMITED_SEATS = 9999;
    private static final int CLASH_SEARCH_LIMIT = 1_000_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final RegistrationRequestRepository requestRepository;
    private final RegistrationItemRepository itemRepository;
    private final StudentProfileRepository profileRepository;
    private final ProgramPlanCourseRepository planRepository;
    private final RegistrationOfferingRepository offeringRepository;
    private final RegistrationSeatRepository seatRepository;
    private final RegistrationScheduleRepository scheduleRepository;
    private final RegistrationTeachingRepository teachingRepository;
    private final AcademicTermRepository termRepository;
    private final CurrentUserService currentUser;
    private final PeopleService people;
    private final RegistrationMapper mapper;
    private final RegistrationNotifier notifier;
    private final RegistrationUserLockRepository userLockRepository;

    public RegistrationService(
            RegistrationRequestRepository requestRepository,
            RegistrationItemRepository itemRepository,
            StudentProfileRepository profileRepository,
            ProgramPlanCourseRepository planRepository,
            RegistrationOfferingRepository offeringRepository,
            RegistrationSeatRepository seatRepository,
            RegistrationScheduleRepository scheduleRepository,
            RegistrationTeachingRepository teachingRepository,
            AcademicTermRepository termRepository,
            CurrentUserService currentUser,
            PeopleService people,
            RegistrationMapper mapper,
            RegistrationNotifier notifier,
            RegistrationUserLockRepository userLockRepository
    ) {
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.profileRepository = profileRepository;
        this.planRepository = planRepository;
        this.offeringRepository = offeringRepository;
        this.seatRepository = seatRepository;
        this.scheduleRepository = scheduleRepository;
        this.teachingRepository = teachingRepository;
        this.termRepository = termRepository;
        this.currentUser = currentUser;
        this.people = people;
        this.mapper = mapper;
        this.notifier = notifier;
        this.userLockRepository = userLockRepository;
    }

    @Transactional(readOnly = true)
    public List<AvailableOfferingResponse> available(Long termId) {
        User student = currentUser.get();
        AcademicTerm term = requireTerm(termId);
        StudentProfile profile = requireCompleteProfile(student.getUserId());

        Set<Long> planCourseIds = planCourseIds(profile, term);

        List<CourseOffering> candidates = offeringRepository.findByTermWithCourse(termId).stream()
                .filter(o -> planCourseIds.contains(o.getCourse().getCourseId()))
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> ids = candidates.stream().map(CourseOffering::getOfferingId).toList();

        Set<Long> enrolled = new LinkedHashSet<>(seatRepository.findEnrolledOfferingIds(student.getUserId(), ids));
        Set<Long> enrolledCourses = new LinkedHashSet<>(
                seatRepository.findActiveCourseIdsInTerm(student.getUserId(), termId)
        );

        Set<Long> chosen = requestRepository.findByStudentUserIdAndTermId(student.getUserId(), termId)
                .map(r -> itemRepository.findByRequestIdOrderByItemIdAsc(r.getRequestId()).stream()
                        .map(RegistrationItem::getOfferingId)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());

        Map<Long, Long> active = activeCounts(ids);
        Map<Long, List<StudentGroup>> groups = groupsByOffering(ids);
        Map<Long, List<String>> doctors = doctorNames(groups);

        return candidates.stream()
                .filter(o -> !enrolled.contains(o.getOfferingId())
                        && !enrolledCourses.contains(o.getCourse().getCourseId()))
                .sorted(Comparator.comparing((CourseOffering o) -> o.getCourse().getCourseCode())
                        .thenComparing(o -> o.getSection() == null ? "" : o.getSection()))
                .map(o -> new AvailableOfferingResponse(
                        o.getOfferingId(),
                        PlanCourseService.courseResponse(o.getCourse()),
                        o.getSection(),
                        o.getCapacity(),
                        seatsLeft(o, active),
                        doctors.getOrDefault(o.getOfferingId(), List.of()),
                        groups.getOrDefault(o.getOfferingId(), List.of()).stream()
                                .map(this::toSlot)
                                .toList(),
                        chosen.contains(o.getOfferingId())
                ))
                .toList();
    }

    @Transactional
    public RegistrationResponse getMine(Long termId) {
        User student = currentUser.get();
        AcademicTerm term = requireTerm(termId);

        userLockRepository.findByIdForUpdate(student.getUserId());

        return mapper.toResponse(getOrCreate(student.getUserId(), term.getTermId()));
    }

    @Transactional
    public RegistrationResponse save(SaveRegistrationRequest body) {
        User student = currentUser.get();
        AcademicTerm term = requireTerm(body.termId());
        StudentProfile profile = requireCompleteProfile(student.getUserId());

        userLockRepository.findByIdForUpdate(student.getUserId());
        RegistrationRequest request = getOrCreateLocked(student.getUserId(), term.getTermId());
        requireEditable(request);

        Set<Long> wanted = new LinkedHashSet<>(body.offeringIds());

        List<CourseOffering> offerings = wanted.isEmpty()
                ? List.of()
                : offeringRepository.findWithCourseByIdIn(wanted);

        if (offerings.size() != wanted.size()) {
            Set<Long> found = offerings.stream().map(CourseOffering::getOfferingId).collect(Collectors.toSet());
            Long missing = wanted.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
            throw RegistrationErrors.notAvailable(missing);
        }

        int credits = validate(student.getUserId(), profile, term, offerings);

        List<RegistrationItem> existing = itemRepository.findByRequestIdOrderByItemIdAsc(request.getRequestId());

        existing.stream()
                .filter(i -> !wanted.contains(i.getOfferingId()))
                .forEach(itemRepository::delete);

        Set<Long> kept = existing.stream().map(RegistrationItem::getOfferingId).collect(Collectors.toSet());

        for (Long offeringId : wanted) {
            if (!kept.contains(offeringId)) {
                RegistrationItem item = new RegistrationItem();
                item.setRequestId(request.getRequestId());
                item.setOfferingId(offeringId);
                item.setStatus(PENDING);
                itemRepository.save(item);
            }
        }

        request.setTotalCredits(credits);
        requestRepository.save(request);
        itemRepository.flush();

        return mapper.toResponse(request);
    }

    @Transactional
    public RegistrationResponse submit(Long termId) {
        User student = currentUser.get();
        AcademicTerm term = requireTerm(termId);
        StudentProfile profile = requireCompleteProfile(student.getUserId());

        userLockRepository.findByIdForUpdate(student.getUserId());
        RegistrationRequest request = getOrCreateLocked(student.getUserId(), term.getTermId());
        requireEditable(request);

        List<RegistrationItem> items = itemRepository.findByRequestIdOrderByItemIdAsc(request.getRequestId());

        if (items.isEmpty()) {
            throw RegistrationErrors.emptyRequest();
        }

        if (profile.getAdvisorId() == null) {
            throw RegistrationErrors.noAdvisor();
        }

        User advisor = people.requireUser(profile.getAdvisorId());

        if (!"ACTIVE".equals(advisor.getStatus())) {
            throw RegistrationErrors.advisorDisabled();
        }

        List<CourseOffering> offerings = offeringRepository.findWithCourseByIdIn(
                items.stream().map(RegistrationItem::getOfferingId).toList()
        );

        int credits = validate(student.getUserId(), profile, term, offerings);

        request.setTotalCredits(credits);
        request.setAdvisorId(profile.getAdvisorId());
        request.setAdminId(null);
        request.setStatus(SUBMITTED);
        request.setSubmittedAt(LocalDateTime.now());
        request.setAdvisorDecidedAt(null);
        request.setAdminDecidedAt(null);
        request.setNote(null);
        requestRepository.save(request);

        notifier.submitted(advisor, student);

        return mapper.toResponse(request);
    }

    private int validate(Long studentId, StudentProfile profile, AcademicTerm term, List<CourseOffering> offerings) {
        if (offerings.isEmpty()) {
            return 0;
        }

        Set<Long> planCourseIds = planCourseIds(profile, term);

        List<Long> ids = offerings.stream().map(CourseOffering::getOfferingId).toList();
        Set<Long> enrolled = new LinkedHashSet<>(seatRepository.findEnrolledOfferingIds(studentId, ids));

        for (CourseOffering offering : offerings) {
            boolean inTerm = offering.getTerm().getTermId().equals(term.getTermId());

            if (!inTerm
                    || !planCourseIds.contains(offering.getCourse().getCourseId())
                    || enrolled.contains(offering.getOfferingId())) {
                throw RegistrationErrors.notAvailable(offering.getOfferingId());
            }
        }

        Set<Long> enrolledCourses = new LinkedHashSet<>(
                seatRepository.findActiveCourseIdsInTerm(studentId, term.getTermId())
        );

        for (CourseOffering offering : offerings) {
            if (enrolledCourses.contains(offering.getCourse().getCourseId())) {
                throw EnrollmentErrors.alreadyEnrolled();
            }
        }

        Set<Long> courses = new LinkedHashSet<>();

        for (CourseOffering offering : offerings) {
            if (!courses.add(offering.getCourse().getCourseId())) {
                throw RegistrationErrors.sameCourseTwice(offering.getCourse().getCourseCode());
            }
        }

        int requested = offerings.stream().mapToInt(o -> o.getCourse().getCreditHours()).sum();
        int total = requested + (int) seatRepository.sumActiveCreditsInTerm(studentId, term.getTermId());

        if (total > profile.getMaxCreditHours()) {
            throw RegistrationErrors.creditLimitExceeded(total, profile.getMaxCreditHours());
        }

        Map<Long, List<StudentGroup>> groups = groupsByOffering(ids);
        checkClashes(offerings, groups);

        Map<Long, Long> active = activeCounts(ids);

        for (CourseOffering offering : offerings) {
            if (seatsLeft(offering, active) <= 0) {
                throw EnrollmentErrors.offeringFull();
            }
        }

        return requested;
    }

    private void checkClashes(List<CourseOffering> offerings, Map<Long, List<StudentGroup>> groups) {
        for (int i = 0; i < offerings.size(); i++) {
            for (int j = i + 1; j < offerings.size(); j++) {
                if (!canAttend(List.of(offerings.get(i), offerings.get(j)), groups)) {
                    throw RegistrationErrors.scheduleClash(List.of(
                            offerings.get(i).getCourse().getCourseCode(),
                            offerings.get(j).getCourse().getCourseCode()
                    ));
                }
            }
        }

        if (!canAttend(offerings, groups)) {
            throw RegistrationErrors.scheduleClash(
                    offerings.stream().map(o -> o.getCourse().getCourseCode()).toList()
            );
        }
    }

    private boolean canAttend(List<CourseOffering> offerings, Map<Long, List<StudentGroup>> groups) {
        List<SlotChoice> choices = new ArrayList<>();

        for (CourseOffering offering : offerings) {
            Map<String, List<StudentGroup>> byType = groups
                    .getOrDefault(offering.getOfferingId(), List.of()).stream()
                    .collect(Collectors.groupingBy(StudentGroup::getGroupType));

            for (List<StudentGroup> options : byType.values()) {
                boolean allTimed = options.stream().allMatch(g ->
                        g.getDayOfWeek() != null && g.getStartTime() != null && g.getEndTime() != null);

                if (allTimed) {
                    choices.add(new SlotChoice(offering.getOfferingId(), options));
                }
            }
        }

        choices.sort(Comparator.comparingInt(c -> c.options().size()));

        return place(0, choices, new ArrayList<>(), new int[]{CLASH_SEARCH_LIMIT});
    }

    private boolean place(int index, List<SlotChoice> choices, List<Placed> placed, int[] budget) {
        if (index == choices.size()) {
            return true;
        }

        if (--budget[0] < 0) {
            return false;
        }

        SlotChoice choice = choices.get(index);

        for (StudentGroup option : choice.options()) {
            boolean free = placed.stream().noneMatch(p -> overlap(p.group(), option));

            if (free) {
                placed.add(new Placed(choice.offeringId(), option));

                if (place(index + 1, choices, placed, budget)) {
                    return true;
                }

                placed.remove(placed.size() - 1);
            }
        }

        return false;
    }

    private boolean overlap(StudentGroup a, StudentGroup b) {
        return a.getDayOfWeek().equals(b.getDayOfWeek())
                && a.getStartTime().isBefore(b.getEndTime())
                && b.getStartTime().isBefore(a.getEndTime());
    }

    private record SlotChoice(Long offeringId, List<StudentGroup> options) {
    }

    private record Placed(Long offeringId, StudentGroup group) {
    }

    public int revalidate(RegistrationRequest request) {
        AcademicTerm term = requireTerm(request.getTermId());
        StudentProfile profile = requireCompleteProfile(request.getStudentUserId());

        List<Long> ids = itemRepository.findByRequestIdOrderByItemIdAsc(request.getRequestId()).stream()
                .map(RegistrationItem::getOfferingId)
                .toList();

        if (ids.isEmpty()) {
            throw RegistrationErrors.emptyRequest();
        }

        return validate(request.getStudentUserId(), profile, term, offeringRepository.findWithCourseByIdIn(ids));
    }

    private RegistrationRequest getOrCreate(Long studentId, Long termId) {
        return requestRepository.findByStudentUserIdAndTermId(studentId, termId)
                .orElseGet(() -> createDraft(studentId, termId));
    }

    private RegistrationRequest getOrCreateLocked(Long studentId, Long termId) {
        return requestRepository.findForUpdate(studentId, termId)
                .orElseGet(() -> createDraft(studentId, termId));
    }

    private RegistrationRequest createDraft(Long studentId, Long termId) {
        RegistrationRequest created = new RegistrationRequest();
        created.setStudentUserId(studentId);
        created.setTermId(termId);
        created.setStatus(DRAFT);
        created.setTotalCredits(0);
        return requestRepository.save(created);
    }

    private void requireEditable(RegistrationRequest request) {
        if (!DRAFT.equals(request.getStatus()) && !RETURNED.equals(request.getStatus())) {
            throw RegistrationErrors.alreadySubmitted();
        }
    }

    private AcademicTerm requireTerm(Long termId) {
        return termRepository.findById(termId).orElseThrow(AcademicErrors::termNotFound);
    }

    private StudentProfile requireCompleteProfile(Long studentId) {
        StudentProfile profile = profileRepository.findById(studentId)
                .orElseThrow(RegistrationErrors::profileIncomplete);

        if (profile.getProgramId() == null || profile.getLevel() == null || profile.getMaxCreditHours() == null) {
            throw RegistrationErrors.profileIncomplete();
        }

        return profile;
    }

    private Set<Long> planCourseIds(StudentProfile profile, AcademicTerm term) {
        return planRepository
                .findByProgramIdAndLevelAndTermType(profile.getProgramId(), profile.getLevel(),
                        term.getTermType().trim().toUpperCase())
                .stream()
                .map(ProgramPlanCourse::getCourseId)
                .collect(Collectors.toSet());
    }

    private Map<Long, Long> activeCounts(Collection<Long> offeringIds) {
        Map<Long, Long> counts = new HashMap<>();

        seatRepository.countActiveByOffering(offeringIds)
                .forEach(row -> counts.put((Long) row[0], (Long) row[1]));

        return counts;
    }

    private int seatsLeft(CourseOffering offering, Map<Long, Long> active) {
        if (offering.getCapacity() == null) {
            return UNLIMITED_SEATS;
        }

        return (int) Math.max(0, offering.getCapacity() - active.getOrDefault(offering.getOfferingId(), 0L));
    }

    private Map<Long, List<StudentGroup>> groupsByOffering(Collection<Long> offeringIds) {
        return scheduleRepository.findByOfferingIdIn(offeringIds).stream()
                .sorted(Comparator.comparing(StudentGroup::getGroupType).thenComparing(StudentGroup::getLabel))
                .collect(Collectors.groupingBy(StudentGroup::getOfferingId));
    }

    private Map<Long, List<String>> doctorNames(Map<Long, List<StudentGroup>> groups) {
        List<Long> groupIds = groups.values().stream()
                .flatMap(List::stream)
                .map(StudentGroup::getGroupId)
                .toList();

        if (groupIds.isEmpty()) {
            return Map.of();
        }

        List<TeachingAssignment> assignments = teachingRepository.findCurrentByGroupIds(groupIds, LocalDateTime.now());

        Map<Long, String> names = people.doctors(
                assignments.stream().map(TeachingAssignment::getDoctorUserId).collect(Collectors.toSet())
        ).values().stream().collect(Collectors.toMap(d -> d.userId(), d -> d.fullName()));

        Map<Long, Long> groupToOffering = groups.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(StudentGroup::getGroupId, StudentGroup::getOfferingId));

        Map<Long, List<String>> result = new HashMap<>();

        for (TeachingAssignment assignment : assignments) {
            List<String> list = result.computeIfAbsent(groupToOffering.get(assignment.getGroupId()), k -> new ArrayList<>());
            String name = names.get(assignment.getDoctorUserId());

            if (name != null && !list.contains(name)) {
                list.add(name);
            }
        }

        return result;
    }

    private ScheduleSlot toSlot(StudentGroup group) {
        return new ScheduleSlot(
                group.getGroupType(),
                group.getLabel(),
                group.getDayOfWeek(),
                group.getStartTime() == null ? null : group.getStartTime().format(TIME),
                group.getEndTime() == null ? null : group.getEndTime().format(TIME)
        );
    }
}
