-- Attendance tables follow docs/openapi.yaml (decided 27 Nov 2026).
-- No attendance code existed before this, so the UPDATEs below normally change 0 rows.

-- 1) Session method names: LOCAL_QR -> LOCAL_WIFI_QR (contract AttendanceMethod)
ALTER TABLE attendance_session DROP CONSTRAINT attendance_session_method_check;
UPDATE attendance_session SET method = 'LOCAL_WIFI_QR' WHERE method = 'LOCAL_QR';
ALTER TABLE attendance_session ADD CONSTRAINT attendance_session_method_check
    CHECK (method IN ('LOCAL_WIFI_QR', 'INTERNET_QR', 'MANUAL'));

-- 2) Session states (contract SessionState). MANUAL_ONLY is also shown when a
--    QR_OPEN session's QR window has passed (worked out by the backend on read).
ALTER TABLE attendance_session ADD CONSTRAINT chk_attendance_session_state
    CHECK (state IN ('QR_OPEN', 'MANUAL_ONLY', 'FINISHED'));

-- 3) At most one unfinished session per group ("SESSION_ALREADY_OPEN"),
--    also when two requests arrive at the same moment.
CREATE UNIQUE INDEX uq_attendance_session_open_per_group
    ON attendance_session (group_id) WHERE state <> 'FINISHED';

-- 4) Record methods = how the mark was made (contract MarkMethod): QR, MANUAL, AUTO_ABSENT
ALTER TABLE attendance_record DROP CONSTRAINT attendance_record_method_check;
UPDATE attendance_record SET method = 'QR' WHERE method IN ('LOCAL_QR', 'INTERNET_QR');
ALTER TABLE attendance_record ADD CONSTRAINT attendance_record_method_check
    CHECK (method IN ('QR', 'MANUAL', 'AUTO_ABSENT'));

-- 5) One mark per student per session: a QR scan and a manual mark write the
--    same row, so nobody is counted twice.
ALTER TABLE attendance_record ADD CONSTRAINT uq_attendance_record_session_enrollment
    UNIQUE (session_id, enrollment_id);

-- 6) A correction may be for a student who was never marked (old value empty),
--    and always has a reason (contract CorrectionRequest.reason is required).
ALTER TABLE attendance_change ALTER COLUMN old_status DROP NOT NULL;
UPDATE attendance_change SET reason_code = 'TECHNICAL_ISSUE' WHERE reason_code IS NULL;
ALTER TABLE attendance_change ALTER COLUMN reason_code SET NOT NULL;

-- 7) Check-in looks the scanned QR up by its hash
CREATE UNIQUE INDEX uq_qr_challenge_token_hash ON qr_challenge (token_hash);
