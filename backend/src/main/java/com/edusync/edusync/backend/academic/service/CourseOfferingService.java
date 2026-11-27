package com.edusync.edusync.backend.academic.service;

import com.edusync.edusync.backend.academic.dto.AcademicCourseResponse;
import com.edusync.edusync.backend.academic.dto.AcademicTermResponse;
import com.edusync.edusync.backend.academic.dto.CourseOfferingResponse;
import com.edusync.edusync.backend.academic.dto.CreateCourseOfferingRequest;
import com.edusync.edusync.backend.academic.dto.UpdateCourseOfferingRequest;
import com.edusync.edusync.backend.academic.entity.AcademicCourse;
import com.edusync.edusync.backend.academic.entity.AcademicTerm;
import com.edusync.edusync.backend.academic.entity.CourseOffering;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicCourseRepository;
import com.edusync.edusync.backend.academic.repository.AcademicTermRepository;
import com.edusync.edusync.backend.academic.repository.CourseOfferingRepository;
import com.edusync.edusync.backend.enrollment.service.OfferingAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseOfferingService {

    private final CourseOfferingRepository offeringRepository;
    private final AcademicCourseRepository courseRepository;
    private final AcademicTermRepository termRepository;
    private final OfferingAccessService access;

    public CourseOfferingService(
            CourseOfferingRepository offeringRepository,
            AcademicCourseRepository courseRepository,
            AcademicTermRepository termRepository,
            OfferingAccessService access
    ) {
        this.offeringRepository = offeringRepository;
        this.courseRepository = courseRepository;
        this.termRepository = termRepository;
        this.access = access;
    }

    /** API contract: both filters are optional. */
    @Transactional(readOnly = true)
    public List<CourseOfferingResponse> getOfferings(Long termId, Long courseId) {
        List<CourseOffering> offerings = offeringRepository.findAllWithDetails()
                .stream()
                .filter(o -> termId == null
                        || termId.equals(o.getTerm().getTermId()))
                .filter(o -> courseId == null
                        || courseId.equals(o.getCourse().getCourseId()))
                .toList();

        // one count query for the whole list, not one per offering
        Map<Long, Long> enrolled = enrolledCounts(offerings.stream().map(CourseOffering::getOfferingId).toList());
        return offerings.stream()
                .map(o -> toResponse(o, enrolled.getOrDefault(o.getOfferingId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseOfferingResponse getOfferingById(Long offeringId) {
        CourseOffering offering = findOffering(offeringId);

        // API contract: students only if enrolled, doctors only if they teach it.
        // Answer 404 (not 403) so nobody can probe which offerings exist.
        if (!access.canRead(offeringId)) {
            throw AcademicErrors.offeringNotFound();
        }

        return toResponse(offering);
    }

    @Transactional
    public CourseOfferingResponse createOffering(
            CreateCourseOfferingRequest request
    ) {
        AcademicCourse course = findCourse(request.courseId());
        AcademicTerm term = findTerm(request.termId());

        String section = normalizeSection(request.section());

        if (section == null) {
            throw AcademicErrors.validation("section", "Section is required");
        }

        validateCourseworkMax(request.courseworkMax());
        validateCapacity(request.capacity());

        checkDuplicate(
                course.getCourseId(),
                term.getTermId(),
                section,
                null
        );

        CourseOffering offering = new CourseOffering();

        offering.setCourse(course);
        offering.setTerm(term);
        offering.setCourseworkMax(request.courseworkMax());
        offering.setSection(section);
        offering.setCapacity(request.capacity());

        return toResponse(offeringRepository.save(offering));
    }

    /** API contract (OfferingUpdate): only section, capacity and courseworkMax can change. */
    @Transactional
    public CourseOfferingResponse updateOffering(
            Long offeringId,
            UpdateCourseOfferingRequest request
    ) {
        CourseOffering offering = findOffering(offeringId);

        String section = request.section() != null
                ? normalizeSection(request.section())
                : offering.getSection();

        BigDecimal courseworkMax = request.courseworkMax() != null
                ? request.courseworkMax()
                : offering.getCourseworkMax();

        Integer capacity = request.capacity() != null
                ? request.capacity()
                : offering.getCapacity();

        validateCourseworkMax(courseworkMax);
        validateCapacity(capacity);

        checkDuplicate(
                offering.getCourse().getCourseId(),
                offering.getTerm().getTermId(),
                section,
                offeringId
        );

        offering.setSection(section);
        offering.setCourseworkMax(courseworkMax);
        offering.setCapacity(capacity);

        return toResponse(offeringRepository.save(offering));
    }

    private CourseOffering findOffering(Long offeringId) {
        return offeringRepository.findById(offeringId)
                .orElseThrow(() -> AcademicErrors.offeringNotFound());
    }

    private AcademicCourse findCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> AcademicErrors.courseNotFound());
    }

    private AcademicTerm findTerm(Long termId) {
        return termRepository.findById(termId)
                .orElseThrow(() -> AcademicErrors.termNotFound());
    }

    private String normalizeSection(String section) {
        if (section == null) {
            return null;
        }

        String normalized = section.trim();

        if (normalized.isBlank()) {
            throw AcademicErrors.validation("section", "Section cannot be blank");
        }

        return normalized;
    }

    private void validateCourseworkMax(BigDecimal courseworkMax) {
        if (courseworkMax != null
                && courseworkMax.compareTo(BigDecimal.ZERO) < 0) {
            throw AcademicErrors.validation(
                    "courseworkMax",
                    "Coursework max cannot be negative"
            );
        }
    }

    private void validateCapacity(Integer capacity) {
        if (capacity != null && capacity < 1) {
            throw AcademicErrors.validation("capacity", "Capacity must be at least 1");
        }
    }

    private void checkDuplicate(
            Long courseId,
            Long termId,
            String section,
            Long excludedOfferingId
    ) {
        boolean duplicate;

        if (section != null) {
            duplicate = excludedOfferingId == null
                    ? offeringRepository
                    .existsByCourse_CourseIdAndTerm_TermIdAndSectionIgnoreCase(
                            courseId, termId, section
                    )
                    : offeringRepository
                    .existsByCourse_CourseIdAndTerm_TermIdAndSectionIgnoreCaseAndOfferingIdNot(
                            courseId, termId, section, excludedOfferingId
                    );
        } else {
            // Old rows without a section (NULL)
            duplicate = excludedOfferingId == null
                    ? offeringRepository
                    .existsByCourse_CourseIdAndTerm_TermIdAndSectionIsNull(
                            courseId, termId
                    )
                    : offeringRepository
                    .existsByCourse_CourseIdAndTerm_TermIdAndSectionIsNullAndOfferingIdNot(
                            courseId, termId, excludedOfferingId
                    );
        }

        if (duplicate) {
            throw AcademicErrors.offeringExists();
        }
    }

    /** offeringId -> ACTIVE enrollments; offerings with none are missing (= 0). */
    private Map<Long, Long> enrolledCounts(List<Long> offeringIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (!offeringIds.isEmpty()) {
            for (Object[] row : offeringRepository.countActiveEnrollments(offeringIds)) {
                counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
        }
        return counts;
    }

    /** One offering (get / create / update answers): looks up its enrolled count. */
    private CourseOfferingResponse toResponse(CourseOffering offering) {
        Long id = offering.getOfferingId();
        return toResponse(offering, id == null ? 0L : enrolledCounts(List.of(id)).getOrDefault(id, 0L));
    }

    private CourseOfferingResponse toResponse(CourseOffering offering, long enrolledCount) {
        AcademicCourse course = offering.getCourse();
        AcademicTerm term = offering.getTerm();

        return new CourseOfferingResponse(
                offering.getOfferingId(),
                new AcademicCourseResponse(
                        course.getCourseId(),
                        course.getCourseCode(),
                        course.getTitle(),
                        course.getCreditHours()
                ),
                new AcademicTermResponse(
                        term.getTermId(),
                        term.getAcademicYear(),
                        term.getTermType(),
                        term.getStartDate(),
                        term.getEndDate()
                ),
                offering.getSection(),
                offering.getCapacity(),
                offering.getCourseworkMax(),
                enrolledCount
        );
    }
}
