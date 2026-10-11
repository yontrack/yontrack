-- Scans the whole table under an exclusive lock, and fails the previous release's inserts
ALTER TABLE BUILDS
    ALTER COLUMN ACTOR SET NOT NULL;
-- Relaxing a constraint: allowed
ALTER TABLE BUILDS
    ALTER COLUMN ACTOR DROP NOT NULL;
