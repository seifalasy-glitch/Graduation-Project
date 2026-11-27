package com.edusync.edusync.backend.enrollment.exception;

import com.edusync.edusync.backend.common.ApiException;
import org.springframework.http.HttpStatus;

public final class EnrollmentErrors {

    private EnrollmentErrors() {
    }

    public static ApiException userNotFound() {
        return of(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
    }

    public static ApiException enrollmentNotFound() {
        return of(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found");
    }

    public static ApiException groupNotFound() {
        return of(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Group not found");
    }

    public static ApiException membershipNotFound() {
        return of(HttpStatus.NOT_FOUND, "MEMBERSHIP_NOT_FOUND", "Group membership not found");
    }

    public static ApiException assignmentNotFound() {
        return of(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "Teaching assignment not found");
    }

    public static ApiException alreadyEnrolled() {
        return of(HttpStatus.CONFLICT, "ALREADY_ENROLLED",
                "The student is already enrolled in this offering");
    }

    public static ApiException offeringFull() {
        return of(HttpStatus.CONFLICT, "OFFERING_FULL",
                "The offering has no free seats");
    }

    public static ApiException groupExists() {
        return of(HttpStatus.CONFLICT, "GROUP_EXISTS",
                "A group with this type and label already exists in the offering");
    }

    public static ApiException alreadyMember() {
        return of(HttpStatus.CONFLICT, "ALREADY_MEMBER",
                "The student already has a membership in this group");
    }

    public static ApiException alreadyMemberInAnotherGroup() {
        return of(HttpStatus.CONFLICT, "ALREADY_MEMBER",
                "The student already has an overlapping membership in another group of the same type");
    }

    public static ApiException alreadyAssigned() {
        return of(HttpStatus.CONFLICT, "ALREADY_ASSIGNED",
                "The doctor already teaches this group");
    }

    public static ApiException notAStudent() {
        return of(HttpStatus.CONFLICT, "NOT_A_STUDENT",
                "This user is not a student");
    }

    public static ApiException notADoctor() {
        return of(HttpStatus.CONFLICT, "NOT_A_DOCTOR",
                "This user is not a doctor");
    }

    public static ApiException doctorDisabled() {
        return of(HttpStatus.CONFLICT, "DOCTOR_DISABLED",
                "Cannot assign a disabled doctor"
        );
    }

    public static ApiException wrongOffering() {
        return of(HttpStatus.CONFLICT, "WRONG_OFFERING",
                "The enrollment belongs to another offering");
    }

    public static ApiException notYourCourse() {
        return of(HttpStatus.FORBIDDEN, "NOT_YOUR_COURSE",
                "You do not teach this course");
    }

    public static ApiException studentDisabled() {
        return of(HttpStatus.CONFLICT, "STUDENT_DISABLED",
                "Cannot enroll a disabled student"
        );
    }

    private static ApiException of(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
