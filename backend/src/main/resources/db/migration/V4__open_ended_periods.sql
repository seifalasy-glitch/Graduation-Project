ALTER TABLE group_membership
    ALTER COLUMN ends_at DROP NOT NULL;

ALTER TABLE teaching_assignment
    ALTER COLUMN ends_at DROP NOT NULL;