-- 86. Search documents per entity (#1878)
--
-- The documents of a deleted branch or build, and of the builds of a deleted branch, are deleted
-- by their entity: builds are deleted in bulk by the retention jobs, and this must not scan the
-- whole table.
CREATE INDEX SEARCH_DOCUMENTS_IX_ENTITY ON SEARCH_DOCUMENTS (ENTITY_TYPE, ENTITY_ID);
