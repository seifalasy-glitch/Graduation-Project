package com.edusync.edusync.backend.registration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationDecisionRequest(
        @NotBlank String decision,
        @Size(max = 1000) String note
) {
}
