package com.edusync.edusync.backend.registration.service;

import com.edusync.edusync.backend.academic.dto.AcademicCourseResponse;
import com.edusync.edusync.backend.academic.entity.AcademicCourse;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicCourseRepository;
import com.edusync.edusync.backend.academic.repository.AcademicProgramRepository;
import com.edusync.edusync.backend.registration.dto.PlanCourseRequest;
import com.edusync.edusync.backend.registration.dto.PlanCourseResponse;
import com.edusync.edusync.backend.registration.entity.ProgramPlanCourse;
import com.edusync.edusync.backend.registration.exception.RegistrationErrors;
import com.edusync.edusync.backend.registration.repository.ProgramPlanCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlanCourseService {

    public static final Set<String> TERM_TYPES = Set.of("FALL", "SPRING", "SUMMER");

    private final ProgramPlanCourseRepository planRepository;
    private final AcademicProgramRepository programRepository;
    private final AcademicCourseRepository courseRepository;

    public PlanCourseService(
            ProgramPlanCourseRepository planRepository,
            AcademicProgramRepository programRepository,
            AcademicCourseRepository courseRepository
    ) {
        this.planRepository = planRepository;
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanCourseResponse> list(Long programId, Integer level, String termType) {
        programRepository.findById(programId).orElseThrow(AcademicErrors::programNotFound);

        String wantedType = termType == null ? null : parseTermType(termType);

        List<ProgramPlanCourse> rows = planRepository.findByProgramIdOrderByLevelAscTermTypeAscPlanCourseIdAsc(programId)
                .stream()
                .filter(p -> level == null || level.equals(p.getLevel()))
                .filter(p -> wantedType == null || wantedType.equals(p.getTermType()))
                .toList();

        return toResponses(rows);
    }

    @Transactional
    public PlanCourseResponse add(Long programId, PlanCourseRequest request) {
        programRepository.findById(programId).orElseThrow(AcademicErrors::programNotFound);
        courseRepository.findById(request.courseId()).orElseThrow(AcademicErrors::courseNotFound);

        String termType = parseTermType(request.termType());

        if (planRepository.existsByProgramIdAndLevelAndTermTypeAndCourseId(
                programId, request.level(), termType, request.courseId())) {
            throw RegistrationErrors.alreadyInPlan();
        }

        ProgramPlanCourse plan = new ProgramPlanCourse();
        plan.setProgramId(programId);
        plan.setCourseId(request.courseId());
        plan.setLevel(request.level());
        plan.setTermType(termType);

        return toResponses(List.of(planRepository.save(plan))).get(0);
    }

    @Transactional
    public void remove(Long planCourseId) {
        ProgramPlanCourse plan = planRepository.findById(planCourseId)
                .orElseThrow(RegistrationErrors::planCourseNotFound);

        planRepository.delete(plan);
    }

    public static String parseTermType(String value) {
        String type = value.trim().toUpperCase(Locale.ROOT);

        if (!TERM_TYPES.contains(type)) {
            throw AcademicErrors.validation("termType", "Term type must be FALL, SPRING or SUMMER");
        }

        return type;
    }

    private List<PlanCourseResponse> toResponses(List<ProgramPlanCourse> rows) {
        Map<Long, AcademicCourse> courses = courseRepository
                .findAllById(rows.stream().map(ProgramPlanCourse::getCourseId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AcademicCourse::getCourseId, Function.identity()));

        return rows.stream()
                .map(p -> new PlanCourseResponse(
                        p.getPlanCourseId(),
                        p.getProgramId(),
                        courseResponse(courses.get(p.getCourseId())),
                        p.getLevel(),
                        p.getTermType()
                ))
                .toList();
    }

    public static AcademicCourseResponse courseResponse(AcademicCourse course) {
        return new AcademicCourseResponse(
                course.getCourseId(),
                course.getCourseCode(),
                course.getTitle(),
                course.getCreditHours()
        );
    }
}
