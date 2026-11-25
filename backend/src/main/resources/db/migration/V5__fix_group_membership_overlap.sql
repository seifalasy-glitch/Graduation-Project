
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE group_membership
DROP CONSTRAINT uq_group_membership_enrollment_group;

ALTER TABLE group_membership
    ADD CONSTRAINT ex_group_membership_period_overlap
    EXCLUDE USING gist (
    enrollment_id WITH =,
    group_id WITH =,
    tsrange(starts_at, ends_at, '[)') WITH &&
);
