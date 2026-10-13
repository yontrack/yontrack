-- Status of the jobs shared by all the replicas (#2079): the progress, last run and counters of a
-- job, written by the replica running it, and the request to stop its run, polled by that replica.
-- Whether a job runs, where and since when, comes from the executing fires of Quartz.
--
-- Times are stored as VARCHAR(24), in the Time.store format, as in every other table.

ALTER TABLE JOBS
    ADD COLUMN RUN_COUNT            BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN LAST_RUN_DATE        VARCHAR(24) NULL,
    ADD COLUMN LAST_RUN_DURATION_MS BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN LAST_ERROR           TEXT        NULL,
    ADD COLUMN ERROR_COUNT          BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN TIMEOUT_COUNT        BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN PROGRESS_PERCENTAGE  INTEGER     NULL,
    ADD COLUMN PROGRESS_MESSAGE     TEXT        NULL,
    ADD COLUMN STOP_REQUESTED       BOOLEAN     NOT NULL DEFAULT FALSE;
