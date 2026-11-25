package com.edusync.edusync.backend.academic.service;

import com.edusync.edusync.backend.academic.dto.AcademicCourseResponse;
import com.edusync.edusync.backend.academic.dto.CreateAcademicCourseRequest;
import com.edusync.edusync.backend.academic.dto.UpdateAcademicCourseRequest;
import com.edusync.edusync.backend.academic.entity.AcademicCourse;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AcademicCourseService {

    private final AcademicCourseRepository courseRepository;

    public AcademicCourseService(AcademicCourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    /** API contract: q searches the course code and the title. */
    @Transactional(readOnly = true)
    public List<AcademicCourseResponse> getCourses(String q) {

        List<AcademicCourse> courses = (q == null || q.isBlank())
                ? courseRepository.findAllByOrderByCourseCodeAsc()
                : courseRepository.search(q.trim());

        return courses
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AcademicCourseResponse createCourse(
            CreateAcademicCourseRequest request
    ) {

        String courseCode = request.courseCode().trim();
        String title = request.title().trim();
        Integer creditHours = request.creditHours();

        if (courseCode.isBlank()) {
            throw AcademicErrors.validation("courseCode", "Course code cannot be blank");
        }

        if (title.isBlank()) {
            throw AcademicErrors.validation("title", "Course title cannot be blank");
        }

        validateCreditHours(creditHours);

        if (courseRepository.existsByCourseCodeIgnoreCase(courseCode)) {
            throw AcademicErrors.courseCodeExists();
        }

        AcademicCourse course = new AcademicCourse();

        course.setCourseCode(courseCode);
        course.setTitle(title);
        course.setCreditHours(creditHours);

        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public AcademicCourseResponse updateCourse(
            Long courseId,
            UpdateAcademicCourseRequest request
    ) {

        AcademicCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> AcademicErrors.courseNotFound());

        if (request.courseCode() != null) {

            String courseCode = request.courseCode().trim();

            if (courseCode.isBlank()) {
                throw AcademicErrors.validation("courseCode", "Course code cannot be blank");
            }

            if (courseRepository
                    .existsByCourseCodeIgnoreCaseAndCourseIdNot(
                            courseCode,
                            courseId
                    )) {

                throw AcademicErrors.courseCodeExists();
            }

            course.setCourseCode(courseCode);
        }

        if (request.title() != null) {

            String title = request.title().trim();

            if (title.isBlank()) {
                throw AcademicErrors.validation("title", "Course title cannot be blank");
            }

            course.setTitle(title);
        }

        if (request.creditHours() != null) {

            validateCreditHours(request.creditHours());

            course.setCreditHours(request.creditHours());
        }

        return toResponse(courseRepository.save(course));
    }

    private void validateCreditHours(Integer creditHours) {

        if (creditHours == null || creditHours < 1) {
            throw AcademicErrors.validation("creditHours", "Credit hours must be at least 1");
        }
    }

    private AcademicCourseResponse toResponse(AcademicCourse course) {

        return new AcademicCourseResponse(
                course.getCourseId(),
                course.getCourseCode(),
                course.getTitle(),
                course.getCreditHours()
        );
    }
}
