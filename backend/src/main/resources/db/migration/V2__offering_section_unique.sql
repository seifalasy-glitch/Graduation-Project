-- V2: one offering per (course, term, section).
-- The old rule let two offerings have an empty section, because PostgreSQL
-- treats every NULL as different. NULLS NOT DISTINCT counts an empty section once.
-- Needs PostgreSQL 15 or newer.
--
-- Safe to run on a database where this was already applied by hand.

ALTER TABLE course_offering
    DROP CONSTRAINT IF EXISTS uq_course_offering_section;

ALTER TABLE course_offering
    ADD CONSTRAINT uq_course_offering_section
    UNIQUE NULLS NOT DISTINCT (course_id, term_id, section);
