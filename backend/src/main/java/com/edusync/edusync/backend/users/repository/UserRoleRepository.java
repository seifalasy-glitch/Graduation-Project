package com.edusync.edusync.backend.users.repository;

import com.edusync.edusync.backend.users.entity.UserRole;
import com.edusync.edusync.backend.users.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Long userId);
}