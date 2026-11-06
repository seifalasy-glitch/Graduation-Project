package com.edusync.edusync.backend.admin.dto;

public record PageMeta(
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
