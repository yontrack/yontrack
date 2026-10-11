Contract migrations (#2057): the second phase of the database migrations, which drops what no
running version of Yontrack uses any longer. They run after the expand migrations of db/migration,
at startup or after a rolling upgrade, and are recorded in schema_version_contract.

A contract migration takes the next free V<n> of the version sequence shared with db/migration.
See doc/dev-guide/database-migrations.md.
