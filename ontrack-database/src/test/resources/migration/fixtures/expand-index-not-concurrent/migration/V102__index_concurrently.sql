-- Allowed, outside a transaction (see the .conf file)
CREATE INDEX CONCURRENTLY IF NOT EXISTS BUILDS_IX_SOURCE ON BUILDS (SOURCE);
