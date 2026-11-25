package com.edusync.edusync.backend.academic.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAcademicProgramRequest(

        @NotBlank(message = "Program code is required")
        String code,

        @NotBlank(message = "Program name is required")
        String name,

        String department,

        Boolean active

) {
}