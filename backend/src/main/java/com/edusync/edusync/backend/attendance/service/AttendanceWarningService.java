package com.edusync.edusync.backend.attendance.service;

import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.attendance.AttendanceErrors;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.WarningResponse;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.WarningRuleInput;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.WarningRuleResponse;
import com.edusync.edusync.backend.attendance.repository.AttendanceStatsQueries;
import com.edusync.edusync.backend.attendance.repository.WarningQueries;
import com.edusync.edusync.backend.attendance.repository.WarningQueries.Absences;
import com.edusync.edusync.backend.attendance.repository.WarningQueries.CourseInfo;
import com.edusync.edusync.backend.attendance.repository.WarningQueries.GroupInfo;
import com.edusync.edusync.backend.attendance.repository.WarningQueries.RuleRow;
import com.edusync.edusync.backend.attendance.repository.WarningQueries.WarningRow;
import com.edusync.edusync.backend.common.AfterCommitMailer;
import com.edusync.edusync.backend.common.DateTimeUtil;
import com.edusync.edusync.backend.enrollment.dto.StudentRef;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.registration.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Attendance warnings (phase C1). Per course the doctor turns warnings on or off separately
 * for lectures and labs and sets an absence limit. Whenever a session of that type is
 * finished or corrected, or the rule is saved turned on, the course is re-checked:
 * a student whose absences reach the limit for the first time gets ONE warning row and
 * ONE email (never repeated for the same rule, even if the limit changes later).
 *
 * Absences = ABSENT + unmarked (finished sessions) + 1 per 2 LATE (see WarningQueries).
 *
 * Who: read rules / warnings = admin or a doctor who teaches / taught the course;
 * change a rule = a doctor teaching the course now. Others get NOT_YOUR_COURSE (403).
 */
@Service
public class AttendanceWarningService {

    static final List<String> GROUP_TYPES = List.of("LECTURE", "PRACTICAL");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final WarningQueries queries;
    private final AttendanceStatsQueries stats;
    private final CurrentUserService currentUser;
    private final AfterCommitMailer mailer;
    private final JavaMailSender mailSender;
    private final String senderEmail;

    public AttendanceWarningService(
            WarningQueries queries,
            AttendanceStatsQueries stats,
            CurrentUserService currentUser,
            AfterCommitMailer mailer,
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String senderEmail
    ) {
        this.queries = queries;
        this.stats = stats;
        this.currentUser = currentUser;
        this.mailer = mailer;
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
    }

    // ================================================================ rules

    /** One rule per group type that exists in the course; never-saved types come back off (ruleId null). */
    @Transactional(readOnly = true)
    public List<WarningRuleResponse> rules(long offeringId) {
        requireReadable(offeringId);
        Map<String, RuleRow> saved = queries.rules(offeringId).stream()
                .collect(Collectors.toMap(RuleRow::groupType, r -> r));
        return queries.groupTypesOfOffering(offeringId).stream()
                .map(type -> saved.containsKey(type)
                        ? toResponse(saved.get(type))
                        : new WarningRuleResponse(null, offeringId, type, false, null, null))
                .toList();
    }

    @Transactional
    public WarningRuleResponse saveRule(long offeringId, String groupTypeText, WarningRuleInput input) {
        String groupType = groupTypeText.trim().toUpperCase(Locale.ROOT);
        if (!GROUP_TYPES.contains(groupType)) {
            throw AcademicErrors.validation("groupType", "groupType must be LECTURE or PRACTICAL");
        }
        if (!stats.offeringExists(offeringId)) {
            throw AcademicErrors.offeringNotFound();
        }
        LocalDateTime now = LocalDateTime.now();
        long doctorId = currentUser.get().getUserId();
        if (!queries.teachesOfferingNow(offeringId, doctorId, now)) {
            throw EnrollmentErrors.notYourCourse();
        }
        if (!queries.groupTypesOfOffering(offeringId).contains(groupType)) {
            throw AttendanceErrors.noSuchGroupType();
        }

        boolean enabled = input.enabled();
        Integer limit = input.absenceLimit();
        if (limit != null && limit < 1) {
            throw AcademicErrors.validation("absenceLimit", "absenceLimit must be 1 or more");
        }
        if (enabled && limit == null) {
            throw AttendanceErrors.limitRequired();
        }
        if (!enabled && limit == null) {
            // turning off without a limit keeps the old limit, so turning on again is easy
            limit = queries.rule(offeringId, groupType).map(RuleRow::absenceLimit).orElse(null);
        }

        RuleRow rule = queries.saveRule(offeringId, groupType, enabled, limit, doctorId, now);
        if (enabled) {
            recheck(offeringId, groupType);
        }
        return toResponse(rule);
    }

    // ================================================================ the check

    /** Called by finish and correction: re-checks the course and group type of that session. */
    @Transactional
    public void recheckForSession(long sessionId) {
        queries.sessionCourseAndType(sessionId)
                .ifPresent(row -> recheck((Long) row[0], (String) row[1]));
    }

    /** Creates missing warnings (and their emails) for students at or over the limit. */
    @Transactional
    public void recheck(long offeringId, String groupType) {
        Optional<RuleRow> found = queries.rule(offeringId, groupType);
        if (found.isEmpty() || !found.get().enabled() || found.get().absenceLimit() == null) {
            return;
        }
        RuleRow rule = found.get();
        LocalDateTime now = LocalDateTime.now();

        for (Absences a : queries.absences(offeringId, groupType, null).values()) {
            if (a.absenceCount() >= rule.absenceLimit()) {
                queries.insertWarningOnce(a.enrollmentId(), rule.ruleId(), a.absenceCount(), a.late(), now)
                        .ifPresent(warningId -> emailWarning(warningId, a, rule));
            }
        }
    }

