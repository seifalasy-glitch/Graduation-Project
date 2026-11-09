package com.edusync.edusync.backend.admin.dto;

import java.util.List;


public record BulkImportResult (
        String mode,
        int totalRows,
        int toCreate,
        int toUpdate,
        int unchanged,
        int errorCount,
        boolean saved,
        List<ImportRowError>errors
){
}