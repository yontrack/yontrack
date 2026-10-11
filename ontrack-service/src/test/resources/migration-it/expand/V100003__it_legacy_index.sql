-- Fixture of TwoPhaseMigrationsIT (#2057): an index created concurrently, outside a transaction
-- through the .conf file of the script
CREATE INDEX CONCURRENTLY IT_LEGACY_IX_NAME ON IT_LEGACY (NAME);