    // ================================================================ lists

    /** GET /offerings/{id}/attendance-warnings[?groupType=], most absences first. */
    @Transactional(readOnly = true)
    public List<WarningResponse> warningsOfOffering(long offeringId, String groupTypeText) {
        String groupType = null;
        if (groupTypeText != null) {
            groupType = groupTypeText.trim().toUpperCase(Locale.ROOT);
            if (!GROUP_TYPES.contains(groupType)) {
                throw AcademicErrors.validation("groupType", "groupType must be LECTURE or PRACTICAL");
            }
        }
        requireReadable(offeringId);
        return build(queries.warningsOfOffering(offeringId, groupType)).stream()
                .sorted(Comparator.comparingInt(WarningResponse::absenceCount).reversed()
                        .thenComparing(w -> w.student().universityId()))
                .toList();
    }

    /** GET /me/attendance-warnings, newest first. */
    @Transactional(readOnly = true)
    public List<WarningResponse> mine() {
        return build(queries.warningsOfStudent(currentUser.get().getUserId()));
    }

    /** Adds the CURRENT counts, weeks and group to each warning row (keeps the input order). */
    List<WarningResponse> build(List<WarningRow> rows) {
        // group the rows by (course, type): one absences query and one groups query each
        Map<String, List<WarningRow>> byCourseType = new LinkedHashMap<>();
        for (WarningRow w : rows) {
            byCourseType.computeIfAbsent(w.offeringId() + "|" + w.groupType(), k -> new ArrayList<>()).add(w);
        }
        Map<Long, WarningResponse> built = new LinkedHashMap<>();
        for (List<WarningRow> part : byCourseType.values()) {
            WarningRow first = part.get(0);
            List<Long> ids = part.stream().map(WarningRow::enrollmentId).toList();
            Map<Long, Absences> absences = queries.absences(first.offeringId(), first.groupType(), ids);
            Map<Long, GroupInfo> groups = queries.latestGroups(ids, first.groupType());
            for (WarningRow w : part) {
                Absences a = absences.getOrDefault(w.enrollmentId(), new Absences(w.enrollmentId(), 0, 0, List.of()));
                GroupInfo g = groups.get(w.enrollmentId());
                built.put(w.warningId(), new WarningResponse(
                        w.warningId(), w.enrollmentId(),
                        new StudentRef(w.userId(), w.universityId(), w.fullName(), w.fullNameAr()),
                        w.programName(), w.courseCode(), w.groupType(),
                        g == null ? null : g.label(), g == null ? null : schedule(g),
                        a.absenceCount(), a.late(), a.absentWeeks(), w.absenceLimit(),
                        DateTimeUtil.toInstant(w.issuedAt()), DateTimeUtil.toInstant(w.emailedAt())));
            }
        }
        return rows.stream().map(w -> built.get(w.warningId())).toList();
    }

    // ================================================================ helpers

    private void requireReadable(long offeringId) {
        if (!stats.offeringExists(offeringId)) {
            throw AcademicErrors.offeringNotFound();
        }
        if (!currentUser.isAdmin() && !queries.everTaughtOffering(offeringId, currentUser.get().getUserId())) {
            throw EnrollmentErrors.notYourCourse();
        }
    }

    /** "Wednesday 12:00–14:00" (contract example); null when the group has no day. */
    static String schedule(GroupInfo g) {
        if (g.dayOfWeek() == null) {
            return null;
        }
        String day = g.dayOfWeek().substring(0, 1).toUpperCase(Locale.ROOT)
                + g.dayOfWeek().substring(1).toLowerCase(Locale.ROOT);
        if (g.startTime() == null || g.endTime() == null) {
            return day;
        }
        return day + " " + g.startTime().format(TIME) + "–" + g.endTime().format(TIME);
    }

    private static WarningRuleResponse toResponse(RuleRow r) {
        return new WarningRuleResponse(r.ruleId(), r.offeringId(), r.groupType(), r.enabled(), r.absenceLimit(),
                DateTimeUtil.toInstant(r.updatedAt()));
    }

    /** The contract's English warning email, sent in the background after commit; emailed_at is set when sent. */
    private void emailWarning(long warningId, Absences a, RuleRow rule) {
        Optional<String[]> contact = queries.studentContact(a.enrollmentId());
        Optional<CourseInfo> course = queries.course(rule.offeringId());
        if (contact.isEmpty() || course.isEmpty()) {
            return;
        }
        String to = contact.get()[0];
        String name = contact.get()[1];
        String doctor = queries.userName(rule.updatedBy()).orElse("your doctor");
        String kind = "LECTURE".equals(rule.groupType()) ? "lecture" : "lab";
        String weeks = a.absentWeeks().stream().map(String::valueOf).collect(Collectors.joining(", "));
        CourseInfo c = course.get();

        String text = "Dear " + name + ",\n\n"
                + "You have been absent " + a.absenceCount() + " times from the " + kind + " of "
                + c.courseCode() + " " + c.title() + (weeks.isEmpty() ? "" : " (weeks " + weeks + ")") + ". "
                + "The limit set by Dr. " + doctor + " is " + rule.absenceLimit() + " absences.\n"
                + "Please contact your doctor if you think this is wrong.\n\nEduSync Team";

        mailer.sendAfterCommit("attendance warning to " + to, () -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(to);
            message.setSubject("Attendance warning – " + c.courseCode() + " " + c.title());
            message.setText(text);
            mailSender.send(message);
            queries.markEmailed(warningId, LocalDateTime.now());
        });
    }
}
