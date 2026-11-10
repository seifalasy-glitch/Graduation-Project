-- ============================================================
-- Test accounts for LOCAL DEVELOPMENT ONLY.
-- Never run this on the real university server.
--
-- Run it in pgAdmin (Query Tool on the edusync database) after
-- the schema is restored. Running it twice is safe: existing
-- accounts are skipped.
--
-- All three accounts use the password:  Edu@2026x
-- ============================================================

INSERT INTO users (university_id, email, full_name, full_name_ar,
                   password_hash, must_change_password, status)
SELECT 'DEV-ADMIN', 'admin.dev@sci.asu.edu.eg', 'Dev Admin', 'أدمن تجريبي',
       crypt('Edu@2026x', gen_salt('bf', 10)), false, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin.dev@sci.asu.edu.eg');

INSERT INTO users (university_id, email, full_name, full_name_ar,
                   password_hash, must_change_password, status)
SELECT 'DEV-DOCTOR', 'doctor.dev@sci.asu.edu.eg', 'Dev Doctor', 'دكتور تجريبي',
       crypt('Edu@2026x', gen_salt('bf', 10)), false, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'doctor.dev@sci.asu.edu.eg');

INSERT INTO users (university_id, email, full_name, full_name_ar,
                   password_hash, must_change_password, status)
SELECT 'DEV-STUDENT', 'student.dev@sci.asu.edu.eg', 'Dev Student', 'طالب تجريبي',
       crypt('Edu@2026x', gen_salt('bf', 10)), false, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'student.dev@sci.asu.edu.eg');

INSERT INTO user_role (user_id, role)
SELECT u.user_id, v.role
FROM (VALUES ('admin.dev@sci.asu.edu.eg',   'ADMIN'),
             ('doctor.dev@sci.asu.edu.eg',  'DOCTOR'),
             ('student.dev@sci.asu.edu.eg', 'STUDENT')) AS v(email, role)
JOIN users u ON u.email = v.email
WHERE NOT EXISTS (
    SELECT 1 FROM user_role r WHERE r.user_id = u.user_id AND r.role = v.role
);

-- Check: should list the three accounts with their roles
SELECT u.user_id, u.email, r.role, u.must_change_password, u.status
FROM users u
JOIN user_role r ON r.user_id = u.user_id
WHERE u.email LIKE '%.dev@sci.asu.edu.eg'
ORDER BY u.user_id;
