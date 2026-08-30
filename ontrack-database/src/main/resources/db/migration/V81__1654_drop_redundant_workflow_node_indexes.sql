-- Drops two redundant indexes on WKF_INSTANCE_NODES (INSTANCE_ID).
--
-- WKF_INSTANCE_NODES_PK is a btree on (INSTANCE_ID, NODE_ID). Its leading column already serves
-- every lookup the repository makes -- INSTANCE_ID alone, INSTANCE_ID with NODE_ID, and the join
-- against WKF_INSTANCES -- so a standalone index on (INSTANCE_ID) adds nothing to reads while being
-- maintained on every node status transition, which is one of the hottest write paths there is.
--
--  * WKF_INSTANCE_NODES_IX_INSTANCE (V47) duplicates the primary key prefix.
--  * WKF_INSTANCE_CONTEXT_IX_INSTANCE (V55) is named after WKF_INSTANCE_CONTEXT but was created on
--    WKF_INSTANCE_NODES by mistake, so it is a second duplicate of the same prefix. The context
--    table needs no index of its own: WKF_INSTANCE_CONTEXT_PK is (INSTANCE_ID, CONTEXT_ID) and its
--    prefix serves the lookups there too.
--
-- Dropping these also improves the single-node lookups: the planner was preferring the narrow
-- one-column index and then filtering on NODE_ID, where the primary key matches both columns
-- directly. WorkflowInstanceIndexUsageIT asserts every affected query shape stays index-served.
--
-- One shape gets slightly worse, knowingly: the instance/node join behind the workflow audit
-- listing does a full index-only scan, and the primary key is wider than the one-column index it
-- was using, so that scan reads more pages. That is an occasional admin listing, traded against two
-- index maintenance operations removed from every node status transition.
--
-- Note that the order rows come back in changes with the index: the node queries have no ORDER BY,
-- and the executions used to arrive in insertion order by accident. WorkflowInstanceRepository now
-- orders them explicitly by the node declaration order of the workflow.

DROP INDEX IF EXISTS WKF_INSTANCE_NODES_IX_INSTANCE;
DROP INDEX IF EXISTS WKF_INSTANCE_CONTEXT_IX_INSTANCE;
