package com.edusync.edusync.backend.academic.service;

import com.edusync.edusync.backend.academic.dto.AcademicTermResponse;
import com.edusync.edusync.backend.academic.dto.CreateAcademicTermRequest;
import com.edusync.edusync.backend.academic.dto.UpdateAcademicTermRequest;
import com.edusync.edusync.backend.academic.entity.AcademicTerm;
import com.edusync.edusync.backend.academic.exception.AcademicErrors;
import com.edusync.edusync.backend.academic.repository.AcademicTermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AcademicTermService {

    // API contract (TermInput.termType)
    private static final Set<String> TERM_TYPES = Set.of("FALL", "SPRING", "SUMMER");

    private final AcademicTermRepository termRepository;

    public AcademicTermService(AcademicTermRepository termRepository) {
        this.termRepository = termRepository;
    }

    /** API contract: newest first. */
    @Transactional(readOnly = true)
    public List<AcademicTermResponse> getAllTerms() {
        return termRepository.findAllNewestFirst()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AcademicTermResponse createTerm(CreateAcademicTermRequest request) {

        String academicYear = request.academicYear().trim();
        String termType = request.termType().trim().toUpperCase(Locale.ROOT);

        if (academicYear.isBlank()) {
            throw AcademicErrors.validation("academicYear", "Academic year cannot be blank");
        }

        validateTermType(termType);
        validateDates(request.startDate(), request.endDate());

        if (termRepository.existsByAcademicYearIgnoreCaseAndTermTypeIgnoreCase(
                academicYear, termType)) {
            throw AcademicErrors.termExists();
        }

        AcademicTerm term = new AcademicTerm();

        term.setAcademicYear(academicYear);
        term.setTermType(termType);
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());

        return toResponse(termRepository.save(term));
    }

    @Transactional
    public AcademicTermResponse updateTerm(
            Long termId,
            UpdateAcademicTermRequest request
    ) {

        AcademicTerm term = termRepository.findById(termId)
                .orElseThrow(() -> AcademicErrors.termNotFound());

        String academicYear = term.getAcademicYear();
        String termType = term.getTermType();

        if (request.academicYear() != null) {

            academicYear = request.academicYear().trim();

            if (academicYear.isBlank()) {
                throw AcademicErrors.validation("academicYear", "Academic year cannot be blank");
            }
        }

        if (request.termType() != null) {

            termType = request.termType().trim().toUpperCase(Locale.ROOT);

            validateTermType(termType);
        }

        LocalDate startDate = request.startDate() != null
                ? request.startDate()
                : term.getStartDate();

        LocalDate endDate = request.endDate() != null
                ? request.endDate()
                : term.getEndDate();

        validateDates(startDate, endDate);

        if (termRepository.existsByAcademicYearIgnoreCaseAndTermTypeIgnoreCaseAndTermIdNot(
                academicYear, termType, termId)) {
            throw AcademicErrors.termExists();
        }

        term.setAcademicYear(academicYear);
        term.setTermType(termType);
        term.setStartDate(startDate);
        term.setEndDate(endDate);

        return toResponse(termRepository.save(term));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {

        if (startDate != null
                && endDate != null
                && endDate.isBefore(startDate)) {

            throw AcademicErrors.validation("endDate", "End date cannot be before start date");
        }
    }

    private void validateTermType(String termType) {

        if (!TERM_TYPES.contains(termType)) {
            throw AcademicErrors.validation(
                    "termType",
                    "Term type must be FALL, SPRING or SUMMER"
            );
        }
    }

    private AcademicTermResponse toResponse(AcademicTerm term) {

        return new AcademicTermResponse(
                term.getTermId(),
                term.getAcademicYear(),
                term.getTermType(),
                term.getStartDate(),
                term.getEndDate()
        );
    }
}
