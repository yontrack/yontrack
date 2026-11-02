-- #2129 A read-only token reads what its owner can read, and changes nothing
ALTER TABLE TOKENS ADD COLUMN READ_ONLY BOOLEAN NOT NULL DEFAULT FALSE;
