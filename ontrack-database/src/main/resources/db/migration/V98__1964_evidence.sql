-- 98. Audit trail: the evidence attached to the validation runs (#1964)
--
-- An evidence is a file attached to a validation run, referenced by an evidence.attached entry of
-- the trail of its build. Its content lives in the evidence storage (S3-compatible), under
-- blobs/<SHA256>, never in the database: this table holds its metadata only. Evidences are
-- immutable - a new upload is a new evidence - and several of them can share one blob.
--
-- SHA256 is the digest the server computed, authoritative; EXTERNAL_DIGEST the SHA-256 the client
-- claimed, if any, which matched it. COLLECTED_AT and DELETED_AT are server times in the format of
-- BUILD_TRAIL_ENTRY.TIME (2026-10-02T08:15:30.120Z), COLLECTED_BY the canonical JSON of the actor.
-- SOURCE_* is where the client says the evidence comes from. DELETED_AT is set by a deletion, the
-- row being kept.
--
-- Evidences go with their validation runs, and so with their builds.
CREATE TABLE EVIDENCE
(
    ID                SERIAL PRIMARY KEY NOT NULL,
    VALIDATION_RUN_ID INTEGER            NOT NULL,
    FILE_NAME         VARCHAR(255)       NOT NULL,
    MEDIA_TYPE        VARCHAR(255)       NOT NULL,
    SIZE              BIGINT             NOT NULL,
    SHA256            VARCHAR(64)        NOT NULL,
    COLLECTED_AT      VARCHAR(24)        NOT NULL,
    COLLECTED_BY      TEXT               NOT NULL,
    SOURCE_TOOL       VARCHAR(255)       NULL,
    SOURCE_VERSION    VARCHAR(255)       NULL,
    SOURCE_URL        VARCHAR(2000)      NULL,
    EXTERNAL_DIGEST   VARCHAR(64)        NULL,
    DELETED_AT        VARCHAR(24)        NULL,
    CONSTRAINT EVIDENCE_FK_VALIDATION_RUN FOREIGN KEY (VALIDATION_RUN_ID) REFERENCES VALIDATION_RUNS (ID) ON DELETE CASCADE
);

CREATE INDEX EVIDENCE_IDX_VALIDATION_RUN ON EVIDENCE (VALIDATION_RUN_ID);
-- The blobs a row still references, for their collection
CREATE INDEX EVIDENCE_IDX_SHA256 ON EVIDENCE (SHA256);
