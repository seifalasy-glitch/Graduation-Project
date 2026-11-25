package com.edusync.edusync.backend.academic.dto;

import jakarta.validation.constraints.Min;

public record UpdateRoomRequest(

        String roomCode,

        String name,

        String building,

        @Min(value = 1, message = "Capacity must be at least 1")
        Integer capacity,

        String networkMode,

        String ssid,

        String routerMac,

        String subnet

) {
}
