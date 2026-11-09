package com.edusync.edusync.backend.admin.dto;

public record ImportRowError(
        int rowNo,
        String column,
        String code,
        String message
) {
}