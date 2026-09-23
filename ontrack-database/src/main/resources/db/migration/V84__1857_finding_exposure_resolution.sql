-- 84. Security findings: resolution and acceptance of the exposure (#1857)
--
-- An exposure row outlives its resolution: absent from the latest scan of its stamp on its
-- branch, it is resolved there, with a reason, and it is what tells a return after resolution
-- from a first exposure. The acceptance of the latest scan is kept beside it, so that its
-- expiry is evaluated when it is read.

ALTER TABLE FINDING_EXPOSURES
    ADD COLUMN ACCEPTED              BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN ACCEPTANCE_EXPIRES_AT DATE        NULL,
    ADD COLUMN RESOLVED_AT           VARCHAR(24) NULL,
    ADD COLUMN RESOLUTION_REASON     VARCHAR(20) NULL; -- ABSENT
