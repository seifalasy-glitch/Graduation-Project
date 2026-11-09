package com.edusync.edusync.backend.admin.service;

import com.edusync.edusync.backend.admin.dto.BulkImportResult;
import com.edusync.edusync.backend.admin.dto.ImportRowError;
import com.edusync.edusync.backend.auth.service.EmailService;
import com.edusync.edusync.backend.users.entity.User;
import com.edusync.edusync.backend.users.entity.UserRole;
import com.edusync.edusync.backend.users.repository.UserRepository;
import com.edusync.edusync.backend.users.repository.UserRoleRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserImportService {

    private static final Logger log = LoggerFactory.getLogger(UserImportService.class);

    // The 5 columns of the Excel file, in this exact order (from the API contract)
    private static final String[] COLUMNS =
            {"UniversityId", "Email", "FullName", "FullNameAr", "Role"};

    // ADMIN accounts are never created from Excel
    private static final List<String> IMPORT_ROLES = List.of("STUDENT", "DOCTOR");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final EmailService emailService;
    private final String universityEmailDomain;

    public UserImportService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            TemporaryPasswordGenerator passwordGenerator,
            EmailService emailService,
            @Value("${app.university-email-domain:}") String universityEmailDomain
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.emailService = emailService;
        this.universityEmailDomain = universityEmailDomain.trim().toLowerCase(Locale.ROOT);
    }

    // ===================== Part 1: the empty template =====================

    public byte[] buildTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Users");
            Row header = sheet.createRow(0);

            for (int i = 0; i < COLUMNS.length; i++) {
                header.createCell(i).setCellValue(COLUMNS[i]);
                sheet.setColumnWidth(i, 25 * 256);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Could not create the Excel template", e);
        }
    }

    // ===================== Part 2: preview or commit =====================

    @Transactional
    public BulkImportResult importUsers(MultipartFile file, String mode) {

        if (!"PREVIEW".equals(mode) && !"COMMIT".equals(mode)) {
            throw new IllegalArgumentException("mode must be PREVIEW or COMMIT");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }

        List<ImportRow> rows = readRows(file);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The file has no rows under the column names");
        }

        List<ImportRowError> errors = new ArrayList<>();
        List<ImportRow> newRows = new ArrayList<>();
        List<PendingUpdate> updates = new ArrayList<>();
        Set<String> idsInFile = new HashSet<>();
        Set<String> emailsInFile = new HashSet<>();
        int unchanged = 0;

        for (ImportRow row : rows) {

            List<ImportRowError> rowErrors = checkRow(row, idsInFile, emailsInFile);

            if (!rowErrors.isEmpty()) {
                errors.addAll(rowErrors);
                continue;
            }

            Optional<User> existing = userRepository.findByUniversityId(row.universityId());

            if (existing.isPresent()) {
                // Same UniversityId already in the system: only the names can change
                User user = existing.get();

                if (sameNames(user, row)) {
                    unchanged++;
                } else {
                    updates.add(new PendingUpdate(user, row));
                }

            } else if (userRepository.findByEmail(row.email()).isPresent()) {
                errors.add(error(row, "Email", "EMAIL_TAKEN",
                        "This email already belongs to another account"));

            } else {
                newRows.add(row);
            }
        }

        boolean commit = "COMMIT".equals(mode);

        // PREVIEW never saves. COMMIT saves nothing if even one row has an error.
        if (!commit || !errors.isEmpty()) {
            return result(mode, rows.size(), newRows.size(), updates.size(),
                    unchanged, errors, false);
        }

        List<WelcomeEmail> emails = new ArrayList<>();

        for (ImportRow row : newRows) {
            String temporaryPassword = passwordGenerator.generate();

            User user = new User();
            user.setUniversityId(row.universityId());
            user.setEmail(row.email());
            user.setFullName(row.fullName());
            user.setFullNameAr(row.fullNameAr());
            user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
            user.setStatus("ACTIVE");
            user.setMustChangePassword(true);
            user.setTokenVersion(0);
            user.setCreatedAt(LocalDateTime.now());

            User savedUser = userRepository.save(user);

            UserRole userRole = new UserRole();
            userRole.setUserId(savedUser.getUserId());
            userRole.setRole(row.role());
            userRoleRepository.save(userRole);

            emails.add(new WelcomeEmail(row.email(), row.fullName(), temporaryPassword));
        }

        for (PendingUpdate update : updates) {
            update.user().setFullName(update.row().fullName());

            if (update.row().fullNameAr() != null) {
                update.user().setFullNameAr(update.row().fullNameAr());
            }

            userRepository.save(update.user());
        }

        sendEmailsAfterSave(emails);

        return result(mode, rows.size(), newRows.size(), updates.size(),
                unchanged, errors, true);
    }

    // ===================== Reading the Excel file =====================

    private List<ImportRow> readRows(MultipartFile file) {
        DataFormatter formatter = new DataFormatter();
        List<ImportRow> rows = new ArrayList<>();

        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            checkHeader(sheet.getRow(0), formatter);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);

                if (row == null) {
                    continue;
                }

                String universityId = cellText(row, 0, formatter);
                String email = cellText(row, 1, formatter).toLowerCase(Locale.ROOT);
                String fullName = cellText(row, 2, formatter);
                String fullNameAr = cellText(row, 3, formatter);
                String role = cellText(row, 4, formatter).toUpperCase(Locale.ROOT);

                boolean blankRow = universityId.isEmpty() && email.isEmpty()
                        && fullName.isEmpty() && fullNameAr.isEmpty() && role.isEmpty();

                if (blankRow) {
                    continue;
                }

                rows.add(new ImportRow(
                        i + 1, // the row number the admin sees in Excel
                        universityId,
                        email,
                        fullName,
                        fullNameAr.isEmpty() ? null : fullNameAr,
                        role
                ));
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Could not read the file. Upload an Excel (.xlsx) file made from the template");
        }

        return rows;
    }

    private void checkHeader(Row header, DataFormatter formatter) {
        if (header == null) {
            throw new IllegalArgumentException(
                    "The first row must hold the column names. Download the template");
        }

        for (int i = 0; i < COLUMNS.length; i++) {
            String name = formatter.formatCellValue(header.getCell(i)).trim();

            if (!COLUMNS[i].equalsIgnoreCase(name)) {
                throw new IllegalArgumentException(
                        "Column " + (i + 1) + " must be " + COLUMNS[i]
                                + ". Download the template and keep its columns");
            }
        }
    }

    private String cellText(Row row, int index, DataFormatter formatter) {
        return formatter.formatCellValue(row.getCell(index)).trim();
    }

    // ===================== Checking one row =====================

    private List<ImportRowError> checkRow(
            ImportRow row,
            Set<String> idsInFile,
            Set<String> emailsInFile
    ) {
        List<ImportRowError> errors = new ArrayList<>();

        if (row.universityId().isEmpty()) {
            errors.add(error(row, "UniversityId", "MISSING_FIELD", "UniversityId is required"));
        }
        if (row.email().isEmpty()) {
            errors.add(error(row, "Email", "MISSING_FIELD", "Email is required"));
        }
        if (row.fullName().isEmpty()) {
            errors.add(error(row, "FullName", "MISSING_FIELD", "FullName is required"));
        }
        if (row.role().isEmpty()) {
            errors.add(error(row, "Role", "MISSING_FIELD", "Role is required"));
        }

        if (!row.email().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(row.email()).matches()) {
                errors.add(error(row, "Email", "INVALID_EMAIL", "This is not a valid email"));
            } else if (!universityEmailDomain.isEmpty()
                    && !row.email().endsWith("@" + universityEmailDomain)) {
                errors.add(error(row, "Email", "NOT_UNIVERSITY_EMAIL",
                        "The email must end with @" + universityEmailDomain));
            }
        }

        if (!row.role().isEmpty() && !IMPORT_ROLES.contains(row.role())) {
            errors.add(error(row, "Role", "UNKNOWN_ROLE", "Role must be STUDENT or DOCTOR"));
        }

        // Set.add returns false when the value was already in the set
        if (!row.universityId().isEmpty() && !idsInFile.add(row.universityId())) {
            errors.add(error(row, "UniversityId", "DUPLICATE_IN_FILE",
                    "This UniversityId appears in an earlier row"));
        }
        if (!row.email().isEmpty() && !emailsInFile.add(row.email())) {
            errors.add(error(row, "Email", "DUPLICATE_IN_FILE",
                    "This email appears in an earlier row"));
        }

        return errors;
    }

    private boolean sameNames(User user, ImportRow row) {
        boolean sameName = Objects.equals(user.getFullName(), row.fullName());
        boolean sameArabicName = row.fullNameAr() == null
                || Objects.equals(user.getFullNameAr(), row.fullNameAr());
        return sameName && sameArabicName;
    }

    // ===================== Emails, only after a successful save =====================

    private void sendEmailsAfterSave(List<WelcomeEmail> emails) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (WelcomeEmail email : emails) {
                    try {
                        emailService.sendWelcomeEmail(
                                email.to(), email.fullName(), email.temporaryPassword());
                    } catch (Exception e) {
                        // The account is saved anyway; the admin can reset the password later
                        log.warn("Could not send the welcome email to {}: {}",
                                email.to(), e.getMessage());
                    }
                }
            }
        });
    }

    // ===================== Small helpers =====================

    private ImportRowError error(ImportRow row, String column, String code, String message) {
        return new ImportRowError(row.rowNo(), column, code, message);
    }

    private BulkImportResult result(
            String mode,
            int totalRows,
            int toCreate,
            int toUpdate,
            int unchanged,
            List<ImportRowError> errors,
            boolean saved
    ) {
        return new BulkImportResult(mode, totalRows, toCreate, toUpdate, unchanged,
                errors.size(), saved, errors);
    }

    private record ImportRow(
            int rowNo,
            String universityId,
            String email,
            String fullName,
            String fullNameAr,
            String role
    ) {
    }

    private record PendingUpdate(User user, ImportRow row) {
    }

    private record WelcomeEmail(String to, String fullName, String temporaryPassword) {
    }
}