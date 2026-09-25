-- 85. Search documents (#1877)
--
-- Search runs on Postgres: every indexer writes its search documents into this one table, and
-- one ranked query runs across every search result type.
--
-- pg_trgm is a trusted contrib extension since Postgres 13: a database owner can create it
-- without superuser. When the database user cannot, it must be created beforehand.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE SEARCH_DOCUMENTS
(
    ID          SERIAL PRIMARY KEY NOT NULL,
    -- ID of the search result type
    TYPE        VARCHAR(100)       NOT NULL,
    -- Unique within the type
    KEY         TEXT               NOT NULL,
    -- Access, and deletion of the document with its project. NULL = the document belongs to no
    -- project, and its type declares the global function granting access to it
    PROJECT_ID  INTEGER            NULL,
    -- Optional project entity being described
    ENTITY_TYPE VARCHAR(40)        NULL,
    ENTITY_ID   INTEGER            NULL,
    -- Shown, and the strongest matching field
    TITLE       TEXT               NOT NULL,
    -- Lower-cased identifiers, each one on its own line and the whole text starting and ending
    -- with a new line, so that an exact or prefix match is a LIKE on '%\n<value>\n%' or
    -- '%\n<value>%', which the trigram index serves
    IDENTIFIERS TEXT               NOT NULL,
    -- Free text, lowest weight
    TEXT        TEXT               NULL,
    -- Full-text vector, with the language-neutral 'simple' configuration: no stemming, no stop
    -- words, since most queries are identifiers
    TSV         TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple'::regconfig, TITLE), 'A') ||
        setweight(to_tsvector('simple'::regconfig, IDENTIFIERS), 'A') ||
        setweight(to_tsvector('simple'::regconfig, coalesce(TEXT, '')), 'C')
        ) STORED,
    -- What the frontend needs to render and link the result
    DATA        JSONB              NOT NULL,
    -- Recency of the thing being described, breaking the ties of the ranking
    UPDATED_AT  TIMESTAMP          NOT NULL,
    -- Time of the last write of the document. A rebuild deletes the documents of its type which it
    -- did not write, i.e. indexed before the rebuild started
    INDEXED_AT  TIMESTAMP          NOT NULL,
    CONSTRAINT SEARCH_DOCUMENTS_UQ_TYPE_KEY UNIQUE (TYPE, KEY),
    CONSTRAINT SEARCH_DOCUMENTS_FK_PROJECT FOREIGN KEY (PROJECT_ID) REFERENCES PROJECTS (ID) ON DELETE CASCADE
);

-- Full-text tier
CREATE INDEX SEARCH_DOCUMENTS_IX_TSV ON SEARCH_DOCUMENTS USING GIN (TSV);
-- Exact, prefix and trigram tiers on the identifiers
CREATE INDEX SEARCH_DOCUMENTS_IX_IDENTIFIERS_TRGM ON SEARCH_DOCUMENTS USING GIN (IDENTIFIERS gin_trgm_ops);
-- Trigram tier on the title
CREATE INDEX SEARCH_DOCUMENTS_IX_TITLE_TRGM ON SEARCH_DOCUMENTS USING GIN (TITLE gin_trgm_ops);
-- Prefix tier on the title
CREATE INDEX SEARCH_DOCUMENTS_IX_TITLE_PREFIX ON SEARCH_DOCUMENTS (lower(TITLE) text_pattern_ops);
-- Restriction on types and access
CREATE INDEX SEARCH_DOCUMENTS_IX_TYPE_PROJECT ON SEARCH_DOCUMENTS (TYPE, PROJECT_ID);
-- Deletion of the documents of a project
CREATE INDEX SEARCH_DOCUMENTS_IX_PROJECT ON SEARCH_DOCUMENTS (PROJECT_ID);
