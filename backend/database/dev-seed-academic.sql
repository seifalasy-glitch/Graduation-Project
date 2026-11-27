-- ============================================================
-- Shared sample data for LOCAL DEVELOPMENT ONLY.
-- Never run this on the real university server.
--
-- Gives everyone (backend, frontend, testing) the same programs,
-- terms, courses, offerings, rooms, groups, students and enrollments,
-- so a bug like "COMP202 shows the wrong doctor" looks the same on
-- every laptop.
--
-- Run it AFTER dev-seed-users.sql (it needs doctor.dev):
--   Get-Content .\dev-seed-academic.sql -Raw | docker exec -i edusync-db psql -U edusync -d edusync -v ON_ERROR_STOP=1 --single-transaction
-- Running it twice is safe: rows that already exist are skipped.
--
-- New accounts below use the same password as dev-seed-users.sql: Edu@2026x
--
-- What you can test with it
--   * Admin screens: 2 programs, 2 terms, 6 courses, 6 offerings, 2 rooms.
--   * Doctor screens: doctor.dev teaches all Fall groups and the Spring groups.
--     doctor2.dev teaches nothing (use it to see NOT_YOUR_COURSE).
--   * Student screens: student.dev .. student5.dev are enrolled in COMP202 and
--     COMP205 (Fall 2026/2027) with a lecture and a lab group each.
--   * Registration for Spring 2026/2027:
--     students are level 2 in CS, credit limit 18, advisor doctor.dev.
--     COMP206 and COMP207 meet at the same time (Sunday 10:00) -> SCHEDULE_CLASH.
--     COMP207 has only 2 seats -> OFFERING_FULL once 2 students are accepted.
--   * student6.dev is DISABLED -> STUDENT_DISABLED when an admin enrolls it.
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'doctor.dev@sci.asu.edu.eg') THEN
        RAISE EXCEPTION 'Run dev-seed-users.sql first (doctor.dev@sci.asu.edu.eg is missing)';
    END IF;
END $$;

-- ------------------------------------------------------------ extra accounts
INSERT INTO users (university_id, email, full_name, full_name_ar,
                   password_hash, must_change_password, status)
SELECT v.university_id, v.email, v.full_name, v.full_name_ar,
       crypt('Edu@2026x', gen_salt('bf', 10)), false, v.status
FROM (VALUES
    ('DEV-DOCTOR-2',  'doctor2.dev@sci.asu.edu.eg',  'Dev Doctor Two',    'دكتور تجريبي ٢', 'ACTIVE'),
    ('DEV-STUDENT-2', 'student2.dev@sci.asu.edu.eg', 'Dev Student Two',   'طالب تجريبي ٢',  'ACTIVE'),
    ('DEV-STUDENT-3', 'student3.dev@sci.asu.edu.eg', 'Dev Student Three', 'طالب تجريبي ٣',  'ACTIVE'),
    ('DEV-STUDENT-4', 'student4.dev@sci.asu.edu.eg', 'Dev Student Four',  'طالب تجريبي ٤',  'ACTIVE'),
    ('DEV-STUDENT-5', 'student5.dev@sci.asu.edu.eg', 'Dev Student Five',  'طالب تجريبي ٥',  'ACTIVE'),
    ('DEV-STUDENT-6', 'student6.dev@sci.asu.edu.eg', 'Dev Student Six',   'طالب تجريبي ٦',  'DISABLED')
) AS v(university_id, email, full_name, full_name_ar, status)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = v.email)
  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.university_id = v.university_id);

INSERT INTO user_role (user_id, role)
SELECT u.user_id, v.role
FROM (VALUES
    ('doctor2.dev@sci.asu.edu.eg',  'DOCTOR'),
    ('student2.dev@sci.asu.edu.eg', 'STUDENT'),
    ('student3.dev@sci.asu.edu.eg', 'STUDENT'),
    ('student4.dev@sci.asu.edu.eg', 'STUDENT'),
    ('student5.dev@sci.asu.edu.eg', 'STUDENT'),
    ('student6.dev@sci.asu.edu.eg', 'STUDENT')
) AS v(email, role)
JOIN users u ON u.email = v.email
WHERE NOT EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.user_id AND r.role = v.role);

-- ------------------------------------------------------------ programs
INSERT INTO academic_program (code, name, department, active)
SELECT v.code, v.name, v.department, true
FROM (VALUES
    ('CS',   'Computer Science', 'Mathematics'),
    ('MATH', 'Mathematics',      'Mathematics')
) AS v(code, name, department)
WHERE NOT EXISTS (SELECT 1 FROM academic_program p WHERE p.code = v.code);

-- ------------------------------------------------------------ terms
INSERT INTO academic_term (academic_year, term_type, start_date, end_date)
SELECT v.academic_year, v.term_type, v.start_date::date, v.end_date::date
FROM (VALUES
    ('2026/2027', 'FALL',   '2026-09-19', '2027-01-21'),
    ('2026/2027', 'SPRING', '2027-02-13', '2027-06-10')
) AS v(academic_year, term_type, start_date, end_date)
WHERE NOT EXISTS (
    SELECT 1 FROM academic_term t
    WHERE t.academic_year = v.academic_year AND t.term_type = v.term_type
);

