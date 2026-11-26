package com.edusync.edusync.backend.registration.repository;

import com.edusync.edusync.backend.registration.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

}
