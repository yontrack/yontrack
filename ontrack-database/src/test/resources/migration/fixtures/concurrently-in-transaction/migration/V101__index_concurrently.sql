-- Postgres refuses CONCURRENTLY in a transaction: the script needs its .conf file
CREATE INDEX CONCURRENTLY BUILDS_IX_ACTOR ON BUILDS (ACTOR);