-- ------------------------------------------------------------ courses
INSERT INTO course (course_code, title, credit_hours)
SELECT v.course_code, v.title, v.credit_hours
FROM (VALUES
    ('COMP101', 'Introduction to Programming', 3),
    ('MATH101', 'Calculus I',                  3),
    ('COMP202', 'Data Structures',             3),
    ('COMP205', 'Database Systems',            3),
    ('COMP206', 'Operating Systems',           3),
    ('COMP207', 'Software Engineering',        3)
) AS v(course_code, title, credit_hours)
WHERE NOT EXISTS (SELECT 1 FROM course c WHERE c.course_code = v.course_code);

-- ------------------------------------------------------------ program plan (CS)
INSERT INTO program_plan_course (program_id, course_id, level, term_type)
SELECT p.program_id, c.course_id, v.level, v.term_type
FROM (VALUES
    ('COMP101', 1, 'FALL'),
    ('MATH101', 1, 'FALL'),
    ('COMP202', 2, 'FALL'),
    ('COMP205', 2, 'FALL'),
    ('COMP206', 2, 'SPRING'),
    ('COMP207', 2, 'SPRING')
) AS v(course_code, level, term_type)
JOIN academic_program p ON p.code = 'CS'
JOIN course c ON c.course_code = v.course_code
WHERE NOT EXISTS (
    SELECT 1 FROM program_plan_course x
    WHERE x.program_id = p.program_id AND x.course_id = c.course_id
      AND x.level = v.level AND x.term_type = v.term_type
);

-- ------------------------------------------------------------ rooms
INSERT INTO room (room_code, name, building, capacity, network_mode, ssid, router_mac, subnet)
SELECT v.room_code, v.name, v.building, v.capacity, v.network_mode, v.ssid, v.router_mac, v.subnet
FROM (VALUES
    ('HALL-A1',  'Hall A1',  'Building A', 150, 'LOCAL_ROUTER', 'EDU-HALL-A1', '3C:84:6A:1F:22:B0', NULL),
    ('LAB-B204', 'Lab B204', 'Building B',  40, 'CAMPUS_WIFI',  NULL,          NULL,                '10.12.3.0/24')
) AS v(room_code, name, building, capacity, network_mode, ssid, router_mac, subnet)
WHERE NOT EXISTS (SELECT 1 FROM room r WHERE r.room_code = v.room_code);

-- ------------------------------------------------------------ offerings
INSERT INTO course_offering (course_id, term_id, section, capacity, coursework_max)
SELECT c.course_id, t.term_id, '1', v.capacity, 40
FROM (VALUES
    ('COMP101', '2026/2027', 'FALL',   100),
    ('MATH101', '2026/2027', 'FALL',   100),
    ('COMP202', '2026/2027', 'FALL',    60),
    ('COMP205', '2026/2027', 'FALL',    60),
    ('COMP206', '2026/2027', 'SPRING',  60),
    ('COMP207', '2026/2027', 'SPRING',   2)
) AS v(course_code, academic_year, term_type, capacity)
JOIN course c ON c.course_code = v.course_code
JOIN academic_term t ON t.academic_year = v.academic_year AND t.term_type = v.term_type
WHERE NOT EXISTS (
    SELECT 1 FROM course_offering o
    WHERE o.course_id = c.course_id AND o.term_id = t.term_id AND o.section = '1'
);

-- ------------------------------------------------------------ groups
INSERT INTO student_group (offering_id, group_type, label, day_of_week, start_time, end_time)
SELECT o.offering_id, v.group_type, v.label, v.day_of_week, v.start_time::time, v.end_time::time
FROM (VALUES
    ('COMP202', 'FALL',   'LECTURE',   'A',  'SUNDAY',    '10:00', '12:00'),
    ('COMP202', 'FALL',   'PRACTICAL', 'L1', 'TUESDAY',   '12:00', '14:00'),
    ('COMP205', 'FALL',   'LECTURE',   'A',  'MONDAY',    '10:00', '12:00'),
    ('COMP205', 'FALL',   'PRACTICAL', 'L1', 'WEDNESDAY', '12:00', '14:00'),
    ('COMP206', 'SPRING', 'LECTURE',   'A',  'SUNDAY',    '10:00', '12:00'),
    ('COMP207', 'SPRING', 'LECTURE',   'A',  'SUNDAY',    '10:00', '12:00')
) AS v(course_code, term_type, group_type, label, day_of_week, start_time, end_time)
JOIN course c ON c.course_code = v.course_code
JOIN academic_term t ON t.academic_year = '2026/2027' AND t.term_type = v.term_type
JOIN course_offering o ON o.course_id = c.course_id AND o.term_id = t.term_id AND o.section = '1'
WHERE NOT EXISTS (
    SELECT 1 FROM student_group g
    WHERE g.offering_id = o.offering_id AND g.group_type = v.group_type AND g.label = v.label
);

