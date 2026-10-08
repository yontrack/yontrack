-- #2032 Agents: the actor of the data and of the override of an admission rule for a pipeline
--
-- Same shape and same meaning as the ACTOR columns of V102: NULL is a person, and there is no
-- backfill.

ALTER TABLE ENV_SLOT_PIPELINE_ADMISSION_RULE_STATUS
    ADD COLUMN DATA_ACTOR     JSONB NULL,
    ADD COLUMN OVERRIDE_ACTOR JSONB NULL;
