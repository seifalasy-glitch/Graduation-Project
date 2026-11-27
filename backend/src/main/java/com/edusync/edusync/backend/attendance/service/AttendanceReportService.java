package com.edusync.edusync.backend.attendance.service;

import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.attendance.AttendanceErrors;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.MyAttendance;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.MySession;
import com.edusync.edusync.backend.attendance.dto.AttendanceDtos.SummaryRow;
import com.edusync.edusync.backend.attendance.repository.AttendanceStatsQueries;
import com.edusync.edusync.backend.attendance.repository.AttendanceStatsQueries.Totals;
import com.edusync.edusync.backend.common.DateTimeUtil;
import com.edusync.edusync.backend.enrollment.dto.StudentRef;
import com.edusync.edusync.backend.enrollment.exception.EnrollmentErrors;
import com.edusync.edusync.backend.registration.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Attendance numbers: the course report (doctor / admin) and "my attendance" (student).
 * Totals and percentages count FINISHED sessions only; percentage = (present + late) / sessions.
 */
@Service
public class AttendanceReportService {

    private final AttendanceStatsQueries stats;
    private final CurrentUserService currentUser;

    public AttendanceReportService(AttendanceStatsQueries stats, CurrentUserService currentUser) {
        this.stats = stats;
        this.currentUser = currentUser;
    }

    /**
     * GET /offerings/{offeringId}/attendance-summary[?groupId=]. One row per student of the
     * counted groups (withdrawn students left out). Doctors see only groups they teach or taught;
     * a doctor with no group in the course gets OFFERING_NOT_FOUND, a group of the course they
     * do not teach gets NOT_YOUR_GROUP.
     */
    @Transactional(readOnly = true)
    public List<SummaryRow> summary(long offeringId, Long groupId) {
        if (!stats.offeringExists(offeringId)) {
            throw AcademicErrors.offeringNotFound();
        }
        boolean admin = currentUser.isAdmin();
        List<Long> visible = admin
                ? stats.groupsOfOffering(offeringId)
                : stats.doctorGroupsOfOffering(offeringId, currentUser.get().getUserId());
        if (!admin && visible.isEmpty()) {
            throw AcademicErrors.offeringNotFound();
        }

        List<Long> counted = visible;
        if (groupId != null) {
            if (!stats.groupsOfOffering(offeringId).contains(groupId)) {
                throw EnrollmentErrors.groupNotFound();
            }
            if (!visible.contains(groupId)) {
                throw AttendanceErrors.notYourGroup();
            }
            counted = List.of(groupId);
        }

        Map<Long, Totals> totals = stats.totalsByEnrollmentInGroups(counted);
        return stats.studentsOfGroups(counted).stream()
                .map(s -> {
                    Totals t = totals.getOrDefault(s.enrollmentId(), Totals.NONE);
                    return new SummaryRow(
                            s.enrollmentId(),
                            new StudentRef(s.userId(), s.universityId(), s.fullName(), s.fullNameAr()),
                            t.sessions(), t.present(), t.late(), t.absent(), t.unmarked(), t.percent());
                })
                .toList();
    }

    /** GET /me/attendance?offeringId=. A course the student is not enrolled in -> OFFERING_NOT_FOUND. */
    @Transactional(readOnly = true)
    public MyAttendance mine(long offeringId) {
        long enrollmentId = stats.enrollmentOf(currentUser.get().getUserId(), offeringId)
                .orElseThrow(AcademicErrors::offeringNotFound);

        Totals t = stats.totalsByEnrollment(List.of(enrollmentId)).getOrDefault(enrollmentId, Totals.NONE);
        List<MySession> sessions = stats.sessionsOfEnrollment(enrollmentId).stream()
                .map(s -> new MySession(s.sessionId(), DateTimeUtil.toInstant(s.startsAt()),
                        s.groupType(), s.groupLabel(), s.status()))
                .toList();

        return new MyAttendance(offeringId, t.present(), t.late(), t.absent(), t.percent(), sessions);
    }
}