-- ------------------------------------------------------------ doctor.dev teaches every group above
INSERT INTO teaching_assignment (group_id, doctor_user_id, starts_at, ends_at)
SELECT g.group_id, d.user_id, TIMESTAMP '2026-09-01 00:00', NULL
FROM student_group g
JOIN course_offering o ON o.offering_id = g.offering_id
JOIN course c ON c.course_id = o.course_id
JOIN academic_term t ON t.term_id = o.term_id AND t.academic_year = '2026/2027'
JOIN users d ON d.email = 'doctor.dev@sci.asu.edu.eg'
WHERE c.course_code IN ('COMP202', 'COMP205', 'COMP206', 'COMP207')
  AND NOT EXISTS (
      SELECT 1 FROM teaching_assignment a
      WHERE a.group_id = g.group_id AND a.doctor_user_id = d.user_id
  );

-- ------------------------------------------------------------ student profiles (CS, level 2, advisor doctor.dev)
INSERT INTO student_profile (user_id, program_id, advisor_id, level, max_credit_hours)
SELECT s.user_id, p.program_id, d.user_id, 2, 18
FROM users s
JOIN academic_program p ON p.code = 'CS'
JOIN users d ON d.email = 'doctor.dev@sci.asu.edu.eg'
WHERE s.email IN ('student.dev@sci.asu.edu.eg',  'student2.dev@sci.asu.edu.eg',
                  'student3.dev@sci.asu.edu.eg', 'student4.dev@sci.asu.edu.eg',
                  'student5.dev@sci.asu.edu.eg', 'student6.dev@sci.asu.edu.eg')
  AND NOT EXISTS (SELECT 1 FROM student_profile x WHERE x.user_id = s.user_id);

-- ------------------------------------------------------------ enrollments in COMP202 and COMP205 (Fall)
INSERT INTO enrollment (student_user_id, offering_id, program_id, status, seat_no)
SELECT s.user_id, o.offering_id, p.program_id, 'ACTIVE', v.seat_no
FROM (VALUES
    ('student.dev@sci.asu.edu.eg',  '2026-001'),
    ('student2.dev@sci.asu.edu.eg', '2026-002'),
    ('student3.dev@sci.asu.edu.eg', '2026-003'),
    ('student4.dev@sci.asu.edu.eg', '2026-004'),
    ('student5.dev@sci.asu.edu.eg', '2026-005')
) AS v(email, seat_no)
JOIN users s ON s.email = v.email
JOIN academic_program p ON p.code = 'CS'
JOIN course c ON c.course_code IN ('COMP202', 'COMP205')
JOIN academic_term t ON t.academic_year = '2026/2027' AND t.term_type = 'FALL'
JOIN course_offering o ON o.course_id = c.course_id AND o.term_id = t.term_id AND o.section = '1'
WHERE NOT EXISTS (
    SELECT 1 FROM enrollment e
    WHERE e.student_user_id = s.user_id AND e.offering_id = o.offering_id
);

-- ------------------------------------------------------------ each enrolled student joins lecture A and lab L1
INSERT INTO group_membership (enrollment_id, group_id, starts_at, ends_at)
SELECT e.enrollment_id, g.group_id, TIMESTAMP '2026-09-19 00:00', NULL
FROM enrollment e
JOIN users s ON s.user_id = e.student_user_id
JOIN student_group g ON g.offering_id = e.offering_id
JOIN course_offering o ON o.offering_id = e.offering_id
JOIN academic_term t ON t.term_id = o.term_id AND t.academic_year = '2026/2027' AND t.term_type = 'FALL'
WHERE s.email LIKE 'student%.dev@sci.asu.edu.eg'
  AND NOT EXISTS (
      SELECT 1 FROM group_membership m
      WHERE m.enrollment_id = e.enrollment_id AND m.group_id = g.group_id
  );

-- ------------------------------------------------------------ summary
SELECT 'programs'    AS what, count(*) FROM academic_program WHERE code IN ('CS', 'MATH')
UNION ALL SELECT 'terms',       count(*) FROM academic_term WHERE academic_year = '2026/2027'
UNION ALL SELECT 'courses',     count(*) FROM course WHERE course_code IN ('COMP101','MATH101','COMP202','COMP205','COMP206','COMP207')
UNION ALL SELECT 'rooms',       count(*) FROM room WHERE room_code IN ('HALL-A1', 'LAB-B204')
UNION ALL SELECT 'offerings',   count(*) FROM course_offering o JOIN academic_term t USING (term_id) WHERE t.academic_year = '2026/2027'
UNION ALL SELECT 'groups',      count(*) FROM student_group g JOIN course_offering o USING (offering_id) JOIN academic_term t USING (term_id) WHERE t.academic_year = '2026/2027'
UNION ALL SELECT 'enrollments', count(*) FROM enrollment e JOIN users u ON u.user_id = e.student_user_id WHERE u.email LIKE '%.dev@sci.asu.edu.eg';
