# Deprecations — Yontrack 6.0

Outcome of the grilling session of 2026-09-23 on managing deprecations across the 5 → 6 major:
what V5 deprecated and V6 removes, what V6 deprecates for removal in V7, and how an upgrader learns
about both.

**Goal:** 6.0 ships with no V5 deprecation left behind, every new deprecation is announced in one
greppable format with a runtime warning, and the *Migration to V6* page tells a V5 user everything
they must do to upgrade.

The issue breakdown is in [issues.md](issues.md), under the `deprecation` label.

## Where we start from

- **79 `@Deprecated` annotations** in the backend (64 in main code, 15 in tests and fixtures),
  **14 deprecated GraphQL fields**, 2 deprecated REST endpoints, 2 deprecated config properties.
  Messages are free text: "Will be removed in V6", "… in V5", "… in 4.6", or no version at all.
- **Only 8 symbols are tagged "remove in V6"**:
  - `Account.name` and the user templating field `NAME` (`UserTemplatingFunctionField.kt`);
  - `PromotionLevel.autoVersioningTrail` and `PromotionRun.autoVersioningTrail` (the KDSL still
    queries the latter; the web UI already uses `autoVersioningTrailPaginated`);
  - `OntrackGitHubClient.getPullRequest`;
  - `SyncConfig` and `SyncPolicy`;
  - the `SCMService` family (with `SCMServiceProvider` and `SCMServiceDetector`), 8 main-code callers;
  - `GitService.collectIndexableGitCommitForBuild`;
  - the JGit dependency (`build.gradle.kts`, a TODO).
- **Overdue — tagged V5 or earlier and still there**:
  - `EntityDataService` ("use `EntityStore`"), still used by 8 main-code files;
  - `Reordering`, whose message is wrong: it is the live input of the reorder mutations;
  - pure-Git support: `GitProjectConfigurationPropertyType`, `GitSCMExtension` (its runtime error
    says "will be removed in version 5"), `GitHubSCMExtension.getBranchesForCommit` on a local clone;
  - GraphQL `EventSubscriptionPayload.id`, `SearchResult.page`, `SearchResult.uri`, `VersionInfo.date`;
  - subscription `name`, "will be required in V5", in the GraphQL input, CasC and the KDSL;
  - the legacy branch-links setting (the form says "Will be removed in V5");
  - the indicators items, which go with the indicators deletion decided in the
    [scorecard session](../2026-09-scorecard.md).
- **Deprecated with no version, about 37 items** — mostly internal, many with no caller. The
  external ones: `POST …/image` on promotion levels and predefined promotion levels (the KDSL still
  calls the latter), `PromotionLevel.promotionRuns` (3 UI files), `AutoVersioningOrder.targetPaths`,
  GitHub configuration `password`, `HookResponse.info`, config properties
  `…elastic…apiCompatibilityMode` (dead) and `…queue.general.warnIfAsync` (misnamed), env var
  `ONTRACK_SCM_ISSUES`, kebab-case CasC aliases.
- **Auto-versioning legacy attribute names**: #1515 promised publicly that the 8 `@JsonAlias`
  names (`project`, `branch`, `promotion`, `path`, `property`, `propertyType`, `regex`,
  `propertyRegex`) are "not supported any longer starting from V6". No issue carries it out.
- **Frontend**: `useGraphQLClient` in 106 files, the old `services/useQuery` in 22, `useReloadState`
  in 7, `getUserErrors` in 4, deprecated fragments in 4. `doc/dev-guide/ui/ui-graphql-call.md` still
  recommends `useGraphQLClient`.
- **Data conversions outside Flyway**: 11 `StartupService` migrations run once at boot and record
  themselves as done in storage. Flyway itself is at `V84` on `v6`; the last migration shipped in
  5.0.0 is `V68__V5_av_audit.sql`.
- **Docs**: `ontrack-docs/docs/content/appendix/migration-to-v6.md` exists, covering Spring Boot 4,
  Jackson 3 and Java 25, with "For extension authors" subsections. It says nothing of deprecations.
  `ontrack-docs/src/docs/` is dead asciidoc.
