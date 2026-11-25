package com.edusync.edusync.backend.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The database stores timestamps without a timezone (LocalDateTime written with LocalDateTime.now()).
 * The API sends them with a timezone (ISO-8601 UTC, e.g. 2026-11-21T16:30:00Z) so the browser shows
 * the right time for every user.
 */
public final class DateTimeUtil {

    private DateTimeUtil() {
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
