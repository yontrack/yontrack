-- Runs at startup, before the pending V101 contract which then drops what this creates
CREATE TABLE OLD_STUFF
(
    ID   SERIAL PRIMARY KEY NOT NULL,
    NAME TEXT               NULL
);
CREATE INDEX OLD_INDEX ON OLD_STUFF (NAME);
ALTER TABLE BUILDS
    ADD COLUMN OLD_COLUMN TEXT NULL;
-- Created here and dropped by a later contract: the normal life of a name
CREATE TABLE TEMPORARY_STUFF
(
    ID SERIAL PRIMARY KEY NOT NULL
);
