package com.edusync.edusync.backend.enrollment.service;

import com.edusync.edusync.backend.admin.dto.BulkImportResult;
import com.edusync.edusync.backend.enrollment.dto.EnrollmentImportRow;
import com.edusync.edusync.backend.enrollment.dto.EnrollmentImportPlan;
import com.edusync.edusync.backend.admin.dto.ImportRowError;
import com.edusync.edusync.backend.academic.entity.AcademicProgram;
import com.edusync.edusync.backend.academic.entity.CourseOffering;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicProgramRepository;
import com.edusync.edusync.backend.academic.repository.CourseOfferingRepository;
import com.edusync.edusync.backend.enrollment.entity.Enrollment;
import com.edusync.edusync.backend.enrollment.entity.GroupMembership;
import com.edusync.edusync.backend.enrollment.entity.StudentGroup;
import com.edusync.edusync.backend.enrollment.repository.EnrollmentRepository;
import com.edusync.edusync.backend.enrollment.repository.GroupMembershipRepository;
import com.edusync.edusync.backend.enrollment.repository.StudentGroupRepository;
import com.edusync.edusync.backend.users.entity.User;
import com.edusync.edusync.backend.users.repository.UserRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EnrollmentImportService {
    private static final String[] COLUMNS = {"UniversityId", "ProgramCode", "SeatNo", "LectureGroup", "LabGroup"};
    private static final String ACTIVE = "ACTIVE";
    private static final String COMPLETED = "COMPLETED";
    private static final String STUDENT = "STUDENT";
    private static final String LECTURE = "LECTURE";
    private static final String PRACTICAL = "PRACTICAL";
    private static final String PREVIEW = "PREVIEW";
    private static final String COMMIT = "COMMIT";

    private final CourseOfferingRepository offerings;
    private final AcademicProgramRepository programs;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;
    private final StudentGroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final PeopleService people;

    public EnrollmentImportService(CourseOfferingRepository offerings, AcademicProgramRepository programs,
                                   UserRepository users, EnrollmentRepository enrollments, StudentGroupRepository groups,
                                   GroupMembershipRepository memberships, PeopleService people) {
        this.offerings = offerings;
        this.programs = programs;
        this.users = users;
        this.enrollments = enrollments;
        this.groups = groups;
        this.memberships = memberships;
        this.people = people;
    }

    @Transactional(readOnly = true)
    public byte[] buildTemplate(Long offeringId) {
        offerings.findById(offeringId).orElseThrow(AcademicErrors::offeringNotFound);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Enrollments");
            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                header.createCell(i).setCellValue(COLUMNS[i]);
                sheet.setColumnWidth(i, 25 * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not create the Excel template", e); }
    }

    // ===================== Part 2: preview or commit =====================

    @Transactional
    public BulkImportResult importEnrollments(Long offeringId, MultipartFile file, String mode) {
        if (!PREVIEW.equals(mode) && !COMMIT.equals(mode)) {
            throw new IllegalArgumentException("mode must be PREVIEW or COMMIT");
        }

        List<EnrollmentImportRow> rows = readRows(file);
        boolean commit = COMMIT.equals(mode);
        CourseOffering offering = (commit ? offerings.findByIdForUpdate(offeringId)
                : offerings.findById(offeringId)).orElseThrow(AcademicErrors::offeringNotFound);
        // COMMIT holds the offering lock before reading enrollments and memberships.
        ImportContext context = loadContext(offering);

        for (EnrollmentImportRow row : rows) {
            validateRow(row, context);
        }

        boolean saved = commit && context.errors.isEmpty();
        if (saved) {
            savePlans(offeringId, context);
        }

        return new BulkImportResult(mode, rows.size(), context.created, context.updated,
                context.unchanged, context.errors.size(), saved, context.errors);
    }

    private ImportContext loadContext(CourseOffering offering) {
        Long offeringId = offering.getOfferingId();
        List<Enrollment> existing = enrollments.findByOfferingIdOrderByEnrollmentIdAsc(offeringId);
        return new ImportContext(offering, existing,
                groups.findByOfferingIdOrderByGroupTypeAscLabelAsc(offeringId),
                programs.findAllByOrderByCodeAsc());
    }

    // ===================== Part 3: row validation =====================

    private void validateRow(EnrollmentImportRow row, ImportContext context) {
        int previousErrorCount = context.errors.size();
        User student = validateStudent(row, context);
        AcademicProgram program = validateProgram(row, context);
        Set<Long> desiredGroups = validateGroups(row, context);
        Enrollment existingEnrollment = student == null ? null
                : context.byStudent.get(student.getUserId());
        if (existingEnrollment != null && COMPLETED.equals(existingEnrollment.getStatus())) {
            error(context.errors, row, "UniversityId", "ENROLLMENT_COMPLETED",
                    "This enrollment is COMPLETED and cannot be changed by import");
        }
        String seatNumber = row.seat().isEmpty() ? null : row.seat();
        validateSeatNumber(row, seatNumber, existingEnrollment, context);

        boolean needsSeat = existingEnrollment == null || !ACTIVE.equals(existingEnrollment.getStatus());
        validateCapacity(row, needsSeat, context);
        List<GroupMembership> currentMemberships = validateMembershipPeriods(row, existingEnrollment, context);

        if (context.errors.size() != previousErrorCount) {
            return;
        }
        if (needsSeat) {
            context.occupied++;
        }

        Set<Long> currentGroups = currentMemberships.stream()
                .map(GroupMembership::getGroupId).collect(Collectors.toSet());
        boolean changed = existingEnrollment == null || needsSeat
                || !Objects.equals(existingEnrollment.getProgramId(), program.getProgramId())
                || !Objects.equals(existingEnrollment.getSeatNo(), seatNumber)
                || !currentGroups.equals(desiredGroups)
                || currentMemberships.size() != currentGroups.size();

        if (existingEnrollment == null) {
            context.created++;
        } else if (changed) {
            context.updated++;
        } else {
            context.unchanged++;
        }
        if (changed) {
            context.plans.add(new EnrollmentImportPlan(student.getUserId(), program.getProgramId(), seatNumber,
                    existingEnrollment, desiredGroups, currentMemberships));
        }
    }

    private User validateStudent(EnrollmentImportRow row, ImportContext context) {
        if (row.id().isEmpty()) {
            error(context.errors, row, "UniversityId", "MISSING_FIELD", "UniversityId is required");
            return null;
        }
        if (!context.seenIds.add(row.id())) {
            error(context.errors, row, "UniversityId", "DUPLICATE_IN_FILE", "This UniversityId appears in an earlier row");
        }
        User student = users.findByUniversityId(row.id()).orElse(null);
        if (student == null || !people.hasRole(student.getUserId(), STUDENT)) {
            error(context.errors, row, "UniversityId", "UNKNOWN_STUDENT", "Student not found");
            return null;
        }
        if (!ACTIVE.equals(student.getStatus())) {
            error(context.errors, row, "UniversityId", "STUDENT_DISABLED", "Cannot enroll a disabled student");
        }
        return student;
    }

    private AcademicProgram validateProgram(EnrollmentImportRow row, ImportContext context) {
        AcademicProgram program = context.programs.stream()
                .filter(p -> p.getCode().equalsIgnoreCase(row.program())).findFirst().orElse(null);
        if (program == null) {
            error(context.errors, row, "ProgramCode", "UNKNOWN_PROGRAM", "Academic program not found");
        }
        return program;
    }

    private Set<Long> validateGroups(EnrollmentImportRow row, ImportContext context) {
        Set<Long> desiredGroups = new HashSet<>();
        resolveGroup(row, "LectureGroup", row.lecture(), LECTURE, context.groups, desiredGroups, context.errors);
        resolveGroup(row, "LabGroup", row.lab(), PRACTICAL, context.groups, desiredGroups, context.errors);
        return desiredGroups;
    }

    private void validateSeatNumber(EnrollmentImportRow row, String seatNumber, Enrollment existingEnrollment,
                                    ImportContext context) {
        if (seatNumber == null) {
            return;
        }
        boolean duplicateInFile = !context.seenSeats.add(seatNumber.toUpperCase(Locale.ROOT));
        boolean duplicateInDatabase = context.existing.stream().anyMatch(e ->
                (existingEnrollment == null || !e.getEnrollmentId().equals(existingEnrollment.getEnrollmentId()))
                        && seatNumber.equalsIgnoreCase(e.getSeatNo()));
        if (duplicateInFile || duplicateInDatabase) {
            error(context.errors, row, "SeatNo", "DUPLICATE_SEAT_NO", "This seat number is already used");
        }
    }

    private void validateCapacity(EnrollmentImportRow row, boolean needsSeat, ImportContext context) {
        if (needsSeat && context.offering.getCapacity() != null
                && context.occupied >= context.offering.getCapacity()) {
            error(context.errors, row, "UniversityId", "OFFERING_FULL", "The offering has no free seats");
        }
    }

    private List<GroupMembership> validateMembershipPeriods(EnrollmentImportRow row, Enrollment existingEnrollment,
                                                            ImportContext context) {
        if (existingEnrollment == null) {
            return List.of();
        }
        List<GroupMembership> current = memberships.findByEnrollmentId(existingEnrollment.getEnrollmentId())
                .stream().filter(m -> m.getEndsAt() == null || m.getEndsAt().isAfter(context.now)).toList();
        if (current.stream().anyMatch(m -> !m.getStartsAt().isBefore(context.now))) {
            error(context.errors, row, "LectureGroup", "GROUP_PERIOD_CONFLICT",
                    "Resolve future group memberships before importing");
        }
        return current;
    }

    // ===================== Part 4: persistence =====================

    private void savePlans(Long offeringId, ImportContext context) {
        for (EnrollmentImportPlan plan : context.plans) {
            Enrollment enrollment = saveEnrollment(offeringId, plan);
            updateGroupMemberships(enrollment, plan, context.now);
        }
        enrollments.flush();
        memberships.flush();
    }

    private Enrollment saveEnrollment(Long offeringId, EnrollmentImportPlan plan) {
        Enrollment enrollment = plan.old() == null ? new Enrollment() : plan.old();
        enrollment.setStudentUserId(plan.studentId());
        enrollment.setOfferingId(offeringId);
        enrollment.setProgramId(plan.programId());
        enrollment.setSeatNo(plan.seat());
        enrollment.setStatus(ACTIVE);
        return enrollments.save(enrollment);
    }

    private void updateGroupMemberships(Enrollment enrollment, EnrollmentImportPlan plan, LocalDateTime now) {
        Set<Long> retainedGroups = new HashSet<>();
        for (GroupMembership membership : plan.current()) {
            if (plan.desired().contains(membership.getGroupId()) && retainedGroups.add(membership.getGroupId())) {
                continue;
            }
            if (!now.isAfter(membership.getStartsAt())) {
                throw new IllegalArgumentException("Membership end must be after its start");
            }
            membership.setEndsAt(now);
            memberships.save(membership);
        }
        memberships.flush();
        for (Long groupId : plan.desired()) {
            if (retainedGroups.contains(groupId)) {
                continue;
            }
            GroupMembership membership = new GroupMembership();
            membership.setEnrollmentId(enrollment.getEnrollmentId());
            membership.setGroupId(groupId);
            membership.setStartsAt(now);
            membership.setEndsAt(null);
            memberships.save(membership);
        }
    }

    private void resolveGroup(EnrollmentImportRow row, String column, String label, String type,
                              List<StudentGroup> available, Set<Long> desired, List<ImportRowError> errors) {
        if (label.isEmpty()) {
            if (available.stream().anyMatch(g -> type.equals(g.getGroupType())))
                error(errors,row,column,"MISSING_FIELD",column + " is required for this offering");
            return;
        }
        StudentGroup group = available.stream().filter(g -> type.equals(g.getGroupType()) && label.equalsIgnoreCase(g.getLabel())).findFirst().orElse(null);
        if (group == null) error(errors,row,column,"UNKNOWN_GROUP","Group not found in this offering");
        else desired.add(group.getGroupId());
    }

    // ===================== Part 5: Excel parsing =====================

    private List<EnrollmentImportRow> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }

        List<EnrollmentImportRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (XSSFWorkbook workbook = openWorkbook(file)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(
                        "The first row must hold the column names. Download the template"
                );
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);

            if (header == null) {
                throw new IllegalArgumentException(
                        "The first row must hold the column names. Download the template"
                );
            }

            for (int i = 0; i < COLUMNS.length; i++) {
                if (!COLUMNS[i].equalsIgnoreCase(
                        formatter.formatCellValue(header.getCell(i)).trim())) {

                    throw new IllegalArgumentException(
                            "Column " + (i + 1) + " must be " + COLUMNS[i]
                                    + ". Download the template and keep its columns"
                    );
                }
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                String[] values = new String[COLUMNS.length];

                for (int j = 0; j < COLUMNS.length; j++) {
                    values[j] = formatter.formatCellValue(row.getCell(j)).trim();
                }

                if (Arrays.stream(values).allMatch(String::isEmpty)) {
                    continue;
                }

                rows.add(new EnrollmentImportRow(
                        i + 1,
                        values[0],
                        values[1],
                        values[2],
                        values[3],
                        values[4]
                ));
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not read the file. Upload an Excel (.xlsx) file made from the template",
                    e
            );
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "The file has no rows under the column names"
            );
        }

        return rows;
    }

    private XSSFWorkbook openWorkbook(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return new XSSFWorkbook(in);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not read the file. Upload an Excel (.xlsx) file made from the template",
                    e
            );
        }
    }

    private void error(List<ImportRowError> errors, EnrollmentImportRow row, String column, String code, String message) {
        errors.add(new ImportRowError(row.number(), column, code, message));
    }
    private static final class ImportContext {
        private final CourseOffering offering;
        private final List<Enrollment> existing;
        private final Map<Long, Enrollment> byStudent;
        private final List<StudentGroup> groups;
        private final List<AcademicProgram> programs;
        private final List<ImportRowError> errors = new ArrayList<>();
        private final List<EnrollmentImportPlan> plans = new ArrayList<>();
        private final Set<String> seenIds = new HashSet<>();
        private final Set<String> seenSeats = new HashSet<>();
        private final LocalDateTime now = LocalDateTime.now();
        private long occupied;
        private int created;
        private int updated;
        private int unchanged;

        private ImportContext(CourseOffering offering, List<Enrollment> existing,
                              List<StudentGroup> groups, List<AcademicProgram> programs) {
            this.offering = offering;
            this.existing = existing;
            this.groups = groups;
            this.programs = programs;
            this.byStudent = existing.stream().collect(Collectors.toMap(Enrollment::getStudentUserId, e -> e));
            this.occupied = existing.stream().filter(e -> ACTIVE.equals(e.getStatus())).count();
        }
    }

}
