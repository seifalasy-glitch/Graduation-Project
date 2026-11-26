package com.edusync.edusync.backend.registration.dto;

import jakarta.validation.constraints.NotNull;

public record SubmitRegistrationRequest(@NotNull Long termId) {
}
