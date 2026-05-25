-- 77. Auto-versioning config push mode

ALTER TABLE AV_AUDIT
    ADD COLUMN PUSH_MODE VARCHAR(128) DEFAULT 'PR';
