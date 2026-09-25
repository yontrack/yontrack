# Search on Postgres — issue breakdown

Breakdown of [README.md](README.md) into agent-sized issues. Created on 2026-09-25.

Every issue carries `initiative: postgres-search`, `type: enhancement`, `status:todo` and
`ready-for-agent`, milestone `6.0`, and links back to the README section it implements.
Dependencies are recorded as native GitHub dependencies as well as prose. Base branch is `v6`.

**The initiative starts once `initiative: findings` is complete.** P1 is blocked by #1862.

| #   | GitHub | Issue                                                               | Repo / base                 | Depends on   |
|-----|--------|---------------------------------------------------------------------|-----------------------------|--------------|
| P1  | [#1876](https://github.com/yontrack/yontrack/issues/1876) | `CONTEXT.md`: search document, search result type                   | `yontrack` / `v6`           | #1862        |
| P2  | [#1877](https://github.com/yontrack/yontrack/issues/1877) | `SEARCH_DOCUMENTS`, the contract, the Postgres service, the new query; projects | `yontrack` / `v6` | P1       |
| P3  | [#1878](https://github.com/yontrack/yontrack/issues/1878) | Branches and builds on Postgres                                     | `yontrack` / `v6`           | P2           |
| P4  | [#1879](https://github.com/yontrack/yontrack/issues/1879) | Releases, build links and git branches on Postgres                  | `yontrack` / `v6`           | P2           |
| P5  | [#1880](https://github.com/yontrack/yontrack/issues/1880) | SCM commits, issues and catalog on Postgres, incremental commit scan | `yontrack` / `v6`          | P2           |
| P6  | [#1881](https://github.com/yontrack/yontrack/issues/1881) | Findings by external id on Postgres                                 | `yontrack` / `v6`           | P2           |
| P7  | [#1882](https://github.com/yontrack/yontrack/issues/1882) | Remove Elasticsearch search                                         | `yontrack` / `v6`           | P3, P4, P5, P6 |
| P8  | [#1883](https://github.com/yontrack/yontrack/issues/1883) | Elasticsearch out of the dev and KDSL acceptance stacks             | `yontrack` / `v6`           | P7           |
| P9  | [#1884](https://github.com/yontrack/yontrack/issues/1884) | UI: the ⌘K command palette                                          | `yontrack` / `v6`           | P3           |
| P10 | [#1885](https://github.com/yontrack/yontrack/issues/1885) | UI: the `/search` results page                                      | `yontrack` / `v6`           | P3           |
| P11 | [#1886](https://github.com/yontrack/yontrack/issues/1886) | `searchPerfTest`                                                    | `yontrack` / `v6`           | P4, P5       |
| P12 | [#1887](https://github.com/yontrack/yontrack/issues/1887) | Nightly `SEARCH.PERFORMANCE` stamp                                  | `yontrack` / `main` + `v6`  | P11          |
| P13 | [yontrack-chart#116](https://github.com/yontrack/yontrack-chart/issues/116) | Chart v6: Elasticsearch no longer required                          | `yontrack-chart` / its default | P7        |

While P2–P6 are in flight, `v6` is always fully searchable: a temporary per-type router sends a
type to Postgres once its indexer is migrated, to Elasticsearch otherwise. P7 deletes it.

---

## P1 ([#1876](https://github.com/yontrack/yontrack/issues/1876)) — `CONTEXT.md`: search document, search result type

README *Indexer contract*.

- Two entries, each with its _Avoid_ list: **search document** (what an indexer writes; _avoid_
  "index entry", "ES document") and **search result type** (the kind of result, owning the
  rendering; _avoid_ "search provider", "index").
- Done when `CONTEXT.md` carries both.
- Blocked by #1862; starts after `initiative: findings` is complete.

## P2 ([#1877](https://github.com/yontrack/yontrack/issues/1877)) — `SEARCH_DOCUMENTS`, the contract, the Postgres service, the new query; projects

README *Postgres features*, *Storage*, *Indexer contract*, *Indexation*, *Querying*, *API*,
*Upgrade to 6.0*.

- Flyway migration: `CREATE EXTENSION IF NOT EXISTS pg_trgm`, the `SEARCH_DOCUMENTS` table and its
  indexes, `PROJECT_ID` `ON DELETE CASCADE`.
- `SearchDocument` and the new indexer interface in `ontrack-model`, with no Elasticsearch type.
  The old `SearchIndexer` stays for now, for the types not yet migrated.
- Postgres search service:
  - write: upsert on `(TYPE, KEY)` and delete, **inside a savepoint**, failures logged and counted
    in `ontrack_search_index_errors{type}`;
  - query: the four matching tiers, minimum length 2, ranking with recency tie-break, access in
    SQL (global view / visible projects / global function for project-less types), facets,
    `perType`;
  - reconciliation: full rebuild as upsert plus deletion of stale documents;
  - startup rebuild, asynchronous, once per indexer (storage key per indexer), with the
    *"Search index is being built"* message while a type rebuilds.
- Temporary per-type router in `SearchService`: Postgres for migrated types, Elasticsearch for the
  rest.
- New GraphQL query `search(query, types, offset, size, perType)` returning total, facets, items.
  The old `search(token, type, …)` becomes a deprecated wrapper over it.
- Projects migrated: `ProjectSearchProvider` writes documents in-transaction on project events;
  its `SearchQuery` DB shortcut goes.
- `doc/dev-guide/search-indexer.md` for extension authors.
- Tests:
  - unit: ranking and tier composition, query parsing;
  - IT: in-transaction visibility (a created project is searchable before the call returns),
    savepoint (a forced write failure leaves the project created and increments the metric),
    cascade on project deletion, access filtering with correct totals for a restricted user
    (`Roles.*`), facets, `perType`, the deprecated wrapper;
  - KDSL: binding for the new query; `ACCDSLSearch` still green through the wrapper.
- No mobile impact: no UI.

## P3 ([#1878](https://github.com/yontrack/yontrack/issues/1878)) — Branches and builds on Postgres

README *Indexer contract*, *Matching*.

- `BranchSearchProvider` and `BuildSearchProvider` migrated. Builds: name and display name as
  identifiers (exact match beats everything), description as text; re-indexed on
  `UPDATE_BUILD_DISPLAY_NAME`.
- `DATA` carries what the existing `framework/search/{branch,build}/Result` components need.
- Tests: IT on exact build name vs prefix vs fuzzy ordering, recency tie-break across builds, the
  display name path; `ACCDSLSearch` (exact match first, prefix matching) green on Postgres.
- No mobile impact: no UI.

## P4 ([#1879](https://github.com/yontrack/yontrack/issues/1879)) — Releases, build links and git branches on Postgres

README *Indexer contract*.

- `ReleaseSearchExtension` (property hooks), `BuildLinkSearchExtension` (`BuildLinkListener`
  hooks; target display name as an extra identifier rather than a second document) and
  `GitBranchSearchIndexer` (property hooks) migrated.
- The daily build-link job becomes a reconciliation job.
- Tests: IT per type, including deletion on property removal and on link removal.
- No mobile impact: no UI.

## P5 ([#1880](https://github.com/yontrack/yontrack/issues/1880)) — SCM commits, issues and catalog on Postgres, incremental commit scan

README *SCM commits — the largest index*.

- `ScmCommitSearchExtension`: `id`/`shortId` identifiers, message truncated to 2 KB,
  `INSERT … ON CONFLICT DO NOTHING`.
- Incremental hourly scan: last indexed commit remembered per project; full rescan weekly; a
  provider that cannot list "since X" keeps the full scan, and the issue says which ones do.
- `ScmIssueSearchExtension` fed from the same pass; `SCMCatalogSearchIndexer` declares its global
  function for project-less documents.
- Tests: IT for the incremental scan (second run indexes only new commits), the weekly full scan,
  truncation, commit-hash lookup; Playwright `search.spec.js` commit and issue cases green.
- No mobile impact: no UI.

## P6 ([#1881](https://github.com/yontrack/yontrack/issues/1881)) — Findings by external id on Postgres

README *Scope and order*.

- The findings indexer of #1862 moved to the new contract; external id as identifier,
  `PROJECT_ID` from the finding's project.
- Tests: the #1862 tests, green on Postgres.
- No mobile impact beyond what #1862 decided.

## P7 ([#1882](https://github.com/yontrack/yontrack/issues/1882)) — Remove Elasticsearch search

README *Elasticsearch after the change*, *API*, *Reconciliation*, *Documentation*.

- Delete `ElasticSearchIndexService`, `ElasticSearchServiceImpl`, `ElasticSearchStartupService`,
  `ElasticSearchV5Migration`, `ElasticSearchIndexationJobs`, `ElasticSearchJobs`,
  `ElasticSearchIndexMetrics`, `ElasticSearchConfiguration`, the old `SearchIndexer`,
  `SearchIndexUtils`, `SearchQuery` and the router.
- `elasticsearch-java` out of `ontrack-model` and of every module but `ontrack-extension-elastic`.
- In `ontrack-extension-elastic`: the client, Spring Boot's Elasticsearch auto-configuration and
  its health indicator only when `ontrack.extension.elastic.metrics.enabled=true`; `target: MAIN`
  still reads `spring.elasticsearch.uris`.
- REST admin endpoints backed by the new service; settings `immediate` and `ignoreExisting`
  removed; job category `search`; metrics `ontrack_search_*`.
- Docs: the ADR; mkdocs *Operations → Search index* page (in `nav:`); Elasticsearch out of
  `start/getting-started.md` and `operations/management-port.md`; the section in
  `appendix/migration-to-v6.md` (Elasticsearch optional, `pg_trgm`, the one-off rebuild, the
  retired settings). Verify with `./gradlew :ontrack-docs:buildDocs`.
- Tests: an IT context starting with **no** Elasticsearch reachable and search working;
  `ontrack-extension-elastic` ITs green with metrics enabled.
- No mobile impact: no UI.

## P8 ([#1883](https://github.com/yontrack/yontrack/issues/1883)) — Elasticsearch out of the dev and KDSL acceptance stacks

README *Elasticsearch after the change*.

- Elasticsearch and Kibana out of `compose/docker-compose.yml`, `-local`, `-dev`, `-ldap`, the three
  `-kdsl*` files, `security/dast/compose/docker-compose-dast.yml`; `scripts/dev-stack.sh` and
  `scripts/dev-stack-test.sh`; `KdslStack` and `KdslStackTest` in `buildSrc`.
- The IT stack (`compose/docker-compose-it.yml`, `ItStack`) **keeps** Elasticsearch.
- `CLAUDE.md`, `DEVELOPMENT.md`, `README.md` and ADRs 0004/0012/0013 updated where they describe
  the stacks (amend, do not rewrite, the ADRs).
- Tests: `dev-stack-test.sh`, `KdslStackTest`; a dev-stack `up` from a fresh checkout.
- No mobile impact: no UI.

## P9 ([#1884](https://github.com/yontrack/yontrack/issues/1884)) — UI: the ⌘K command palette

README *Command palette*.

- Palette component, mounted in the desktop layout: ⌘K/Ctrl+K anywhere, `/` outside inputs;
  ↑↓, Enter, ⌘/Ctrl+Enter, Esc.
- One debounced `search(query, perType: 3)` through `useQuery` from `@components/services/GraphQL`;
  groups by type with facet counts; rendering through the existing
  `framework/search/{type}/Result` and `Icon`.
- Last 10 visited entities, through new get/set functions in `@components/storage/local`, shown
  before typing.
- User-menu items filtered client-side.
- A "see all results" entry opening `/search?q=`.
- The navbar box and dropdown (`SearchBox`, `SearchBoxResults`, `SearchBoxTypeResults`) replaced
  by a "Search… ⌘K" button.
- Docs: mkdocs *Searching* page (in `nav:`).
- Tests: Jest on keyboard handling and recently visited; Playwright `search.spec.js` rewritten
  around the palette (open by shortcut, find a project, a commit, an issue, open by Enter).
- Mobile impact: none — mounted by the desktop layout only; mobile has had no global search since
  #1723.
- Demo: no `DemoContent` change, the seeded data already has what to find.

## P10 ([#1885](https://github.com/yontrack/yontrack/issues/1885)) — UI: the `/search` results page

README *Results page*, *Highlighting*.

- Rebuilt on the new query: `q` and `type` in the URL, facet counts as type filters, pagination,
  `useQuery`.
- Backend: a `highlight` field computed by `ts_headline` on the page's rows, on `TEXT` only; titles
  highlighted client-side.
- Dead code removed: `SearchTypeSection`, `JumpToProject`, `SearchContext.active`, the `uri` and
  `page` fields of `GQLTypeSearchResult`.
- Docs: the *Searching* page covers the results page.
- Tests: IT on `highlight`; Playwright on filters, pagination and a shared URL.
- Mobile impact: none — `/search` stays desktop-only in `mobileRoutes`.

## P11 ([#1886](https://github.com/yontrack/yontrack/issues/1886)) — `searchPerfTest`

README *searchPerfTest*.

- Gradle task separate from `integrationTest`, on the IT stack's Postgres (its ports from
  `.yontrack-it/instance.env`, passed by the task like `integrationTest`).
- Bulk loader: 100k builds, 5k branches, 1M commit documents, plus build links, releases and
  issues in proportion.
- `EXPLAIN` assertions for palette, results page, exact build and commit hash: each uses its
  index.
- p95 latencies per scenario plus the full rebuild time, written to a JSON report; fails only past
  a generous ceiling.
- Tests: the task itself, run locally.
- No mobile impact: no UI.

## P12 ([#1887](https://github.com/yontrack/yontrack/issues/1887)) — Nightly `SEARCH.PERFORMANCE` stamp

README *SEARCH.PERFORMANCE stamp*.

- `.yontrack/ci.yaml` (on `main`, since every branch reads it): `SEARCH.PERFORMANCE` with
  `metrics: {}`, no promotion, with a comment in the style of `COVERAGE.*`.
- `.github/workflows/search-perf.yml` on `main`: nightly and `workflow_dispatch`; checks out `v6`
  until 6.0 is merged into `main`; runs `searchPerfTest`; posts `palette_p95`, `results_p95`,
  `commit_lookup_p95`, `exact_build_p95`, `rebuild_seconds` with `yontrack validate … metrics` on
  the build of the checked-out commit; `FAILED` with the query in the description when an
  `EXPLAIN` assertion fails.
- Icon: copy [SEARCH.PERFORMANCE.png](SEARCH.PERFORMANCE.png) to
  `.yontrack/images/validations/`; the one-off manual upload is noted in the issue for Damien.
- Tests: a `workflow_dispatch` run posting onto a `v6` build.
- No mobile impact: no UI.

## P13 ([yontrack-chart#116](https://github.com/yontrack/yontrack-chart/issues/116)) — Chart v6: Elasticsearch no longer required

In `yontrack/yontrack-chart`, with that repository's labels. README *Elasticsearch after the
change*.

- Elasticsearch out of the chart's required dependencies and default values.
- An optional `elasticsearch` block, for the metrics export only, feeding
  `spring.elasticsearch.uris` / `ontrack.extension.elastic.metrics.*`.
- A note on `pg_trgm` for externally managed databases.
- Chart major version 6.
- Blocked by P7, as a native cross-repository dependency.
