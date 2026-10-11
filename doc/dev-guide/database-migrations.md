# Database migrations: expand and contract

Yontrack upgrades within a major without downtime (`initiative: no-downtime`): during a rolling
upgrade the **previous version keeps serving while the new one migrates the shared schema**. A
migration must therefore never break, nor block, the previous version. Destructive changes are not
forbidden: they move to a second phase, which runs once the previous version is gone (#2057).

## Two phases, two locations

| Phase    | Location                                             | History table             | Runs                                     |
|----------|------------------------------------------------------|---------------------------|------------------------------------------|
| Expand   | `ontrack-database/src/main/resources/db/migration/` | `schema_version`          | at startup, always                       |
| Contract | `ontrack-database/src/main/resources/db/contract/`  | `schema_version_contract` | after the expand phase - see below       |

- Both locations share **one version sequence**: a contract file takes the next free `V<n>` like any
  migration. A version used in both locations fails the build.
- Each history table records the files of its own location only.
- `StartupStrategy` runs the expand migrations, then the contract ones through `ContractMigrations`
  (`ontrack-service`, package `support`).

## When the contract phase runs

| Property                                           | Value               | Behaviour                                                                                                                                                                         |
|----------------------------------------------------|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ontrack.config.database.contract-migrations`      | `startup` (default) | After the expand migrations, the pending contracts run, in version order. Dev stacks, integration tests, KDSL and Docker Compose: an upgrade there restarts Yontrack anyway. |
|                                                    | `deferred`          | Startup runs the expand migrations only, and logs the pending contracts.                                                                                                          |
| `ontrack.config.database.run-contract-and-exit`    | `true`              | The regular image runs the pending expand migrations (none, after a rollout), then the pending contracts, logs what it applied and exits: 0, or 1 on a failure. Used by the post-upgrade hook Job of the Helm chart (yontrack-chart#151). |

The run-and-exit mode stops the application while its context starts, right after the migrations:
no startup service runs, and the web server never listens. Skipped versions need nothing special:
upgrading 6.0.1 → 6.0.4, startup applies three releases' expands, the hook three releases' contracts.

A contract which fails is rolled back with its transaction (PostgreSQL DDL is transactional), and
stays pending: the application keeps running on the expanded schema, and the next run retries it.
A script outside a transaction (`.conf` below) which fails leaves a failed row in the history: fix
it, then `flyway repair` it before running again.

## Where each statement goes

| Statement                                                                            | Expand (`db/migration`)                          | Contract (`db/contract`) |
|--------------------------------------------------------------------------------------|--------------------------------------------------|--------------------------|
| `CREATE TABLE`, `CREATE INDEX CONCURRENTLY`, `ADD COLUMN` nullable or with `DEFAULT`  | yes                                              | no                       |
| `INSERT`, `UPDATE`, `DELETE ... WHERE` (backfills)                                   | yes                                              | `UPDATE`, `DELETE` of removed features' data |
| `ADD CONSTRAINT ... NOT VALID`, then `VALIDATE CONSTRAINT` in a later migration      | yes                                              | no                       |
| `ADD CONSTRAINT ... UNIQUE USING INDEX` on an index built concurrently               | yes                                              | no                       |
| `DROP TABLE / COLUMN / INDEX / CONSTRAINT / ...`, `ALTER TABLE ... DROP ...`         | no                                               | yes                      |
| `DELETE FROM` without `WHERE`, `TRUNCATE` (as a `DELETE`)                             | no                                               | yes                      |
| `RENAME`, `ALTER COLUMN ... TYPE`, `SET NOT NULL`, `ADD COLUMN ... NOT NULL` without `DEFAULT` | no: add a new column, backfill, switch the code, and drop the old column in a contract of a later release | no |
| `CREATE INDEX` without `CONCURRENTLY`, `ADD CONSTRAINT` without `NOT VALID`          | only on a table the same script creates          | no                       |

The locking statements count as much as the destructive ones: V102 widened columns of `BUILDS`,
`EVENTS` and `PROMOTION_RUNS` under an `ACCESS EXCLUSIVE` lock, and blocked the previous version as
surely as a drop would have.

A drop ships as a contract **with the release which stops using the object**: the contracts of a
release run once the previous release, which still uses it, is gone. Never drop what the release
itself still reads. Once the contracts have run, rolling back to the previous release is no longer
possible.

An expand migration never creates a name - table, index, column - which a contract of a lower
version drops: startup runs every pending expand before any pending contract, so that contract would
drop the new object, or the expand would fail on the old one.

### `CREATE INDEX CONCURRENTLY`: outside a transaction

PostgreSQL refuses `CONCURRENTLY` in a transaction, and Flyway runs every script in one. A script
using it - and nothing else - comes with a `.conf` file of the same name, which takes it out of the
transaction:

```sql
-- V107__2100_builds_actor_index.sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS BUILDS_IX_ACTOR ON BUILDS ((ACTOR ->> 'agent'));
```

```properties
# V107__2100_builds_actor_index.sql.conf
executeInTransaction=false
```

Use `IF NOT EXISTS`: a concurrent build which fails leaves an invalid index behind, and the script is
then run again. Flyway takes a session-level advisory lock rather than a transactional one
(`FlywayConfiguration`), without which the index would wait forever for the lock's transaction.

## The rules test

`MigrationRulesRepositoryTest` (`ontrack-database`, run by `./gradlew test`) checks every migration
above its `BASELINE` - V106, the latest migration when the rules were introduced; the earlier ones
are grandfathered and never change - and fails on:

- in `db/migration`: a drop, a rename, a change of column type, a truncation, a `DELETE` without
  `WHERE`, `SET NOT NULL`, a `NOT NULL` column without `DEFAULT`, an index not created
  `CONCURRENTLY` and a constraint added without `NOT VALID` (or `USING INDEX`) - the last four
  except on a table created by the same script;
- in `db/contract`: anything else than `DROP`, `ALTER TABLE ... DROP`, `DELETE FROM` and `UPDATE`;
- an expand creating a name dropped by a contract of a lower version;
- a version used in both locations;
- a script using `CONCURRENTLY` without its `.conf` file;
- a Java or Kotlin migration, which the test cannot read: review it against the rules by hand.

`MigrationRulesTest` covers each rule with a fixture under
`ontrack-database/src/test/resources/migration/fixtures/`.

### The allow-list

A migration which breaks a rule on purpose - a table that no release reads any longer, a Kotlin data
migration - is exempted by a line of
`ontrack-database/src/test/resources/migration/expand-allowlist.txt`:

```text
V112: CI_LEGACY is read by no release since 6.0 (#2101)
```

A line which exempts nothing fails the test, like the deprecation markers baseline: delete it with
the migration it was written for. The allow-list never exempts a version used in both locations.

## Testing a migration against the previous release

The question is whether the **previous release** still works on the expanded schema.

- Write the migration, then an integration test in the style of `TwoPhaseMigrationsIT`
  (`ontrack-service`): migrate a `TemporaryDatabase` in `deferred` mode, run the statements of the
  previous release - the queries of its repository, copied from its tag - and check they still work;
  then run the contracts and check what they dropped is gone.
- For a change of a hot table, look at the locks it takes: anything stronger than
  `SHARE UPDATE EXCLUSIVE` on a large table blocks the previous release for its duration.
- The end-to-end proof is the rolling-upgrade CI job of the chart (yontrack-chart#151), which
  upgrades under load and fails on any failed request.

The 5.5 → 6.0 upgrade is a major one and keeps its downtime: the existing 6.0 migrations are not
reworked.
