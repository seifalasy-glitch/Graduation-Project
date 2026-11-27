-- V5: a student can leave a group and join the SAME group again later.
-- The old rule UNIQUE (enrollment_id, group_id) allowed only one membership row
-- per student and group, ever. The new rule only forbids two membership periods
-- of the same student in the same group that overlap in time
-- (ends_at NULL = still a member, so it needs V4).
--
-- Safe to run on a database where this was already applied by hand.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE group_membership
    DROP CONSTRAINT IF EXISTS uq_group_membership_enrollment_group;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ex_group_membership_period_overlap'
          AND conrelid = 'group_membership'::regclass
    ) THEN
        ALTER TABLE group_membership
            ADD CONSTRAINT ex_group_membership_period_overlap
            EXCLUDE USING gist (
                enrollment_id WITH =,
                group_id WITH =,
                tsrange(starts_at, ends_at, '[)') WITH &&
            );
    END IF;
END $$;
