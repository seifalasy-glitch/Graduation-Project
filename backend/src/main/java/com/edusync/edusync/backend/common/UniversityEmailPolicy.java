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
        // trim: the property in application.properties may contain trailing spaces
        this.domain = domain.trim().toLowerCase(Locale.ROOT);
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
