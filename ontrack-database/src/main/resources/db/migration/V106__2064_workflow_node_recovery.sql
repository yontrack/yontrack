-- 106. Recovery of the workflow nodes left unfinished by a stopped Yontrack process (#2064)
--
-- Additive only, so that the previous version keeps running against this schema while both run
-- side by side during a rolling upgrade.

-- Yontrack processes running workflow nodes, with their last heartbeat
CREATE TABLE WKF_ENGINE_PROCESSES
(
    ID        VARCHAR(64) NOT NULL PRIMARY KEY,
    STARTED   VARCHAR(24) NOT NULL,
    HEARTBEAT VARCHAR(24) NOT NULL
);

-- Who launched an instance, to resume its nodes under the same identity.
-- A null account with an actor is the system; both null, unknown.
ALTER TABLE WKF_INSTANCES
    ADD COLUMN ACCOUNT TEXT NULL;
ALTER TABLE WKF_INSTANCES
    ADD COLUMN ACTOR JSONB NULL;

-- Process which claimed a node, and last time a node was sent to the queue
ALTER TABLE WKF_INSTANCE_NODES
    ADD COLUMN OWNER VARCHAR(64) NULL;
ALTER TABLE WKF_INSTANCE_NODES
    ADD COLUMN DISPATCHED VARCHAR(24) NULL;

-- The recovery scans the unfinished nodes only
CREATE INDEX IF NOT EXISTS WKF_INSTANCE_NODES_IX_UNFINISHED
    ON WKF_INSTANCE_NODES (INSTANCE_ID)
    WHERE STATUS IN ('CREATED', 'WAITING', 'STARTED');
