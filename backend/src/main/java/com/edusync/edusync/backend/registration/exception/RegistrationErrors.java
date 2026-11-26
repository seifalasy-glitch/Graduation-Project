package com.edusync.edusync.backend.registration.exception;

import com.edusync.edusync.backend.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RegistrationErrors {

    private RegistrationErrors() {
    }

    public static ApiException requestNotFound() {
        return of(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "Registration request not found");
    }

    public static ApiException profileNotFound() {
        return of(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Student profile not found");
    }

    public static ApiException planCourseNotFound() {
        return of(HttpStatus.NOT_FOUND, "PLAN_COURSE_NOT_FOUND", "Plan course not found");
    }

    public static ApiException profileIncomplete() {
        return of(HttpStatus.CONFLICT, "PROFILE_INCOMPLETE",
                "Your program, level and credit limit are not set yet. Contact the administration");
    }

    public static ApiException alreadyInPlan() {
        return of(HttpStatus.CONFLICT, "ALREADY_IN_PLAN",
                "This course is already in the plan for this level and term type");
    }

    public static ApiException notAvailable(Long offeringId) {
        return new ApiException(HttpStatus.CONFLICT, "NOT_AVAILABLE",
                "This offering is not available for you in this term",
                Map.of("offeringId", offeringId));
    }

    public static ApiException sameCourseTwice(String courseCode) {
        return of(HttpStatus.CONFLICT, "SAME_COURSE_TWICE",
                "Only one section of " + courseCode + " can be chosen");
    }

    public static ApiException creditLimitExceeded(int total, int max) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("totalCredits", total);
        details.put("maxCreditHours", max);

        return new ApiException(HttpStatus.CONFLICT, "CREDIT_LIMIT_EXCEEDED",
                "Total credit hours (" + total + ") exceed your limit (" + max + ")", details);
    }

    public static ApiException scheduleClash(List<String> courses) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("courses", courses);

        return new ApiException(HttpStatus.CONFLICT, "SCHEDULE_CLASH",
                String.join(", ", courses) + " cannot be attended without overlapping times", details);
    }

    public static ApiException advisorDisabled() {
        return of(HttpStatus.CONFLICT, "ADVISOR_DISABLED",
                "Your academic advisor account is disabled. Contact the administration");
    }

    public static ApiException alreadySubmitted() {
        return of(HttpStatus.CONFLICT, "ALREADY_SUBMITTED",
                "The request was already submitted and can no longer be edited");
    }

    public static ApiException emptyRequest() {
        return of(HttpStatus.CONFLICT, "EMPTY_REQUEST", "Choose at least one course first");
    }

    public static ApiException noAdvisor() {
        return of(HttpStatus.CONFLICT, "NO_ADVISOR",
                "You have no academic advisor yet. Contact the administration");
    }

    public static ApiException notYourAdvisee() {
        return of(HttpStatus.FORBIDDEN, "NOT_YOUR_ADVISEE", "This student is not your advisee");
    }

    public static ApiException wrongState(String expected) {
        return of(HttpStatus.CONFLICT, "WRONG_STATE",
                "The request must be " + expected + " for this action");
    }

    private static ApiException of(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
