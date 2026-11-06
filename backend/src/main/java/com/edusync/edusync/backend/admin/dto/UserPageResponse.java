package com.edusync.edusync.backend.admin.dto;

import java.util.List;

public record UserPageResponse(
        List<UserResponse> items,
        PageMeta page
) {
}
