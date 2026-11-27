package com.edusync.edusync.backend.academic.service;

import com.edusync.edusync.backend.academic.entity.AcademicTerm;
import com.edusync.edusync.backend.academic.repository.AcademicTermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * "Current term" rule (approved 27 Nov 2026): the term where today is between
 * start_date and end_date, both days included. No such term (a break between
 * terms) = no current term, and "this term" screens show an empty list.
 * If two terms overlap by mistake, the one that started last wins.
 */
@Service
public class CurrentTermService {

    private final AcademicTermRepository termRepository;

    public CurrentTermService(AcademicTermRepository termRepository) {
        this.termRepository = termRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AcademicTerm> current() {
        return termRepository.findContaining(LocalDate.now()).stream().findFirst();
    }

    public static boolean isCurrent(AcademicTerm term) {
        LocalDate today = LocalDate.now();
        return term.getStartDate() != null && term.getEndDate() != null
                && !today.isBefore(term.getStartDate()) && !today.isAfter(term.getEndDate());
    }

    /** API label, e.g. FALL + 2026/2027 -> "Fall 2026/2027". */
    public static String label(String termType, String academicYear) {
        String type = termType == null || termType.isEmpty() ? ""
                : termType.substring(0, 1).toUpperCase(Locale.ROOT)
                  + termType.substring(1).toLowerCase(Locale.ROOT);
        return (type + " " + academicYear).trim();
    }
}
