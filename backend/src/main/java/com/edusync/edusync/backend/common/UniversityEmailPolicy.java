package com.edusync.edusync.backend.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * One place that decides what a university email is, so the user form and
 * the Excel import follow the same rule.
 */
@Component
public class UniversityEmailPolicy {

    private final String domain;

    public UniversityEmailPolicy(
            @Value("${app.university-email-domain:sci.asu.edu.eg}") String domain
    ) {
        // trim: an env var set by hand (UNIVERSITY_EMAIL_DOMAIN) can carry stray spaces
        this.domain = domain.trim().toLowerCase(Locale.ROOT);
    }

    /** The domain without the "@", lower case, e.g. "sci.asu.edu.eg" (used in error messages). */
    public String domain() {
        return domain;
    }

    public boolean isUniversityEmail(String email) {
        return email != null
                && email.trim().toLowerCase(Locale.ROOT).endsWith("@" + domain);
    }

    public void requireUniversityEmail(String email) {
        if (!isUniversityEmail(email)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Email must be a university email (@" + domain + ")",
                    ApiException.fieldDetails("email", "must be a university email")
            );
        }
    }
}
