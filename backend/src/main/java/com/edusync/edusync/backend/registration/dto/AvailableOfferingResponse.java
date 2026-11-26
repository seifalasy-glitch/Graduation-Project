package com.edusync.edusync.backend.registration.dto;

import com.edusync.edusync.backend.academic.dto.AcademicCourseResponse;

import java.util.List;

public record AvailableOfferingResponse(
        Long offeringId,
        AcademicCourseResponse course,
        String section,
        Integer capacity,
        Integer seatsLeft,
        List<String> doctorNames,
        List<ScheduleSlot> schedule,
        boolean alreadyChosen
) {
}
