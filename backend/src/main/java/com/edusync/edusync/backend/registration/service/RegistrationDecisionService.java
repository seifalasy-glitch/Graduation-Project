package com.edusync.edusync.backend.registration.service;

import com.edusync.edusync.backend.academic.entity.CourseOffering;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.CourseOfferingRepository;
import com.edusync.edusync.backend.enrollment.entity.Enrollment;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.enrollment.repository.EnrollmentRepository;
import com.edusync.edusync.backend.enrollment.service.PeopleService;
import com.edusync.edusync.backend.registration.dto.RegistrationDecisionRequest;
import com.edusync.edusync.backend.registration.dto.RegistrationResponse;
import com.edusync.edusync.backend.registration.entity.RegistrationItem;
import com.edusync.edusync.backend.registration.entity.RegistrationRequest;
import com.edusync.edusync.backend.registration.entity.StudentProfile;
import com.edusync.edusync.backend.registration.exception.RegistrationErrors;
import com.edusync.edusync.backend.registration.repository.RegistrationItemRepository;
import com.edusync.edusync.backend.registration.repository.RegistrationRequestRepository;
import com.edusync.edusync.backend.registration.repository.StudentProfileRepository;
import com.edusync.edusync.backend.users.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RegistrationDecisionService {

    private static final String ADVISOR_APPROVED = "ADVISOR_APPROVED";
    private static final String APPROVED = "APPROVED";
    private static final String ENROLLED = "ENROLLED";

    private final RegistrationRequestRepository requestRepository;
    private final RegistrationItemRepository itemRepository;
    private final StudentProfileRepository profileRepository;
    private final CourseOfferingRepository offeringRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CurrentUserService currentUser;
    private final PeopleService people;
    private final RegistrationMapper mapper;
    private final RegistrationNotifier notifier;
    private final RegistrationService registrationService;

    public RegistrationDecisionService(
            RegistrationRequestRepository requestRepository,
            RegistrationItemRepository itemRepository,
            StudentProfileRepository profileRepository,
            CourseOfferingRepository offeringRepository,
            EnrollmentRepository enrollmentRepository,
            CurrentUserService currentUser,
            PeopleService people,
            RegistrationMapper mapper,
            RegistrationNotifier notifier,
            RegistrationService registrationService
    ) {
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.profileRepository = profileRepository;
        this.offeringRepository = offeringRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.currentUser = currentUser;
        this.people = people;
        this.mapper = mapper;
        this.notifier = notifier;
        this.registrationService = registrationService;
    }

    @Transactional
    public RegistrationResponse advisorDecide(Long requestId, RegistrationDecisionRequest body) {
        User advisor = currentUser.get();
        RegistrationRequest request = lock(requestId);

        if (!advisor.getUserId().equals(request.getAdvisorId())) {
            throw RegistrationErrors.notYourAdvisee();
        }

        if (!RegistrationService.SUBMITTED.equals(request.getStatus())) {
            throw RegistrationErrors.wrongState("SUBMITTED");
        }

        boolean approve = isApprove(body);
        String note = cleanNote(body.note());

        request.setStatus(approve ? ADVISOR_APPROVED : RegistrationService.RETURNED);
        request.setNote(note);
        request.setAdvisorDecidedAt(LocalDateTime.now());

        return mapper.toResponse(requestRepository.save(request));
    }

    @Transactional
    public RegistrationResponse adminDecide(Long requestId, RegistrationDecisionRequest body) {
        User admin = currentUser.get();
        RegistrationRequest request = lock(requestId);

        if (!ADVISOR_APPROVED.equals(request.getStatus())) {
            throw RegistrationErrors.wrongState(ADVISOR_APPROVED);
        }

        boolean approve = isApprove(body);
        String note = cleanNote(body.note());

        if (approve) {
            enrollAll(request);
            request.setStatus(APPROVED);
        } else {
            request.setStatus(RegistrationService.RETURNED);
        }

        request.setNote(note);
        request.setAdminId(admin.getUserId());
        request.setAdminDecidedAt(LocalDateTime.now());
        requestRepository.save(request);

        notifier.decided(people.requireUser(request.getStudentUserId()), approve, note);

        return mapper.toResponse(request);
    }

    private void enrollAll(RegistrationRequest request) {
        List<RegistrationItem> items = itemRepository.findByRequestIdOrderByItemIdAsc(request.getRequestId());

        if (items.isEmpty()) {
            throw RegistrationErrors.emptyRequest();
        }

        User student = people.requireUser(request.getStudentUserId());

        if (!"ACTIVE".equals(student.getStatus())) {
            throw EnrollmentErrors.studentDisabled();
        }

        StudentProfile profile = profileRepository.findById(student.getUserId())
                .filter(p -> p.getProgramId() != null)
                .orElseThrow(RegistrationErrors::profileIncomplete);

        List<RegistrationItem> ordered = items.stream()
                .sorted(Comparator.comparing(RegistrationItem::getOfferingId))
                .toList();

        List<CourseOffering> locked = new ArrayList<>();

        for (RegistrationItem item : ordered) {
            locked.add(offeringRepository.findByIdForUpdate(item.getOfferingId())
                    .orElseThrow(AcademicErrors::offeringNotFound));
        }

        request.setTotalCredits(registrationService.revalidate(request));

        for (int i = 0; i < ordered.size(); i++) {
            RegistrationItem item = ordered.get(i);
            CourseOffering offering = locked.get(i);

            if (enrollmentRepository.existsByStudentUserIdAndOfferingId(student.getUserId(), offering.getOfferingId())) {
                throw EnrollmentErrors.alreadyEnrolled();
            }

            if (offering.getCapacity() != null
                    && enrollmentRepository.countByOfferingIdAndStatus(offering.getOfferingId(), "ACTIVE")
                    >= offering.getCapacity()) {
                throw EnrollmentErrors.offeringFull();
            }

            Enrollment enrollment = new Enrollment();
            enrollment.setStudentUserId(student.getUserId());
            enrollment.setOfferingId(offering.getOfferingId());
            enrollment.setProgramId(profile.getProgramId());
            enrollment.setStatus("ACTIVE");
            enrollmentRepository.save(enrollment);

            item.setStatus(ENROLLED);
            itemRepository.save(item);
        }
    }

    private RegistrationRequest lock(Long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(RegistrationErrors::requestNotFound);
    }

    private boolean isApprove(RegistrationDecisionRequest body) {
        String decision = body.decision().trim().toUpperCase(Locale.ROOT);

        if (!decision.equals("APPROVE") && !decision.equals("RETURN")) {
            throw AcademicErrors.validation("decision", "Decision must be APPROVE or RETURN");
        }

        if (decision.equals("RETURN") && (body.note() == null || body.note().isBlank())) {
            throw AcademicErrors.validation("note", "A note is required when returning a request");
        }

        return decision.equals("APPROVE");
    }

    private String cleanNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