- **No written policy**: no ADR, no `CLAUDE.md` rule, nothing in `doc/dev-guide/major-branch.md`.
  The only rule of thumb is in the [Postgres search session](../2026-09-postgres-search/README.md):
  the old `search(...)` query is a deprecated wrapper in 6.0, removed in 7.0.

## Decisions

### Scope

- **Only external contracts are user-facing deprecations**: the GraphQL API, REST, CasC keys,
  `ontrack.*` configuration properties and environment variables, Helm values, KDSL / CLI,
  notification channels and templating, event types. They go in the migration page, and they get
  the runtime warning.
- **There is no extension SPI**: Yontrack extensions can no longer be written outside the
  repository, so Kotlin interfaces and base classes are internal code.
- **Internal code** is cleaned up the same way — removed by default — but never appears in the
  migration page and gets no runtime warning.

### Policy

- **Deprecated in major N, removed in N+1, by default.** Every V5 deprecation is removed in V6. An
  exception is explicit: it is re-tagged `Removed in V7` and listed on the migration page as
  carried over, never kept silently.
- **One marker format on every surface**:
  - Kotlin / Java: `@Deprecated("Removed in V7. Use X instead. See #NNNN")`
  - GraphQL: `@deprecated(reason: "Removed in V7. Use X instead. See #NNNN")`
  - Frontend: `/** @deprecated Removed in V7. Use X instead. See #NNNN */`

  The literal `Removed in V7`, a replacement (or `No replacement.`), and an issue number. The V7
  cleanup is then one grep.
- **A test enforces it**, run by `./gradlew test` on `v6`. It scans Kotlin / Java `@Deprecated`,
  GraphQL deprecations and frontend `@deprecated`, and fails when an entry lacks the marker or the
  issue number, or when a deprecated *external* item is not named on the migration page.
  - It accepts both `Removed in V6` and `Removed in V7`: the 5.5 readiness patch (below) brings
    `Removed in V6` markers into `v6` through the main → v6 merge, and they stay until each
    removal issue lands.
  - The items that do not conform today are listed in a baseline the test tolerates; each removal
    issue shrinks it, and no entry may be added to it.
- **Runtime warnings, for external contracts only**:
  - one Micrometer counter, `ontrack.deprecated.usage` (`ontrack_deprecated_usage_total` in
    Prometheus), tagged `surface` (`graphql`, `rest`, `config`, `casc`, `env`, `templating`) and
    `item`;
  - a `WARN` log once per item per JVM run;
  - a GraphQL instrumentation that counts every deprecated field or argument actually queried —
    the API is the largest surface, and the one admins cannot audit otherwise;
  - no UI. The metric appears in the generated metrics docs, and an admin can graph it before
    upgrading.
- **The policy is recorded as ADR 0017** (`docs/adr/0017-deprecation-and-removal-across-majors.md`).
  `CLAUDE.md` carries the short rules and links to it.

### Upgrade path

- **6.0 accepts an upgrade from any 5.x release.** Flyway replays every migration, so the schema is
  safe from any 5.x starting point.
- **6.0 refuses to start on a pre-5.0 database**: at startup, before Flyway runs, a database that
  has a Flyway history but no `V68` stops Yontrack with "upgrade to any 5.x release first". A fresh
  database passes.
- **What the V6 cleanup may delete** — the real upgrade risk is not Flyway but code-based data
  conversions:
  - anything present in **5.0.0** may be deleted: startup migrations, and readers of legacy
    *stored* formats (`fromStorage` fallbacks, `@JsonAlias` on persisted data). Every 5.x install
    ran 5.0.0's code once;
  - anything added after 5.0.0 stays until V7, unless a Flyway migration converts the data instead;
  - aliases on *input only* (API, CasC) are not data conversions: they follow the deprecation
    policy above.

  The general rule — the floor is the last Flyway version of the previous major's `.0` — goes into
  `doc/dev-guide/major-branch.md`.
