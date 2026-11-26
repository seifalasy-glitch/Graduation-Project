package com.edusync.edusync.backend.registration.service;

import com.edusync.edusync.backend.academic.entity.CourseOffering;
import com.edusync.edusync.backend.common.DateTimeUtil;
import com.edusync.edusync.backend.enrollment.dto.DoctorRef;
import com.edusync.edusync.backend.enrollment.dto.StudentRef;
import com.edusync.edusync.backend.enrollment.service.PeopleService;
import com.edusync.edusync.backend.registration.dto.RegistrationItemResponse;
import com.edusync.edusync.backend.registration.dto.RegistrationResponse;
import com.edusync.edusync.backend.registration.entity.RegistrationItem;
import com.edusync.edusync.backend.registration.entity.RegistrationRequest;
import com.edusync.edusync.backend.registration.entity.StudentProfile;
import com.edusync.edusync.backend.registration.repository.RegistrationItemRepository;
import com.edusync.edusync.backend.registration.repository.RegistrationOfferingRepository;
import com.edusync.edusync.backend.registration.repository.StudentProfileRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RegistrationMapper {

    private final RegistrationItemRepository itemRepository;
    private final RegistrationOfferingRepository offeringRepository;
    private final StudentProfileRepository profileRepository;
    private final PeopleService people;

    public RegistrationMapper(
            RegistrationItemRepository itemRepository,
            RegistrationOfferingRepository offeringRepository,
            StudentProfileRepository profileRepository,
            PeopleService people
    ) {
        this.itemRepository = itemRepository;
        this.offeringRepository = offeringRepository;
        this.profileRepository = profileRepository;
        this.people = people;
    }

    public RegistrationResponse toResponse(RegistrationRequest request) {
        return toResponses(List.of(request)).get(0);
    }

    public List<RegistrationResponse> toResponses(List<RegistrationRequest> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RegistrationItem>> items = itemRepository
                .findByRequestIdIn(rows.stream().map(RegistrationRequest::getRequestId).toList())
                .stream()
                .collect(Collectors.groupingBy(RegistrationItem::getRequestId));

        Collection<Long> offeringIds = items.values().stream()
                .flatMap(List::stream)
                .map(RegistrationItem::getOfferingId)
                .collect(Collectors.toSet());

        Map<Long, CourseOffering> offerings = offeringIds.isEmpty()
                ? Map.of()
                : offeringRepository.findWithCourseByIdIn(offeringIds).stream()
                .collect(Collectors.toMap(CourseOffering::getOfferingId, Function.identity()));

        Collection<Long> studentIds = rows.stream()
                .map(RegistrationRequest::getStudentUserId)
                .collect(Collectors.toSet());

        Map<Long, StudentRef> students = people.students(studentIds);

        Map<Long, StudentProfile> profiles = profileRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, Function.identity()));

        Map<Long, DoctorRef> advisors = people.doctors(
                rows.stream()
                        .map(r -> advisorId(r, profiles.get(r.getStudentUserId())))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );

        return rows.stream()
                .map(r -> {
                    StudentProfile profile = profiles.get(r.getStudentUserId());
                    Long advisorId = advisorId(r, profile);

                    List<RegistrationItemResponse> itemResponses = items
                            .getOrDefault(r.getRequestId(), List.of())
                            .stream()
                            .sorted(java.util.Comparator.comparing(RegistrationItem::getItemId))
                            .map(i -> toItem(i, offerings.get(i.getOfferingId())))
                            .toList();

                    return new RegistrationResponse(
                            r.getRequestId(),
                            students.get(r.getStudentUserId()),
                            r.getTermId(),
                            r.getStatus(),
                            r.getTotalCredits() == null ? 0 : r.getTotalCredits(),
                            profile == null ? null : profile.getMaxCreditHours(),
                            advisorId == null ? null : advisors.get(advisorId),
                            r.getNote(),
                            DateTimeUtil.toInstant(r.getSubmittedAt()),
                            DateTimeUtil.toInstant(r.getAdvisorDecidedAt()),
                            DateTimeUtil.toInstant(r.getAdminDecidedAt()),
                            itemResponses
                    );
                })
                .toList();
    }

    private Long advisorId(RegistrationRequest request, StudentProfile profile) {
        if (request.getAdvisorId() != null) {
            return request.getAdvisorId();
        }

        return profile == null ? null : profile.getAdvisorId();
    }

    private RegistrationItemResponse toItem(RegistrationItem item, CourseOffering offering) {
        return new RegistrationItemResponse(
                item.getItemId(),
                item.getOfferingId(),
                offering.getCourse().getCourseCode(),
                offering.getCourse().getTitle(),
                offering.getSection(),
                offering.getCourse().getCreditHours(),
                item.getStatus()
        );
    }
}
