package com.edusync.edusync.backend.registration.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveRegistrationRequest(
        @NotNull Long termId,
        @NotNull List<Long> offeringIds
) {
}
