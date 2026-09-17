-- 82. Removal of the automatic labels (label providers)

-- Computed labels (their PROJECT_LABEL associations follow by cascade)
DELETE
FROM LABEL
WHERE COMPUTED_BY IS NOT NULL;

ALTER TABLE LABEL
    DROP COLUMN COMPUTED_BY;

-- Settings of the label provider job
DELETE
FROM SETTINGS
WHERE CATEGORY = 'net.nemerosa.ontrack.model.settings.LabelProviderJobSettings';
