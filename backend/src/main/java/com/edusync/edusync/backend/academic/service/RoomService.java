package com.edusync.edusync.backend.academic.service;

import com.edusync.edusync.backend.academic.dto.CreateRoomRequest;
import com.edusync.edusync.backend.academic.dto.RoomResponse;
import com.edusync.edusync.backend.academic.dto.UpdateRoomRequest;
import com.edusync.edusync.backend.academic.entity.Room;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RoomService {

    // API contract (RoomInput.networkMode)
    private static final Set<String> NETWORK_MODES = Set.of("LOCAL_ROUTER", "CAMPUS_WIFI");

    private static final Pattern MAC =
            Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$");

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> getRooms() {
        return roomRepository.findAllByOrderByRoomCodeAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {

        String roomCode = request.roomCode().trim();
        String building = request.building().trim();

        if (roomCode.isBlank()) {
            throw AcademicErrors.validation("roomCode", "Room code cannot be blank");
        }

        if (building.isBlank()) {
            throw AcademicErrors.validation("building", "Building cannot be blank");
        }

        validateCapacity(request.capacity());

        if (roomRepository.existsByRoomCodeIgnoreCase(roomCode)) {
            throw AcademicErrors.roomCodeExists();
        }

        Room room = new Room();

        room.setRoomCode(roomCode);
        // The database requires a name, the API does not: the room code is the default name
        room.setName(
                request.name() == null || request.name().isBlank()
                        ? roomCode
                        : request.name().trim()
        );
        room.setBuilding(building);
        room.setCapacity(request.capacity());

        applyNetwork(
                room,
                request.networkMode(),
                request.ssid(),
                request.routerMac(),
                request.subnet()
        );

        return toResponse(roomRepository.save(room));
    }

    @Transactional
    public RoomResponse updateRoom(Long roomId, UpdateRoomRequest request) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> AcademicErrors.roomNotFound());

        if (request.roomCode() != null) {

            String roomCode = request.roomCode().trim();

            if (roomCode.isBlank()) {
                throw AcademicErrors.validation("roomCode", "Room code cannot be blank");
            }

            if (roomRepository.existsByRoomCodeIgnoreCaseAndRoomIdNot(roomCode, roomId)) {
                throw AcademicErrors.roomCodeExists();
            }

            room.setRoomCode(roomCode);
        }

        if (request.name() != null) {

            String name = request.name().trim();

            if (name.isBlank()) {
                throw AcademicErrors.validation("name", "Room name cannot be blank");
            }

            room.setName(name);
        }

        if (request.building() != null) {

            String building = request.building().trim();

            if (building.isBlank()) {
                throw AcademicErrors.validation("building", "Building cannot be blank");
            }

            room.setBuilding(building);
        }

        if (request.capacity() != null) {
            validateCapacity(request.capacity());
            room.setCapacity(request.capacity());
        }

        boolean networkChanged = request.networkMode() != null
                || request.ssid() != null
                || request.routerMac() != null
                || request.subnet() != null;

        if (networkChanged) {
            // Mix what was sent with what the room already has, then check the whole result
            applyNetwork(
                    room,
                    request.networkMode() != null ? request.networkMode() : room.getNetworkMode(),
                    request.ssid() != null ? request.ssid() : room.getSsid(),
                    request.routerMac() != null ? request.routerMac() : room.getRouterMac(),
                    request.subnet() != null ? request.subnet() : room.getSubnet()
            );
        }

        return toResponse(roomRepository.save(room));
    }

    private void validateCapacity(Integer capacity) {
        if (capacity == null || capacity < 1) {
            throw AcademicErrors.validation("capacity", "Capacity must be at least 1");
        }
    }

    /**
     * LOCAL_ROUTER rooms are recognised by their router (ssid + routerMac);
     * CAMPUS_WIFI rooms by the subnet. The fields of the other mode are cleared.
     */
    private void applyNetwork(
            Room room,
            String networkMode,
            String ssid,
            String routerMac,
            String subnet
    ) {
        if (networkMode == null || networkMode.isBlank()) {
            throw AcademicErrors.validation("networkMode", "Network mode is required");
        }

        String mode = networkMode.trim().toUpperCase(Locale.ROOT);

        if (!NETWORK_MODES.contains(mode)) {
            throw AcademicErrors.validation(
                    "networkMode",
                    "Network mode must be LOCAL_ROUTER or CAMPUS_WIFI"
            );
        }

        if (mode.equals("LOCAL_ROUTER")) {

            if (ssid == null || ssid.isBlank()) {
                throw AcademicErrors.validation(
                        "ssid",
                        "SSID is required for LOCAL_ROUTER rooms"
                );
            }

            if (routerMac == null || !MAC.matcher(routerMac.trim()).matches()) {
                throw AcademicErrors.validation(
                        "routerMac",
                        "Router MAC must look like 3C:84:6A:1F:22:B0"
                );
            }

            room.setSsid(ssid.trim());
            room.setRouterMac(routerMac.trim().toUpperCase(Locale.ROOT).replace('-', ':'));
            room.setSubnet(null);

        } else {

            if (subnet == null || subnet.isBlank()) {
                throw AcademicErrors.validation(
                        "subnet",
                        "Subnet is required for CAMPUS_WIFI rooms"
                );
            }

            if (!isValidCidr(subnet.trim())) {
                throw AcademicErrors.validation(
                        "subnet",
                        "Subnet must look like 10.12.3.0/24"
                );
            }

            room.setSubnet(subnet.trim());
            room.setSsid(null);
            room.setRouterMac(null);
        }

        room.setNetworkMode(mode);
    }

    private boolean isValidCidr(String value) {
        String[] parts = value.split("/");

        if (parts.length != 2) {
            return false;
        }

        try {
            int prefix = Integer.parseInt(parts[1]);

            if (prefix < 0 || prefix > 32) {
                return false;
            }

            String[] octets = parts[0].split("\\.");

            if (octets.length != 4) {
                return false;
            }

            for (String octet : octets) {
                int number = Integer.parseInt(octet);

                if (number < 0 || number > 255) {
                    return false;
                }
            }

            return true;

        } catch (NumberFormatException e) {
            return false;
        }
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getRoomId(),
                room.getRoomCode(),
                room.getName(),
                room.getBuilding(),
                room.getCapacity(),
                room.getNetworkMode(),
                room.getSsid(),
                room.getRouterMac(),
                room.getSubnet()
        );
    }
}
