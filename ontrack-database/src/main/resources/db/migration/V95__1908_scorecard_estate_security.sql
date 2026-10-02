-- 95. Delivery scorecard: what an estate expects of the security scans of its projects (#1908)
--
-- The freshness is a number of days, NULL for the one of the settings. The remediation targets are
-- numbers of days a CRITICAL or a HIGH finding may stay open, NULL for no target.
ALTER TABLE SCORECARD_ESTATES
    ADD COLUMN SECURITY_FRESHNESS_DAYS       INTEGER NULL,
    ADD COLUMN SECURITY_CRITICAL_TARGET_DAYS INTEGER NULL,
    ADD COLUMN SECURITY_HIGH_TARGET_DAYS     INTEGER NULL;

-- The kinds of scan an estate expects every project to have run, each fresher than the freshness.
-- None: any fresh scan covers a project.
CREATE TABLE SCORECARD_ESTATE_SCAN_KINDS
(
    ID        SERIAL PRIMARY KEY NOT NULL,
    ESTATE_ID INTEGER            NOT NULL,
    KIND      VARCHAR(20)        NOT NULL, -- IMAGE | CODE | SECRETS | DAST | DEPENDENCIES | OTHER
    CONSTRAINT SCORECARD_ESTATE_SCAN_KINDS_FK_ESTATE FOREIGN KEY (ESTATE_ID) REFERENCES SCORECARD_ESTATES (ID) ON DELETE CASCADE,
    CONSTRAINT SCORECARD_ESTATE_SCAN_KINDS_UQ UNIQUE (ESTATE_ID, KIND)
);
