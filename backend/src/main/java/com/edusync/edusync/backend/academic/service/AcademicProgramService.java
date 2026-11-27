package com.edusync.edusync.backend.academic.service;

import com.edusync.edusync.backend.academic.dto.AcademicProgramResponse;
import com.edusync.edusync.backend.academic.dto.CreateAcademicProgramRequest;
import com.edusync.edusync.backend.academic.dto.UpdateAcademicProgramRequest;
import com.edusync.edusync.backend.academic.entity.AcademicProgram;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AcademicProgramService {

    private final AcademicProgramRepository programRepository;

    public AcademicProgramService(AcademicProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    /** API contract: activeOnly (default true) hides the deactivated programs. */
    @Transactional(readOnly = true)
    public List<AcademicProgramResponse> getPrograms(boolean activeOnly) {
        List<AcademicProgram> programs = programRepository.findAllByOrderByCodeAsc()
                .stream()
                .filter(program -> !activeOnly || Boolean.TRUE.equals(program.getActive()))
                .toList();

        if (programs.isEmpty()) {
            return List.of();
        }

        // 2 count queries for the whole list, not 2 per program
        List<Long> ids = programs.stream().map(AcademicProgram::getProgramId).toList();
        Map<Long, Long> students = toMap(programRepository.countActiveStudents(ids));
        Map<Long, Long> courses = toMap(programRepository.countPlanCourses(ids));

        return programs.stream()
                .map(program -> toResponse(program, students, courses))
                .toList();
    }

    @Transactional
    public AcademicProgramResponse createProgram(CreateAcademicProgramRequest request) {
        String code = request.code().trim();
        String name = request.name().trim();

        if (code.isBlank()) {
            throw AcademicErrors.validation("code", "Program code cannot be blank");
        }

        if (name.isBlank()) {
            throw AcademicErrors.validation("name", "Program name cannot be blank");
        }

        if (programRepository.existsByCodeIgnoreCase(code)) {
            throw AcademicErrors.programCodeExists();
        }

        AcademicProgram program = new AcademicProgram();
        program.setCode(code);
        program.setName(name);
        program.setDepartment(
                request.department() == null
                        ? null
                        : request.department().trim()
        );
        program.setActive(
                request.active() == null ? true : request.active()
        );

        return toResponse(programRepository.save(program));   // new program: counts are 0
    }

    @Transactional
    public AcademicProgramResponse updateProgram(
            Long programId,
            UpdateAcademicProgramRequest request
    ) {
        AcademicProgram program = programRepository.findById(programId)
                .orElseThrow(() -> AcademicErrors.programNotFound());

        if (request.code() != null) {
            String code = request.code().trim();

            if (code.isBlank()) {
                throw AcademicErrors.validation("code", "Program code cannot be blank");
            }

            if (programRepository.existsByCodeIgnoreCaseAndProgramIdNot(code, programId)) {
                throw AcademicErrors.programCodeExists();
            }

            program.setCode(code);
        }

        if (request.name() != null) {
            String name = request.name().trim();

            if (name.isBlank()) {
                throw AcademicErrors.validation("name", "Program name cannot be blank");
            }

            program.setName(name);
        }

        if (request.department() != null) {
            program.setDepartment(request.department().trim());
        }

        if (request.active() != null) {
            program.setActive(request.active());
        }

        return toResponse(programRepository.save(program));
    }

    /** One program (POST / PATCH answers): looks up its two counts. */
    private AcademicProgramResponse toResponse(AcademicProgram program) {
        List<Long> id = List.of(program.getProgramId());
        return toResponse(program,
                toMap(programRepository.countActiveStudents(id)),
                toMap(programRepository.countPlanCourses(id)));
    }

    private AcademicProgramResponse toResponse(
            AcademicProgram program,
            Map<Long, Long> studentCounts,
            Map<Long, Long> courseCounts
    ) {
        return new AcademicProgramResponse(
                program.getProgramId(),
                program.getCode(),
                program.getName(),
                program.getDepartment(),
                program.getActive(),
                studentCounts.getOrDefault(program.getProgramId(), 0L),  // no row = 0
                courseCounts.getOrDefault(program.getProgramId(), 0L)
        );
    }

    /** Rows of [programId, count] from the count queries -> programId -> count. */
    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return map;
    }
}