- **Startup migrations** under that rule:

  | Migration                                | First in     | V6                          |
  |------------------------------------------|--------------|-----------------------------|
  | `GitHubConfigurationTokenMigration`      | 4.0.44       | deleted                     |
  | `NotificationRecordIDMigration`          | 4.8.47       | deleted                     |
  | `SubscriptionNameMigration`              | 4.9.0        | deleted                     |
  | `WorkflowInstanceRepositoryMigration`    | 4.11-alpha   | deleted                     |
  | `DashboardWidgetHeight5Migration`        | 4.11.32      | deleted                     |
  | `AutoVersioningAuditStoreMigration`      | 5.0-beta.16  | deleted                     |
  | `BranchStatusesWidgetWarningMigration` (and `AbstractMigration`, its only user) | 5.0-beta.30 | deleted |
  | `ElasticSearchV5Migration`               | 5.0-beta.0   | left to the Postgres search removal of Elasticsearch — it is the index-reset mechanism, not a one-off |
  | `AutoVersioningTrackingStoreMigration`   | 5.0.23       | **kept** until V7           |
  | `GitLabConfigurationTokenMigration`      | 5.5.0        | **kept** until V7           |

### 5.5 readiness patch

- **One 5.5 patch release warns about everything V6 removes.** Every *external* item V6 removes
  gets a `Removed in V6` marker and the runtime warning, whether V5 announced it or not:
  - the GraphQL fields tagged V6 and the templating field `NAME`;
  - the overdue V5 items (GraphQL fields, nameless subscriptions, the branch-links setting,
    pure-Git configuration);
  - the untagged external removals (the `POST …/image` endpoints, `promotionRuns`, `targetPaths`,
    `HookResponse.info`, `apiCompatibilityMode`);
  - the auto-versioning legacy attribute names;
  - indicators usage (GraphQL and settings) and the Elasticsearch configuration.

  Internal code gets no warning.
- **It is where the runtime-warning mechanism is built**, once: on `main`, cherry-picked to
  `release/5.5`, and brought into `v6` by the regular main → v6 merge. The V6 issue that adds new
  V7 deprecations depends on it.
- **No Flyway migration in it**, as for any patch (`doc/dev-guide/patch-release.md`). It only adds
  markers and warnings, so none is needed.
- **One issue**, `ready-for-agent`, to be picked later.

### What V6 removes

All V5 deprecations go, with these specific calls:

- **Items tagged V6**: removed as tagged. The KDSL moves to `autoVersioningTrailPaginated`
  first.
- **`Reordering`**: un-deprecated — the message is wrong, it is the live mutation input.
- **`EntityDataService`**: its callers move to `EntityStore`, its data moves with a Flyway
  migration, and it is deleted. Its own issue; if it turns out too large, it is the one allowed
  carry-over, re-tagged `Removed in V7`.
- **Pure-Git support** goes entirely: `GitProjectConfigurationPropertyType` (a Flyway migration
  deletes its stored properties), `GitSCMExtension`, the JGit dependency, and
  `GitHubSCMExtension.getBranchesForCommit` moves to the GitHub API.
- **Untagged external items**, deprecated in V5 without a version, are removed: both
  `POST …/image` endpoints (the KDSL moves to `PUT` first), `PromotionLevel.promotionRuns` (the 3
  UI files move to `promotionRunsPaginated` first), `AutoVersioningOrder.targetPaths`,
  `HookResponse.info`, `apiCompatibilityMode`, the legacy branch-links setting.
- **`NotificationResultType.TIMEOUT`**: un-deprecated — its own message says no removal is planned.
- **Auto-versioning legacy attribute names** are removed, as #1515 promised. Configurations still
  using them are rejected; the migration page says so and lists the new names.
- **Subscription `name` becomes required** in the GraphQL input, CasC and the KDSL, and
  `EventSubscriptionPayload.id` goes. No data migration is needed: `SubscriptionNameMigration` has
  named every subscription since 4.9.
- **Internal code**: the untagged internal items and test fixtures are removed, their callers
  migrated.
