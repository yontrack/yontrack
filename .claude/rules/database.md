---
paths:
  - "ontrack-database/**"
---

# Database rules (Flyway)

Migrations are SQL files named `V{N}__short_description.sql`, where `N` is the next free number of
one version sequence shared by two locations (#2057, `doc/dev-guide/database-migrations.md`):

- `ontrack-database/src/main/resources/db/migration/` - the **expand** phase, run at startup while
  the previous release still serves: additive only, and taking no lock that blocks it.
- `ontrack-database/src/main/resources/db/contract/` - the **contract** phase, run once the previous
  release is gone (history table `schema_version_contract`): `DROP`, `ALTER TABLE ... DROP`,
  `DELETE FROM` and `UPDATE` of removed features' data, nothing else.

- **Never** modify an existing migration file — always add a new one.
- **Never** put a migration in a patch release. A patch is cherry-picked onto a `release/X.Y`
  branch while `main` keeps moving, so a `V82` on the patch branch and an unrelated `V82` on `main`
  give any user upgrading from the patch to the next minor a checksum conflict. If a fix needs a
  schema change it is not a patch — see `doc/dev-guide/patch-release.md`.
- **Always** add `ON DELETE CASCADE` on FK references to entity tables.
- Use `SERIAL PRIMARY KEY NOT NULL` for auto-increment primary keys.

```sql
-- V107__my_new_feature.sql: a new table, indexed by the same script
CREATE TABLE MY_TABLE (
    ID          SERIAL PRIMARY KEY NOT NULL,
    NAME        VARCHAR(100)       NOT NULL,
    ENTITY_ID   INTEGER            NOT NULL REFERENCES ENTITIES (ID) ON DELETE CASCADE
);
CREATE INDEX MY_TABLE_ENTITY_IDX ON MY_TABLE (ENTITY_ID);
```

## Expand and contract

`MigrationRulesRepositoryTest` (`./gradlew :ontrack-database:test`) enforces these on every
migration above V106, the baseline - never change the files up to it:

- **Never** in `db/migration`: `DROP`, `RENAME`, `ALTER COLUMN ... TYPE`, `TRUNCATE`, `DELETE`
  without `WHERE`, `SET NOT NULL`, `ADD COLUMN ... NOT NULL` without `DEFAULT`. A drop or a deletion
  goes to `db/contract`, in the release which stops using the object; a rename or a change of type
  is a new column, a backfill, and a contract dropping the old one.
- **Always** `CREATE INDEX CONCURRENTLY` and `ADD CONSTRAINT ... NOT VALID` (or `UNIQUE USING
  INDEX`) on an existing table - a table created by the same script is exempt. A script using
  `CONCURRENTLY` contains nothing else and comes with a `V{N}__x.sql.conf` file holding
  `executeInTransaction=false`.
- **Never** create in `db/migration` a name which a contract of a lower version drops: startup runs
  every pending expand before any pending contract.
- **Never** use a version in both locations.
- An exception - a table no release reads, a Kotlin migration, which the test cannot read - goes in
  `ontrack-database/src/test/resources/migration/expand-allowlist.txt`, one `V{N}: reason (#issue)`
  per line. A line exempting nothing fails the test: delete it.
- Test a migration against the previous release with an IT in the style of `TwoPhaseMigrationsIT`
  (`ontrack-service`): expand only (`deferred`), run the previous release's query, then the
  contracts.
