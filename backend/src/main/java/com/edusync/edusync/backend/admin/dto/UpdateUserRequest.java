package com.edusync.edusync.backend.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateUserRequest(

        @Email
        String email,

        String fullName,

        String fullNameAr,

        List<@NotBlank String> roles,

        String status

) {
}
