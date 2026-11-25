package com.edusync.edusync.backend.academic.repository;

import com.edusync.edusync.backend.academic.entity.AcademicProgram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcademicProgramRepository extends JpaRepository<AcademicProgram, Long> {

    List<AcademicProgram> findAllByOrderByCodeAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndProgramIdNot(String code, Long programId);
}
