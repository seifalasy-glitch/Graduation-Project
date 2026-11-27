-- Attendance warning rules follow docs/openapi.yaml (AttendanceWarningRule):
-- off by default, no default limit (the doctor sets it when turning warnings on),
-- and we remember which doctor set it
-- (the warning email says "The limit set by Dr. {doctorFullName}").

ALTER TABLE attendance_warning_rule ALTER COLUMN absence_limit DROP NOT NULL;
ALTER TABLE attendance_warning_rule ALTER COLUMN enabled SET DEFAULT false;

ALTER TABLE attendance_warning_rule ADD CONSTRAINT chk_warning_rule_group_type
    CHECK (group_type IN ('LECTURE', 'PRACTICAL'));
ALTER TABLE attendance_warning_rule ADD CONSTRAINT chk_warning_rule_limit
    CHECK (absence_limit IS NULL OR absence_limit >= 1);
ALTER TABLE attendance_warning_rule ADD CONSTRAINT chk_warning_rule_enabled_needs_limit
    CHECK (NOT enabled OR absence_limit IS NOT NULL);
-- (one rule per course + group type already exists: uq_warning_rule in V1)

ALTER TABLE attendance_warning_rule ADD COLUMN updated_by bigint REFERENCES users (user_id);
