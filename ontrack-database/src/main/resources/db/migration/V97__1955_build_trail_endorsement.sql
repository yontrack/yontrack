-- 97. Audit trail: the endorsements of the entries by the instance key (#1955)
--
-- An endorsement is the Ed25519 signature of the 32 bytes of the hash of an entry by the instance
-- key (TrailEndorsementFormat). It is kept out of BUILD_TRAIL_ENTRY, and out of what is hashed, so
-- that batching or a key rollover can come later without touching the hash format.
--
-- An entry written while the instance key is not provisioned has no endorsement, and never gets
-- one afterwards. The key of the table leaves room for an entry endorsed by several keys.
--
-- KEY_ID is the first 16 hex characters of the SHA-256 of the public key, SIGNATURE the signature
-- in base64, and TIME the time of the endorsement in the format of BUILD_TRAIL_ENTRY.TIME
-- (2026-10-02T08:15:30.120Z). Endorsements go with their entries.
CREATE TABLE BUILD_TRAIL_ENDORSEMENT
(
    ENTRY_ID  INTEGER     NOT NULL,
    KEY_ID    VARCHAR(64) NOT NULL,
    SIGNATURE TEXT        NOT NULL,
    TIME      VARCHAR(24) NOT NULL,
    CONSTRAINT BUILD_TRAIL_ENDORSEMENT_PK PRIMARY KEY (ENTRY_ID, KEY_ID),
    CONSTRAINT BUILD_TRAIL_ENDORSEMENT_FK_ENTRY FOREIGN KEY (ENTRY_ID) REFERENCES BUILD_TRAIL_ENTRY (ID) ON DELETE CASCADE
);