- **Frontend helpers** — its own issue: `useReloadState`, `getUserErrors`, the deprecated
  fragments, `closeUri` and the old `services/useQuery` go; `useGraphQLClient` is migrated in
  batches with `/migrate-use-graphql-client`, then deleted; ESLint `no-restricted-imports` rules
  cover each of them; `doc/dev-guide/ui/ui-graphql-call.md` stops recommending the old hook. Being
  internal, it may continue after the cutover if unfinished — no doc impact, no V7 deadline.
- **`ontrack-docs/src/docs/`** is deleted, and the `CLAUDE.md` rule about it goes with it. The
  migration page does not mention it.
- **Removals decided elsewhere** — indicators ([scorecard](../2026-09-scorecard.md)) and
  Elasticsearch search ([Postgres search](../2026-09-postgres-search/README.md)) — stay decided
  there; the migration page lists them.

### What V6 deprecates for V7

Items whose users were never told, so they cannot go straight away:

- GitHub configuration `password` authentication — use a token or a GitHub App;
- the `ONTRACK_SCM_ISSUES` environment variable;
- the kebab-case CasC aliases (`GitHubEngineConfiguration`, subscriptions, webhooks);
- `…queue.general.warnIfAsync`, renamed `warnIfSync`; the old name stays as an alias.

Each gets the V7 marker and the runtime warning. The deprecated `search(...)` wrapper from the
Postgres search session joins this list when it lands.

### Documentation

- **The page stays `ontrack-docs/docs/content/appendix/migration-to-v6.md`, "Migration to V6"**,
  path and title unchanged.
- **It covers everything a V5 user needs to upgrade**, not only deprecations, in four sections:
  1. *Upgrade path* — from any 5.x; 4.x goes through 5.x first; watch
     `ontrack_deprecated_usage_total` on 5.5.x before upgrading.
  2. *Breaking changes* — Spring Boot 4, Jackson 3 and Java 25 (the existing content), plus the
     removed features: indicators, Elasticsearch search, pure-Git support.
  3. *Removed* — every V5 deprecation removed, with its replacement.
  4. *Newly deprecated* — every item removed in V7, with its replacement.
- **The "For extension authors" subsections are dropped**: there are no extension authors. What
  they say that still matters to deployers, API / KDSL clients or CasC users moves to those
  audiences.
- **Agents write it all.** One issue restructures the page and writes the initial content; from
  then on every removal or deprecation issue updates the page in the same commit. The marker test
  checks that each deprecated external item is named there.

### Cutover

`doc/dev-guide/major-branch.md` does not describe the v6 → main cutover yet. Its first written
item is a gate: **zero `Removed in V6` markers and an empty baseline** before `v6` becomes `main`.

### Definition of done

- **Every removal issue checks the demo seed, the mobile UI and the KDSL acceptance tests** for the
  removed item, migrates them if they use it, and says which way it decided — "no demo / mobile
  impact because …". A silent break of the demo on `v6.dev` is the likely failure.
- **Every removal or deprecation issue updates the migration page** in the same commit.

### Out of scope

- The indicators and Elasticsearch removals themselves (their own sessions).
- The Helm chart (`yontrack/yontrack-chart`) — the Postgres search session carries its v6 change.
- Compose files still defaulting to image tag `5` and the `nemerosa/ontrack*` names.
- Unifying the UI environment variable prefixes (`ONTRACK_URL`, `NEXTAUTH_*`, `YONTRACK_UI_*`).

## Sources

- `doc/dev-guide/major-branch.md`, `doc/dev-guide/patch-release.md`
- `ontrack-docs/docs/content/appendix/migration-to-v6.md`
- #1515 — old auto-versioning attribute names supported until V6
- [Scorecard session](../2026-09-scorecard.md), [harvesting brief](../2026-09-harvesting-brief.md) —
  indicators deleted in 6.0
- [Postgres search session](../2026-09-postgres-search/README.md) — Elasticsearch search removed,
  `search(...)` deprecated for 7.0
- ADR 0015, ADR 0016 — Spring Boot 4 and Jackson 3
