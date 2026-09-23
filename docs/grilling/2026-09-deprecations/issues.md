# Deprecations — issue breakdown

Breakdown of [README.md](README.md) into agent-sized issues. **Not created yet** — the GitHub
column is filled when they are.

Every issue carries `deprecation`, `status:todo` and `ready-for-agent`, and links back to the
README section it implements. D0 is on milestone `5.5` and based on `main`; all others are on
milestone `6.0` and based on `v6`. Dependencies are recorded as native GitHub dependencies as well
as prose.

Every removal or deprecation issue also:

- updates `ontrack-docs/docs/content/appendix/migration-to-v6.md` in the same commit (*Removed* or
  *Newly deprecated* section, with the replacement) — external items only;
- removes its items from the marker-test baseline (D1);
- checks the demo seed, the mobile UI and the KDSL acceptance tests for the removed item, migrates
  them if needed, and says which way it decided.

| #   | GitHub | Issue                                                                  | Repo / base                      | Depends on |
|-----|--------|------------------------------------------------------------------------|----------------------------------|------------|
| D0  | —      | 5.5 readiness: runtime warnings and `Removed in V6` markers            | `yontrack` / `main` → `release/5.5` | —       |
| D1  | —      | Deprecation policy: ADR 0017, `CLAUDE.md`, `major-branch.md`, marker test | `yontrack` / `v6`             | —          |
| D2  | —      | *Migration to V6*: restructure and initial content                     | `yontrack` / `v6`                | —          |
| D3  | —      | Upgrade floor at 5.0, delete the startup migrations present in 5.0.0   | `yontrack` / `v6`                | D2         |
| D4  | —      | Remove the backend items tagged V6                                     | `yontrack` / `v6`                | D1, D2     |
| D5  | —      | Remove the deprecated GraphQL fields                                   | `yontrack` / `v6`                | D1, D2     |
| D6  | —      | Remove the deprecated REST endpoints and other external items          | `yontrack` / `v6`                | D1, D2     |
| D7  | —      | Deprecate for V7: GitHub password, `ONTRACK_SCM_ISSUES`, kebab-case CasC aliases, `warnIfAsync` | `yontrack` / `v6` | D0, D1, D2 |
| D8  | —      | Remove pure-Git support                                                | `yontrack` / `v6`                | D4         |
| D9  | —      | `EntityDataService` → `EntityStore`                                    | `yontrack` / `v6`                | D1         |
| D10 | —      | Remove the auto-versioning legacy attribute names (#1515)              | `yontrack` / `v6`                | D1, D2     |
| D11 | —      | Subscription `name` becomes required                                   | `yontrack` / `v6`                | D1, D2     |
| D12 | —      | Internal backend cleanup                                               | `yontrack` / `v6`                | D1         |
| D13 | —      | Frontend deprecated helpers and `useGraphQLClient`                     | `yontrack` / `v6`                | D1         |
| D14 | —      | Delete `ontrack-docs/src/docs`                                         | `yontrack` / `v6`                | —          |

D7 depends on D0 *having reached `v6`* through the main → v6 merge, not only on D0 being merged
into `main`.

---

## D0 — 5.5 readiness: runtime warnings and `Removed in V6` markers

README *Policy* (runtime warnings), *5.5 readiness patch*.

- The runtime-warning mechanism: Micrometer counter `ontrack.deprecated.usage` tagged `surface` and
  `item`, a `WARN` log once per item per JVM run, a GraphQL instrumentation counting every
  deprecated field or argument actually queried. Declared in the metrics so the generated docs
  list it.
- `Removed in V6` markers and warnings on every external item V6 removes: the GraphQL fields tagged
  V6, templating `NAME`, the overdue V5 GraphQL fields, nameless subscriptions, the branch-links
  setting, pure-Git configuration, both `POST …/image` endpoints, `promotionRuns`, `targetPaths`,
  `HookResponse.info`, `apiCompatibilityMode`, the auto-versioning legacy attribute names,
  indicators usage (GraphQL and settings), the Elasticsearch configuration.
- No Flyway migration. Follows `doc/dev-guide/patch-release.md`: lands on `main`, cherry-picked to
  `release/5.5`, released as a 5.5 patch.
- Done when a 5.5 patch warns about each item, and the counter shows up in `/manage/prometheus`.

## D1 — Deprecation policy: ADR 0017, `CLAUDE.md`, `major-branch.md`, marker test

README *Scope*, *Policy*, *Upgrade path* (the rule), *Cutover*.

- `docs/adr/0017-deprecation-and-removal-across-majors.md`: deprecate in N, remove in N+1; the
  marker format; runtime warnings for external contracts only; the upgrade floor at the previous
  major's `.0`; what data-conversion code the cleanup may delete.
- `CLAUDE.md`: the short rules — marker format, update the migration page in the same commit,
  removal DoD — and a link to the ADR.
- `doc/dev-guide/major-branch.md`: the removal rule, and the first item of the cutover procedure:
  zero `Removed in V6` markers and an empty baseline.
- The marker test, run by `./gradlew test`: scans Kotlin / Java `@Deprecated`, GraphQL
  deprecations and frontend `@deprecated`; fails on a missing `Removed in V6` / `Removed in V7`,
  replacement or issue number, and on a deprecated external item not named on the migration page.
  The items that do not conform today go in a baseline the test tolerates and which may only
  shrink.
- Done when the test is green on `v6` with the baseline, and fails on a new non-conforming
  `@Deprecated`.

## D2 — *Migration to V6*: restructure and initial content

README *Documentation*.

- `ontrack-docs/docs/content/appendix/migration-to-v6.md` keeps its path and title. Four sections:
  *Upgrade path*, *Breaking changes*, *Removed*, *Newly deprecated*.
- *Upgrade path*: from any 5.x; 4.x goes through 5.x first; watch `ontrack_deprecated_usage_total`
  on 5.5.x before upgrading.
- *Breaking changes*: the existing Spring Boot 4, Jackson 3 and Java 25 content, and the removed
  features — indicators, Elasticsearch search, pure-Git support — each linking to its detail.
- The "For extension authors" subsections are dropped; what still matters moves to deployers,
  API / KDSL clients or CasC users.
- Done when `./gradlew :ontrack-docs:buildDocs` renders the page with its four sections.

## D3 — Upgrade floor at 5.0, delete the startup migrations present in 5.0.0

README *Upgrade path*.

- At startup, before Flyway runs: a database with a Flyway history but no `V68` stops Yontrack
  with "upgrade to any 5.x release first". A fresh database passes.
- Delete `GitHubConfigurationTokenMigration`, `NotificationRecordIDMigration`,
  `SubscriptionNameMigration`, `WorkflowInstanceRepositoryMigration`,
  `DashboardWidgetHeight5Migration`, `AutoVersioningAuditStoreMigration`,
  `BranchStatusesWidgetWarningMigration` and `AbstractMigration`. Keep
  `AutoVersioningTrackingStoreMigration` and `GitLabConfigurationTokenMigration`; leave
  `ElasticSearchV5Migration` to the Elasticsearch removal.
- Integration tests: a pre-5.0 history is refused, a 5.0 history and an empty database start.
- The *Upgrade path* section of the migration page states the check.

## D4 — Remove the backend items tagged V6

README *What V6 removes*.

- `SCMService`, `SCMServiceProvider`, `SCMServiceDetector` and their overrides; the 8 callers move to
  `SCMExtension` / `SCMDetector`.
- `SyncConfig`, `SyncPolicy` → `syncForward`.
- `OntrackGitHubClient.getPullRequest` → `getPR` (`GitHubConfigurator`).
- `GitService.collectIndexableGitCommitForBuild` → `SCMBuildCommitIndexService`.
- Templating: `UserTemplatingFunctionField.NAME` → `EMAIL` — external, on the migration page.

## D5 — Remove the deprecated GraphQL fields

README *What V6 removes*.

- `Account.name`, `PromotionLevel.autoVersioningTrail`, `PromotionRun.autoVersioningTrail` (the
  KDSL and `ACCAutoVersioningTrail` move to `autoVersioningTrailPaginated` first).
- `SearchResult.page`, `SearchResult.uri`, `VersionInfo.date`. Coordinate with the Postgres search
  initiative, which keeps `search(...)` as a deprecated wrapper.
- `PromotionLevel.promotionRuns` (`ProjectView`, `FavouriteBranchesWidget`, `FavouriteProjectsWidget`
  move to `promotionRunsPaginated` first), `AutoVersioningOrder.targetPaths`.
- Not the indicators fields: they go with the indicators deletion.
- Regenerate the `ontrack.graphql` dumps.

## D6 — Remove the deprecated REST endpoints and other external items

README *What V6 removes*.

- `POST /rest/structure/promotionLevels/{id}/image` and
  `POST /rest/admin/predefinedPromotionLevels/{id}/image`; `PredefinedPromotionLevelsMgt` in the KDSL
  moves to `PUT` first.
- `HookResponse.info` → `infoLink` (both copies, and the two places still setting it).
- `ElasticMetricsConfigProperties.apiCompatibilityMode`.
- The legacy branch-links setting and its form.
- Un-deprecate `Reordering` and `NotificationResultType.TIMEOUT`.

## D7 — Deprecate for V7: GitHub password, `ONTRACK_SCM_ISSUES`, kebab-case CasC aliases, `warnIfAsync`

README *What V6 deprecates for V7*.

- Each gets the `Removed in V7` marker and the runtime warning from D0:
  - GitHub configuration `password` authentication — use a token or a GitHub App;
  - `ONTRACK_SCM_ISSUES` (`EnvConstants.YONTRACK_LEGACY_SCM_ISSUES`);
  - the kebab-case aliases in `GitHubEngineConfiguration`, `SubscriptionsCascContextData` and
    `WebhooksCascConfigContext`;
  - `…queue.general.warnIfAsync` renamed `warnIfSync`, the old name kept as a deprecated alias.
- Listed under *Newly deprecated* on the migration page.

## D8 — Remove pure-Git support

README *What V6 removes*.

- `GitProjectConfigurationPropertyType`, with a Flyway migration deleting its stored properties.
- `GitSCMExtension`, `GitNoRemoteCounter`, the JGit dependency.
- `GitHubSCMExtension.getBranchesForCommit` moves from the local clone to the GitHub API.
- *Breaking changes* on the migration page: pure-Git projects must move to GitHub, GitLab or
  Bitbucket.

## D9 — `EntityDataService` → `EntityStore`

README *What V6 removes*.

- The 8 callers move to `EntityStore`; a Flyway migration moves the stored data; `EntityDataService`
  and its implementation are deleted.
- If it turns out too large: re-tag it `Removed in V7`, list it as carried over on the migration
  page, and stop.

## D10 — Remove the auto-versioning legacy attribute names (#1515)

README *What V6 removes*.

- Drop the 8 `@JsonAlias` names in `AutoVersioningSourceConfig` (`project`, `branch`, `promotion`,
  `path`, `property`, `propertyType`, `regex`, `propertyRegex`) from the config and CasC.
- Remove the "Legacy parameter names" note in `integrations/auto-versioning/auto-versioning.md`.
- The migration page lists each old name with its new one.

## D11 — Subscription `name` becomes required

README *What V6 removes*.

- `name` non-null in `SubscribeToEventsInput`, in `SubscriptionsCascContextData`, and in the KDSL
  `NotificationsMgt`.
- `EventSubscriptionPayload.id` removed.
- No data migration: `SubscriptionNameMigration` has named every subscription since 4.9.

## D12 — Internal backend cleanup

README *What V6 removes*.

- The untagged internal deprecations: remove those without callers, migrate the callers of the
  others, then remove them (`Time.forStorage`, `AbstractJdbcRepository.dateTimeFromDB`,
  `StructureRepository`, the GitHub ingestion V1 model and `FilterHelper`, `idField`, …).
- The deprecated test fixtures.
- No migration page entry.

## D13 — Frontend deprecated helpers and `useGraphQLClient`

README *What V6 removes*.

- Remove `useReloadState`, `getUserErrors`, the deprecated fragments, `closeUri` and the old
  `services/useQuery`, migrating their callers.
- Migrate `useGraphQLClient` in batches with `/migrate-use-graphql-client`, then delete it.
- ESLint `no-restricted-imports` rules for each of them.
- `doc/dev-guide/ui/ui-graphql-call.md` stops recommending `useGraphQLClient`.
- Internal: may continue after the cutover if unfinished.

## D14 — Delete `ontrack-docs/src/docs`

README *What V6 removes*.

- Delete the tree and the `CLAUDE.md` rule about it.
- Not mentioned on the migration page.
