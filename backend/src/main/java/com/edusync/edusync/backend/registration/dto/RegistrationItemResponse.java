package com.edusync.edusync.backend.registration.dto;

public record RegistrationItemResponse(
        Long itemId,
        Long offeringId,
        String courseCode,
        String courseTitle,
        String section,
        Integer creditHours,
        String status
) {
}
