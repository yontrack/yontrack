-- 96. Audit trail: the entries of the trail of every build (#1954)
--
-- The vocabulary is the one of CONTEXT.md: the trail of a build is the hash-chained list of its
-- entries. The hash format is TrailHashFormatV1 (schema version 1), a compatibility contract.
--
-- PAYLOAD and ACTOR hold the RFC 8785 canonical JSON text which was hashed, and TIME the hashed
-- time itself, ISO-8601 UTC with milliseconds (2026-10-02T08:15:30.120Z) - not the
-- Time.forStorage format of the other tables - so that a trail is verified from its rows alone.
--
-- A trail goes with its build (ON DELETE CASCADE in 6.0).
CREATE TABLE BUILD_TRAIL_ENTRY
(
    ID             SERIAL PRIMARY KEY NOT NULL,
    BUILD_ID       INTEGER            NOT NULL,
    SEQ            INTEGER            NOT NULL, -- 1..n per build, without gap
    SCHEMA_VERSION INTEGER            NOT NULL,
    TYPE           VARCHAR(100)       NOT NULL,
    PAYLOAD        TEXT               NOT NULL,
    ACTOR          TEXT               NOT NULL,
    TIME           VARCHAR(24)        NOT NULL,
    PREV_HASH      VARCHAR(64)        NULL,     -- NULL for seq 1 only
    HASH           VARCHAR(64)        NOT NULL,
    CONSTRAINT BUILD_TRAIL_ENTRY_FK_BUILD FOREIGN KEY (BUILD_ID) REFERENCES BUILDS (ID) ON DELETE CASCADE,
    CONSTRAINT BUILD_TRAIL_ENTRY_UQ_SEQ UNIQUE (BUILD_ID, SEQ)
);
