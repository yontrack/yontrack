-- #2026 Agent policy: a slot admits agents or not.
--
-- Slots carry no properties, so the flag is a column. FALSE by default: no slot admits agents
-- until somebody decides it does.

ALTER TABLE ENV_SLOTS
    ADD COLUMN AGENTS_ADMITTED BOOLEAN NOT NULL DEFAULT FALSE;
