-- 89. Delivery scorecard: the readings (#1896)
--
-- The vocabulary is the one of CONTEXT.md: a reading, for one set of one project.
--
-- One row per set, project, reading and day. The daily snapshots are appended, not overwritten:
-- a recompute on the same day overwrites that day's row, and the rows past the retention are
-- purged by the daily job.
--
-- ESTATE_ID is NULL for the no-estate set. Its foreign key to the estates comes with them (#1900).
-- The uniqueness treats a NULL estate as a value.
--
-- Times are stored as VARCHAR(24), in the Time.forStorage format, as in every other table.
CREATE TABLE SCORECARD_READINGS
(
    ID             SERIAL PRIMARY KEY NOT NULL,
    ESTATE_ID      INTEGER            NULL,
    PROJECT_ID     INTEGER            NOT NULL,
    READING        VARCHAR(100)       NOT NULL, -- delivery.leadTime, delivery.frequency, ...
    DAY            DATE               NOT NULL,
    COMPUTED_AT    VARCHAR(24)        NOT NULL,
    WINDOW_START   VARCHAR(24)        NOT NULL,
    WINDOW_END     VARCHAR(24)        NOT NULL,
    VALUE          DOUBLE PRECISION   NULL,
    BASIS          VARCHAR(20)        NOT NULL, -- MEASURED | ESTIMATED | UNKNOWN
    UNKNOWN_REASON VARCHAR(40)        NULL,
    DETAILS        JSONB              NOT NULL,
    CONSTRAINT SCORECARD_READINGS_FK_PROJECT FOREIGN KEY (PROJECT_ID) REFERENCES PROJECTS (ID) ON DELETE CASCADE,
    CONSTRAINT SCORECARD_READINGS_UQ_KEY UNIQUE NULLS NOT DISTINCT (ESTATE_ID, PROJECT_ID, READING, DAY)
);

CREATE INDEX SCORECARD_READINGS_IX_PROJECT ON SCORECARD_READINGS (PROJECT_ID, READING, DAY);
CREATE INDEX SCORECARD_READINGS_IX_ESTATE ON SCORECARD_READINGS (ESTATE_ID);
CREATE INDEX SCORECARD_READINGS_IX_DAY ON SCORECARD_READINGS (DAY);
