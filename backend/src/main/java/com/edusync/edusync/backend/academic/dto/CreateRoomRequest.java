package com.edusync.edusync.backend.academic.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoomRequest(

        @NotBlank(message = "Room code is required")
        String roomCode,

        String name,

        @NotBlank(message = "Building is required")
        String building,

        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        Integer capacity,

        @NotBlank(message = "Network mode is required")
        String networkMode,

        String ssid,

        String routerMac,

        String subnet

) {
}
