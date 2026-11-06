package com.edusync.edusync.backend.users.repository;

import com.edusync.edusync.backend.users.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUniversityId(String universityId);

    @Query("""
            SELECT u
            FROM User u
            WHERE (:status = '' OR u.status = :status)
              AND (
                  :q = ''
                  OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(u.universityId) LIKE LOWER(CONCAT('%', :q, '%'))
              )
              AND (
                  :role = ''
                  OR EXISTS (
                      SELECT ur
                      FROM UserRole ur
                      WHERE ur.userId = u.userId
                        AND ur.role = :role
                  )
              )
            """)
    Page<User> searchUsers(
            @Param("role") String role,
            @Param("status") String status,
            @Param("q") String q,
            Pageable pageable
    );
}
