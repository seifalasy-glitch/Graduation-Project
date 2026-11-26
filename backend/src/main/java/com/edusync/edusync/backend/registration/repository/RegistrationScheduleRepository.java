package com.edusync.edusync.backend.registration.repository;

import com.edusync.edusync.backend.enrollment.entity.StudentGroup;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationScheduleRepository extends JpaRepository<StudentGroup, Long> {

    List<StudentGroup> findByOfferingIdIn(Collection<Long> offeringIds);
}
