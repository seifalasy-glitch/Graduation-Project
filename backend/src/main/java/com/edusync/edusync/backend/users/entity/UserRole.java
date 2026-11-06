package com.edusync.edusync.backend.users.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_role", schema = "public")
@IdClass(UserRoleId.class)
@Getter
@Setter
public class UserRole {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "role", length = 30)
    private String role;
}