-- #1952 New GitHub configurations no longer send the workflow ID by default.
-- The existing ones, stored without the key, keep sending it.
UPDATE CONFIGURATIONS
SET CONTENT = CONTENT || '{"workflowSendId": true}'::JSONB
WHERE TYPE = 'net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration'
  AND NOT (CONTENT ? 'workflowSendId');
