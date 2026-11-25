package com.edusync.edusync.backend.academic.service;

import com.edusync.edusync.backend.academic.dto.AcademicProgramResponse;
import com.edusync.edusync.backend.academic.dto.CreateAcademicProgramRequest;
import com.edusync.edusync.backend.academic.dto.UpdateAcademicProgramRequest;
import com.edusync.edusync.backend.academic.entity.AcademicProgram;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AcademicProgramService {

    private final AcademicProgramRepository programRepository;

    public AcademicProgramService(AcademicProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    /** API contract: activeOnly (default true) hides the deactivated programs. */
    @Transactional(readOnly = true)
    public List<AcademicProgramResponse> getPrograms(boolean activeOnly) {
        return programRepository.findAllByOrderByCodeAsc()
                .stream()
                .filter(program -> !activeOnly || Boolean.TRUE.equals(program.getActive()))
                .map(this::toResponse)
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

        return toResponse(programRepository.save(program));
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

    private AcademicProgramResponse toResponse(AcademicProgram program) {
        return new AcademicProgramResponse(
                program.getProgramId(),
                program.getCode(),
                program.getName(),
                program.getDepartment(),
                program.getActive()
        );
    }
}
