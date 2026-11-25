package com.edusync.edusync.backend.academic.dto;

public record RoomResponse(
        Long roomId,
        String roomCode,
        String name,
        String building,
        Integer capacity,
        String networkMode,
        String ssid,
        String routerMac,
        String subnet
) {
}
