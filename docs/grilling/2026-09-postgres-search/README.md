# Search on Postgres — Yontrack 6.0

Outcome of the grilling session of 2026-09-23 on replacing Elasticsearch by native Postgres search,
and on modernising the search UI.

**Goal:** Yontrack no longer needs Elasticsearch to run. Elasticsearch stays only as an optional
*target* for the metrics export (`ontrack-extension-elastic`), never for search.

The issue breakdown is in [issues.md](issues.md), under a new `initiative: postgres-search` label.
The icon of the performance stamp is [SEARCH.PERFORMANCE.png](SEARCH.PERFORMANCE.png) (source:
[SEARCH.PERFORMANCE.svg](SEARCH.PERFORMANCE.svg)).

## Where we start from

- **Elasticsearch serves exactly two things**: the global search box and the optional metrics
  export. Nothing else reads or writes the search indexes — no audit, notification,
  auto-versioning or change-log code. The only callers of `SearchService` are `SearchController`,
  `GQLRootQuerySearch` and `GQLRootQuerySearchResultTypes`.
- **Ten indexers** behind one extension point, `SearchIndexer` (`ontrack-model/.../structure/SearchIndexer.kt`):

  | Indexer                   | Module              | Result type    | Indexed on                              |
  |---------------------------|---------------------|----------------|-----------------------------------------|
  | `ProjectSearchProvider`   | `ontrack-ui`        | `project`      | project events (search itself hits the DB via `SearchQuery`) |
  | `BranchSearchProvider`    | `ontrack-ui`        | `branch`       | branch events                           |
  | `BuildSearchProvider`     | `ontrack-ui`        | `build`        | build events, display name change        |
  | `ReleaseSearchExtension`  | `extension-general` | `build-release`| `ReleasePropertyType` hooks             |
  | `BuildLinkSearchExtension`| `extension-general` | `build-link`   | `BuildLinkListener` hooks, daily job     |
  | `GitBranchSearchIndexer`  | `extension-git`     | `git-branch`   | `GitBranchConfigurationPropertyType` hooks |
  | `ScmCommitSearchExtension`| `extension-scm`     | `scm-commit`   | hourly **full** scan of every commit     |
  | `ScmIssueSearchExtension` | `extension-scm`     | `scm-issue`    | fed by the commit scan                   |
  | `SCMCatalogSearchIndexer` | `extension-scm`     | `scm-catalog`  | weekly job, disabled by default          |
  | findings (#1862)          | `extension-findings`| external id    | planned in `initiative: findings`        |

- **Elasticsearch types leak into the public model**: `elasticsearch-java` is an `api` dependency
  of `ontrack-model`; `SearchIndexer.initIndex` / `buildQuery` take ES builders.
- **Yontrack cannot start without Elasticsearch**: `ElasticSearchStartupService` creates the
  indexes at startup with no fallback, and `StartupStrategy` does not catch it.
- **Access is checked after the query**, per hit, in `toSearchResult`, which also reloads each
  entity from the database. Totals and pages are therefore wrong for a restricted user, and a
  results page costs one DB read per hit.
- **Event listeners are synchronous and in-transaction**: `EventPostServiceImpl` is `@Transactional`
  and calls every `EventListener` directly (`EventListenerServiceImpl.onEvent`).
- **Postgres 17** everywhere (compose, dev, IT, KDSL). No minimum documented, no extension in use,
  all existing name matching is `ILIKE`.
- **Metrics export** (`DefaultElasticMetricsClient`) is off by default
  (`ontrack.extension.elastic.metrics.enabled=false`) and reuses search's `Rest5Client` when
  `target: MAIN`, or builds its own with `target: CUSTOM`. There is no Micrometer Elastic registry.
- **The UI**: the navbar box (`SearchBox`) searches from 3 characters with a 500 ms debounce, and
  fires **one GraphQL request per result type** into a dropdown. Enter does not navigate; nothing
  links to `/search`, whose query sends `type: ''` to a `String!` argument and is almost certainly
  broken. No shortcut, no keyboard navigation, no recently visited. `SearchView`, `SelectProject`
  and `SelectBranch` still use `useGraphQLClient`; `SearchTypeSection` and `JumpToProject` are
  dead.
- **Mobile has no global search**, by design since 5.4 (#1723): `/search` answers "desktop only".
- **6.0 is built on `v6`** (`doc/dev-guide/major-branch.md`), deployed to v6.dev.yontrack.com,
  never released on its own.

## Scope and order

- **6.0 only, on `v6`.** Removing a piece of infrastructure is a major-version change. No release
  ever runs both backends.
- **The initiative starts once `initiative: findings` is complete.** #1862 (search findings by
  external id) is built on the Elasticsearch `SearchIndexer` as part of that initiative, and is
  migrated here like the nine others. The first issue of this initiative is **blocked by #1862**.
- **Mobile search is out of scope**, with no follow-up for now. The palette is desktop-only;
  `/search` stays desktop-only in `mobileRoutes`.
- **The Helm chart** (`yontrack/yontrack-chart`) gets its own follow-up for a v6 chart.

## Postgres features

- Built-in full-text search (`tsvector`, `tsquery`, GIN) with the **`simple`** text search
  configuration: no stemming, no stop words, language-neutral. Most queries are identifiers
  (`1.2.3`, `release/4.1`, `ABC-123`, commit hashes), which stemming damages.
- The **`pg_trgm`** contrib extension for substring and typo tolerance. It is a *trusted*
  extension since Postgres 13: a database owner can create it on RDS, Cloud SQL and Azure without
  superuser.
- **Nothing else**: no `unaccent`, no ParadeDB `pg_search`, no `pgvector` — each would bring back a
  deployment prerequisite, which is what this work removes.

## Storage — one `SEARCH_DOCUMENTS` table

Every indexer writes into a single table, rather than the search querying source tables.

- One ranked query across every type, which the palette needs.
- One indexer contract, and one ranking scale shared by every type.
- Properties (release, display name, git branch) are JSON and commits are not in the database at
  all, so querying source tables would not have covered them anyway.

Columns (indicative — the issue settles the DDL):

| Column        | Purpose                                                                        |
|---------------|--------------------------------------------------------------------------------|
| `ID`          | `SERIAL PRIMARY KEY NOT NULL`                                                  |
| `TYPE`        | result type id                                                                 |
| `KEY`         | unique within the type — `UNIQUE (TYPE, KEY)`                                  |
| `PROJECT_ID`  | nullable, `REFERENCES PROJECTS (ID) ON DELETE CASCADE`                         |
| `ENTITY_TYPE`, `ENTITY_ID` | optional `ProjectEntityID`                                        |
| `TITLE`       | shown, strongest matching field                                                |
| `IDENTIFIERS` | text of the identifiers, for exact / prefix / trigram matching                 |
| `TEXT`        | free text, lowest weight                                                       |
| `TSV`         | `tsvector`, weighted: title and identifiers `A`, text `C`                      |
| `DATA`        | `JSONB`, everything the frontend needs to render and link the result           |
| `UPDATED_AT`  | recency, for ranking and reconciliation                                        |

Indexes: GIN on `TSV`; GIN `gin_trgm_ops` on `IDENTIFIERS` and `TITLE`; B-tree `text_pattern_ops`
on `lower(TITLE)` for prefixes; B-tree on `(TYPE, PROJECT_ID)`.

## Indexer contract

`SearchIndexer` is replaced by a contract with **no Elasticsearch type**, and `elasticsearch-java`
leaves `ontrack-model`. An indexer only **describes documents** — it no longer builds queries, nor
reloads entities:

```kotlin
data class SearchDocument(
    val type: String,               // result type id
    val key: String,                // unique within the type
    val projectId: Int?,            // access and ON DELETE CASCADE; null = not project-scoped
    val entity: ProjectEntityID?,
    val title: String,
    val identifiers: List<String>,  // exact / prefix / trigram: names, display names, hashes, keys
    val text: String?,              // free text: descriptions, commit messages
    val data: JsonNode,             // what the frontend needs to render and link
)
```

- The SQL is written **once**, in the search service. No indexer carries SQL.
- Results are **rendered from `DATA`**, with no DB read per hit.
- A type without a project (the SCM catalog) declares the global function that grants seeing it.
- The dev-guide page `doc/dev-guide/search-indexer.md` documents the contract for extension
  authors.

## Indexation

### In the entity's transaction

- A document is written **in the same transaction** as the entity change, through the existing
  synchronous listeners and property hooks. No lag, no refresh setting, no drift.
- The write runs inside a **savepoint**. In Postgres a failed statement aborts the whole
  transaction, so a search bug would otherwise block creating a build. On failure: roll back to
  the savepoint, log, increment `ontrack_search_index_errors{type}`, and leave the repair to
  reconciliation. **Search must never block the delivery pipeline.**
- Writes are upserts on `(TYPE, KEY)`; deletions delete by key.

### Reconciliation

- The per-indexer jobs stay, as **upsert plus deletion of stale documents** (`UPDATED_AT` older
  than the run), instead of drop-and-recreate.
- Manual only for types indexed in-transaction; scheduled for the external ones (commits, issues,
  SCM catalog).
- Job category renamed from `elasticsearch` to `search`. Metrics renamed from
  `ontrack_elasticsearch_*` to `ontrack_search_*`.
- An import that bypasses events (see [2026-09-ledger.md](../2026-09-ledger.md)) still needs a
  reconciliation run, as it needs a reindex today.

### SCM commits — the largest index

- Identifiers: `id` and `shortId`. Text: the message, **truncated to 2 KB**.
- `INSERT … ON CONFLICT DO NOTHING`, replacing today's one `GET` per commit plus bulk.
- The hourly job becomes **incremental**: per project, remember the last indexed commit and scan
  only what is newer. A **full rescan weekly**. A provider whose SCM API cannot answer "since X"
  keeps the full scan.
- Issues are still extracted from the messages in the same pass.

### Upgrade to 6.0

- A Flyway migration creates the table and runs `CREATE EXTENSION IF NOT EXISTS pg_trgm`.
- A startup service, replacing `ElasticSearchV5Migration`, runs every indexer's full rebuild
  **asynchronously, once**, tracked by a storage key per indexer — so a later indexer, or a
  contract change, triggers only its own rebuild.
- While a type is rebuilding, search answers with what exists so far, plus the message
  *"Search index is being built"*.
- The 6.0 migration guide says: create `pg_trgm` beforehand when the database user cannot; the
  Elasticsearch cluster can be retired unless the metrics export targets it.

## Querying

### Matching

Tiers, strongest first:

1. **Exact** identifier, case-insensitive.
2. **Prefix** of an identifier or of the title.
3. **Full-text** match on `TSV` (`simple`, prefix-enabled `tsquery`).
4. **Trigram** similarity on identifiers and title — a **fallback, per type** (#1888): it runs
   for a type only when that type has fewer than **20** matches in tiers 1–3. The threshold is a
   constant, independent of the page, so a type's matches and count are the same on every page,
   in the facets and in the palette.

- **Minimum query length: 2** (was 3). At 2 characters only exact and prefix run, on the B-tree;
  trigram starts at 3.

### Ranking

- Score = tier, then full-text rank and trigram similarity within a tier.
- Ties broken by **recency** (`UPDATED_AT`, newest first) — which matters for builds — then by the
  type's `order`.
- **No type precedence** in the score: an exact build name beats a vague project match. Grouping
  by type is a presentation concern of the palette only.
- **Only N candidates per type are ranked** (#1888), N being the count cap below: chosen by tier,
  then recency. Relevance orders only those N, so an older, more relevant document past N is
  not shown.

### Counts

**Capped at N per type** (#1888), in the palette and on `/search`: N is
`ontrack.config.search.count-cap`, 1000 by default. `SearchFacet.capped` says a type has more;
`SearchResults.total` is the sum of the capped counts, and `SearchResults.capped` is true when any
facet is. The UI shows "Commits (1000+)", "All (3000+)", "3000+ results"; pagination runs over
the known total, so a single type ends at page 50.

### Access

Filtered **in SQL**, so totals, facets and pages count only what the user can see — up to the
cap of the counts (#1888, *Counts* above), past which a count is "1000+" rather than exact:

- A user with global project view sees everything.
- Anyone else sees `PROJECT_ID IN (:visibleProjects)`.
- `PROJECT_ID IS NULL` documents require the global function their type declares.

### Highlighting

- `ts_headline` is expensive: it runs **only on the rows of the current page** of the results
  page, and only on `TEXT` (commit messages, descriptions).
- Titles are highlighted client-side from the query tokens.

## API

- New root query:

  ```graphql
  search(query: String!, types: [String!], offset: Int = 0, size: Int = 20, perType: Int): SearchResults!
  # SearchResults { total: Int!, facets: [SearchFacet!]! (type, count), items: [SearchResult!]! }
  ```

  `perType` returns the top *N* of each type in **one request** — the palette's call.
- `search(token, type, offset, size)` stays in 6.0 as a **deprecated wrapper** over the new query,
  for the KDSL client, `ACCDSLSearch`, the `yontrack` CLI and the MCP `search` tool. Removed in 7.0.
- REST admin endpoints kept, backed by the new service: `POST /rest/search/index/type/{type}` and
  `POST /rest/search/index/reset`. The Playwright suite now needs them for commits only. No
  GraphQL mutation.
- Settings `ontrack.config.search.index.*`: drop `immediate` and `ignoreExisting` (meaningless
  now); keep `batch`, `logging`, `tracing`, `reset`.

## Elasticsearch after the change

- Only `ontrack-extension-elastic` uses it, for the metrics export.
- The Elasticsearch client, and Spring Boot's Elasticsearch auto-configuration and health
  indicator, are active **only when `ontrack.extension.elastic.metrics.enabled=true`**.
- `target: MAIN` keeps working, reading `spring.elasticsearch.uris`, for backward compatibility.
- The dev stack (`scripts/dev-stack.sh`, `compose/docker-compose-dev.yml`) and the KDSL
  acceptance stacks drop Elasticsearch and Kibana.
- The **IT stack keeps Elasticsearch**, for `ontrack-extension-elastic`'s ITs only. Moving those to
  Testcontainers is a separate matter.

## UI

### Command palette

- **⌘K / Ctrl+K** opens it from anywhere, focused inputs included; **`/`** opens it when no input
  is focused.
- Inside: ↑↓ to move, Enter to open, ⌘/Ctrl+Enter to open in a new tab, Esc to close.
- Typing: one `search(query, perType: 3)` request, debounced, results grouped by type with their
  facet counts. Per-type rendering keeps the `framework/search/{type}/Result` and `Icon`
  components.
- Before typing: the **last 10 visited entities**, stored per browser through new get/set
  functions in `@components/storage/local`.
- It also lists **user-menu items** (Settings, Notifications, Auto-versioning audit…), filtered
  client-side — the menu already reflects the user's rights. Page commands are a later concern.
- Enter on the "see all results" entry opens `/search?q=`.
- The navbar box and its per-type dropdown are replaced by a **"Search… ⌘K" button** that opens the
  palette.

### Results page `/search`

- Fixed and rebuilt: `q` and `type` in the URL, facet counts as type filters, pagination,
  highlighting.
- Uses `useQuery` from `@components/services/GraphQL`.
- Dead code goes: `SearchTypeSection`, `JumpToProject`, `SearchContext.active`, the unused
  `uri`/`page` fields of `GQLTypeSearchResult`.

### Mobile impact

None: the palette is mounted by the desktop layout only, mobile has had no global search since
#1723, and `/search` stays desktop-only in `mobileRoutes`.

### Demo

No `DemoContent` change: the seeded projects, branches, builds and releases already give the
palette something to find.

## Performance

### Budget

- Palette: **< 150 ms p95**. Results page: **< 500 ms p95**. The same for a user seeing a tenth
  of the projects (#1888).
- A search sets its own `work_mem` (`SET LOCAL`, `ontrack.config.search.work-mem`, 64 MB by
  default): on the Postgres default of 4 MB, the bitmap of a frequent word goes lossy (#1888).
- Sized for the largest known instance with 3× headroom.
- Indexing must never slow down creating builds, promotions or validations.

### `searchPerfTest`

- A Gradle task **separate from `integrationTest`**, on the IT stack's Postgres.
- Bulk-loads directly into Postgres: 100k builds, 5k branches, 1M commit documents, plus build
  links, releases and issues in proportion.
- **`EXPLAIN` assertions**: each query shape (palette, results page, exact build, commit hash)
  uses its index — deterministic, the realistic regression.
- **Latency**: p95 per scenario measured and reported against the budget, which is verified
  locally. The run fails only past a **runner ceiling** per scenario, set from a dispatch run of
  the nightly on a GitHub runner × 1.5 (#1888), so a noisy runner does not turn CI red.
- Also measures the full rebuild time of the dataset.

### `SEARCH.PERFORMANCE` stamp

- Declared in `.yontrack/ci.yaml` with the `metrics` data type. Recording only, in no promotion,
  like `COVERAGE.*`.
- Metrics, in milliseconds unless stated: `palette_p95`, `results_p95`, `commit_lookup_p95`,
  `exact_build_p95`, `rebuild_seconds`.
- Posted by a new nightly workflow `search-perf.yml` (and on `workflow_dispatch`), through
  `yontrack validate … metrics` like `scripts/coverage-validate.sh`, onto the Yontrack build of the
  checked-out commit.
- Scheduled workflows only run from `main`, so it **checks out `v6`** until 6.0 is merged into
  `main`, then switches to `main`.
- A failed `EXPLAIN` assertion posts `FAILED`, with the offending query in the description.
- Icon: [SEARCH.PERFORMANCE.png](SEARCH.PERFORMANCE.png) — the `COVERAGE.*` rounded square and
  ring gauge, in indigo `#3949AB`, with a magnifying glass in place of the label. It is copied to
  `.yontrack/images/validations/` and uploaded **once, by hand**, onto the predefined stamp, per
  that folder's README.

## Documentation

Each issue documents what it changes. Overall:

- **ADR** (next free number in `docs/adr/`): *Postgres replaces Elasticsearch for search* — one
  table, in-transaction write, savepoint, no type precedence.
- **mkdocs**: a *Searching* page (palette, shortcuts, matching rules) and an *Operations → Search
  index* page (rebuilds, `pg_trgm`, metrics), both in the `nav:` of `ontrack-docs/mkdocs.yml`.
- **Removals**: Elasticsearch out of `start/getting-started.md` and `operations/management-port.md`;
  a section in `appendix/migration-to-v6.md`.
- **Dev guide**: `doc/dev-guide/search-indexer.md`.
- **`CONTEXT.md`**: *search document*, *search result type*.
- **`CLAUDE.md`, `DEVELOPMENT.md`, `README.md`**: Elasticsearch out of the dev and KDSL stacks.

## Rejected

- **Querying the source tables directly** — no cross-type ranking, and properties and commits are
  not queryable that way.
- **An `english` text search configuration** — stemming damages identifiers.
- **Non-contrib extensions** (ParadeDB, `pgvector`) — a new deployment prerequisite.
- **Dual backends in a release**, or the switch in a 5.x minor.
- **One big backend issue** — the migration is incremental on `v6` instead, with a temporary
  per-type router between the two backends, deleted by the last backend issue.
- **Recently visited in server-side preferences** — a DB write on every page view.
- **Mobile search** — out of scope, no follow-up for now.
