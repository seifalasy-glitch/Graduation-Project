package com.edusync.edusync.backend.academic.repository;

import com.edusync.edusync.backend.academic.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByOrderByRoomCodeAsc();

    boolean existsByRoomCodeIgnoreCase(String roomCode);

    boolean existsByRoomCodeIgnoreCaseAndRoomIdNot(String roomCode, Long roomId);
}
