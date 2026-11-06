package com.edusync.edusync.backend.auth.ratelimit;

import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ForgotPasswordRateLimiter {

    private static final int MAX_REQUESTS = 5;

    private static final Duration WINDOW =
            Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, RequestWindow> requests =
            new ConcurrentHashMap<>();

    public boolean isAllowed(String ipAddress) {

        Instant now = Instant.now();

        AtomicBoolean allowed = new AtomicBoolean(false);

        requests.compute(ipAddress, (ip, current) -> {

            if (current == null ||
                    !now.isBefore(current.startedAt().plus(WINDOW))) {

                allowed.set(true);
                return new RequestWindow(now, 1);
            }

            if (current.count() >= MAX_REQUESTS) {
                allowed.set(false);
                return current;
            }

            allowed.set(true);

            return new RequestWindow(
                    current.startedAt(),
                    current.count() + 1
            );
        });

        return allowed.get();
    }

    private record RequestWindow(
            Instant startedAt,
            int count
    ) {
    }
}
