-- Withdrawn students used to stay in their groups (withdrawing did not end the
-- memberships), so group member counts and attendance lists still included them.
-- The code now ends them on withdraw (EnrollmentService.update). This fixes rows
-- that were withdrawn before that change. Safe to run on any database: it only
-- touches memberships of WITHDRAWN enrollments that are still open.
--
-- LOCALTIMESTAMP = "now" without a timezone, like the app's LocalDateTime.now().
-- The `starts_at < LOCALTIMESTAMP` guard keeps chk_membership_period (ends_at > starts_at) true.

-- 1) Already started and still open -> ends now
UPDATE group_membership m
SET ends_at = LOCALTIMESTAMP
FROM enrollment e
WHERE e.enrollment_id = m.enrollment_id
  AND e.status = 'WITHDRAWN'
  AND m.starts_at < LOCALTIMESTAMP
  AND (m.ends_at IS NULL OR m.ends_at > LOCALTIMESTAMP);

-- 2) Not started yet -> removed (it never happened)
DELETE FROM group_membership m
USING enrollment e
WHERE e.enrollment_id = m.enrollment_id
  AND e.status = 'WITHDRAWN'
  AND m.starts_at >= LOCALTIMESTAMP;
