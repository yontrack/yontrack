# Search performance test

*Introduced by [#1886](https://github.com/yontrack/yontrack/issues/1886), P11 of
`initiative: postgres-search`. The design is the *Performance* section of
[`docs/grilling/2026-09-postgres-search/README.md`](../../docs/grilling/2026-09-postgres-search/README.md#performance).*

Search runs on Postgres, in one table, `SEARCH_DOCUMENTS`. Whether it stays fast is a matter of
volume: the integration tests index a few documents, where an instance holds hundreds of
thousands of builds and a million commits. `searchPerfTest` loads that volume and measures the
search on it.

```bash
./gradlew searchPerfTest
```

It needs what `integrationTest` needs — Docker and JDK 25 — and nothing else: the task brings the
integration test stack up and down, on this checkout's slot, exactly as `integrationTest` does,
and passes its ports on the same way. It is **not** part of `check` or `build`: it takes minutes,
and it is run on its own, nightly by the `SEARCH.PERFORMANCE` workflow (#1887) or by hand.

## What it does

The whole run is `SearchPerf`, in `ontrack-service/src/test/java/.../service/search/perf/`, a plain
`main` started by a `JavaExec` task, not a JUnit test: it is one measured sequence.

1. **Database.** A database of its own, `ontrack_search_perf`, on the Postgres of the integration
   test stack — dropped and created again, then migrated by Flyway. The integration tests never
   see the dataset, and the dataset never sees their data.
2. **Load.** `SearchPerfDataset` bulk-loads, with `INSERT … SELECT` over `generate_series` — no
   row goes through the JVM, and every value derives from its ID, so two runs load the same data:

   | | Scale 1 |
   |---|---|
   | Projects | 500 |
   | Branches (and their Git branches) | 5,000 |
   | Builds | 100,000 |
   | Build links | 100,000 |
   | Releases | 10,000 |
   | Issues | 50,000 |
   | Commits | 1,000,000 |

   The documents have the `TITLE`, `IDENTIFIERS` and `TEXT` their indexers write; their `DATA`
   only has the shape and the size of the real one, since no query reads into it. The indexes of
   the table are dropped for the load and created again from their own definitions, the ones of
   the migrations. Then `VACUUM ANALYZE`.
3. **`EXPLAIN` assertions.** For each query shape — the palette (and the palette of a user seeing
   one project out of ten), the results page and its facets, an exact build name, a full and a
   short commit hash — each statement the search runs is `EXPLAIN`ed. It must use one of the
   indexes of its tier, and never scan the whole table. The statements are those of
   `SearchDocumentJdbcRepository.searchStatements`, the very SQL the search runs.
4. **Latencies.** After a warm-up, each query of each scenario runs 10 times through
   `SearchDocumentServiceImpl.search` — as the service runs it: in a read-only transaction, with
   its `SET LOCAL work_mem` and its cap of the counts (#1888), through
   `SearchDocumentJdbcRepository.search`:

   | Scenario | Queries | Shape |
   |---|---|---|
   | `palette` | 22 prefixes, words, typos, names | all types, 3 best per type |
   | `palette_restricted` | the same | the same, for a user seeing one project out of ten |
   | `results_restricted` | the same | the results page, for a user seeing one project out of ten |
   | `results` | the same | the second page of the builds, highlighted, plus the facets of all the types |
   | `exact_build` | 20 build names, of the four styles of the dataset | palette |
   | `commit_lookup` | 10 full and 10 short commit hashes | palette |

5. **Rebuild.** All the documents are rebuilt through `SearchDocumentServiceImpl.rebuild`: its
   batches, savepoints, upserts and deletion of stale documents are the real ones. The source is
   an approximation: the real indexers read the entities through the services, or scan the SCMs,
   where the test reads the documents back from the table. The time is the time of the writes.

## The report

`ontrack-service/build/reports/search-perf/search-perf.json`:

```json
{
  "palette_p95": 361.6,
  "results_p95": 508.4,
  "commit_lookup_p95": 49.6,
  "exact_build_p95": 73.9,
  "rebuild_seconds": 139.7,
  "explain": [
    {
      "scenario": "palette",
      "query": "payment",
      "statement": "palette rows",
      "expected_indexes": ["search_documents_ix_identifiers_trgm", "..."],
      "passed": true,
      "indexes": ["search_documents_ix_identifiers_trgm", "..."]
    }
  ],
  "details": { "...": "..." }
}
```

- The five figures at the top are the metrics of the `SEARCH.PERFORMANCE` stamp: p95 latencies in
  milliseconds, and the rebuild in seconds.
- `explain` has one entry per statement checked. A failed one also carries its `reason`, its
  `sql`, its `params` and the `plan`.
- `details` has the rest: the version of Postgres, the dataset and how long it took to load, the
  p50, p95 and max of every scenario, the ten slowest queries, the budgets, the ceilings, the
  figures over budget, the rebuild per type, and the `failures`.

The run fails:

- on a failed `EXPLAIN` assertion — deterministic, the realistic regression;
- on a p95 past its **ceiling** — one per scenario (`CEILINGS` in `SearchPerf`): palette,
  results, restricted palette, restricted results, exact build, commit. The ceilings are for the
  GitHub runner the nightly runs on, slower than the machine of the budget: each one is set from
  the p95 of a `workflow_dispatch` run of `search-perf.yml`, × 1.5 (#1888). Until that run, they
  are still 10 times the budget;
- on an error of the rebuild.

A p95 over its **budget** — palette 150 ms, results page 500 ms, exact build and commit lookup 150 ms
since they go through the palette — does not fail it. It is listed in `details.over_budget`, and
the nightly stamp records it: a noisy runner must not turn it red.

## Options

| Gradle property | Default | |
|---|---|---|
| `-PsearchPerf.scale=0.1` | `1` | a smaller dataset, for working on the test itself: seconds instead of minutes, stack aside |
| `-PsearchPerf.rounds=20` | `10` | more samples per query |
| `-PsearchPerf.reuse=true` | `false` | keeps the dataset of the previous run, when it has the same scale |

When working on it, `-x integrationTestComposeDown` keeps the stack up between two runs. The
database is then reachable on the Postgres port of `.yontrack-it/instance.env`, as `ontrack` /
`ontrack`, for an `EXPLAIN ANALYZE` of your own.

## The nightly `SEARCH.PERFORMANCE` stamp

[`search-perf.yml`](../../.github/workflows/search-perf.yml) runs `searchPerfTest` every night at
03:17 UTC, and on demand (#1887):

```bash
gh workflow run search-perf.yml --ref main            # measures v6
gh workflow run search-perf.yml --ref main -f ref=v6  # the same, explicitly
```

The workflow lives on `main`, since GitHub only schedules the default branch's workflows, but it
**measures `v6`** until 6.0 is merged into `main` — then `SEARCH_PERF_REF` switches to `main`
(one line, and an item of *The cutover* in [major-branch.md](major-branch.md)).

- **Which build.** The Yontrack build of the head of `v6`, on the instance, project and branch
  `ci.yml` registers it in: `yontrack` on self.dev, branch `v6`. When the head has no build — a
  `[skip ci]` commit, or a push whose CI has not registered it yet — the newest commit that has one,
  and that is the commit checked out and measured. Never v6.dev: it holds none of `v6`'s builds.
- **What is sent.** The five figures of the report as `metrics`, PASSED — a p95 over its budget
  included, named in the description. **FAILED** with no figure when the run failed: a failed
  `EXPLAIN` assertion (scenario, query, statement and reason in the description), a p95 past its
  ceiling, a rebuild error, or no report at all. The workflow run is red whenever the stamp is.
- **Where the rest is.** The report is kept 30 days as the run's `search-perf-report` artefact, and
  the run's summary tabulates it.

`scripts/search-perf-validate.sh` holds the build resolution and what is sent when, and
`./scripts/search-perf-validate-test.sh` tests it against a stubbed CLI. The stamp is declared in
`.yontrack/ci.yaml`, in no promotion; its icon is in `.yontrack/images/validations/`.

## What it found

The first runs, on a laptop (Apple silicon, 12 CPUs, Docker with 12 GB), Postgres 17 with the
default configuration of its image (`shared_buffers` 128 MB, `work_mem` 4 MB). The whole task took
5 min 30 s: the stack, 45 s of load (27 s of documents, 17 s of indexes), 1 min of measures and
2 min 20 s of rebuild, 2 min of which for the commits.

- Every query shape uses its indexes: all the `EXPLAIN` assertions pass.
- The exact build (p95 74 ms) and the commit lookup (p95 50 ms) are within budget.
- The palette is not: p95 360 ms for a budget of 150 ms, for a median of 40 ms. The results page
  is just over its budget, at about 510 ms.
- The slow queries are the ones made of a **frequent word**. `pay-10` matches every commit of the
  payment projects, 52,000 documents; `2025.03`, `build-4` or `flaky test` thousands. The search
  counts all its candidates for the facets and ranks all of them for the best rows of each type:
  its cost grows with the number of matches, not with the size of the page. On a `work_mem` of
  4 MB the bitmap of so many rows does not fit and turns lossy, and every row of every page it
  touches is checked again, trigram similarity included — `pay-10` takes 0.9 s as an
  administrator, and 2 s for a user seeing a tenth of the projects, whose plan is not parallel.
- The same frequent-word queries with `SET work_mem = '64MB'` ran three to four times faster
  (measured by hand with `EXPLAIN ANALYZE`).

One change came out of it: the palette used to scan its candidates twice, once for the facets
and once for the best rows of each type. Its best rows now carry the count of their type, which
are the facets, in a single scan (`SearchDocumentJdbcRepository`, #1886). On the first version of
the dataset, it took the p95 of the palette from 1.5 s to 0.9 s.

Going further is a decision on the design — capping the counts of the facets, or the candidates
which are ranked — or on the configuration of Postgres, not a missing index.

## The latency budget (#1888)

#1888 changed the design, not the indexes:

- **Counts are capped** at `ontrack.config.search.count-cap` (1000) per type, `capped` in the
  GraphQL API, "1000+" in the UI.
- **Only the capped candidates are ranked**: the first 1000 of each type, by tier then recency.
  The matches are selected narrow — ID, type, tier, recency — and relevance (`ts_rank`,
  similarity) is computed for the candidates only; the other columns are read for the rows
  returned only.
- **Trigram is a fallback** for the types with fewer than 20 matches in the other tiers. Its scan
  is gated by a one-time condition, so that it is skipped when no type needs it.
- **Search sets its own `work_mem`**, `SET LOCAL`, from `ontrack.config.search.work-mem` (64 MB).

New `EXPLAIN` assertions cover the capped count and the capped ranking (on `pay-10`), the
trigram fallback (on typos, expecting the trigram index of the title) and the restricted scope.

The first run after the change, on the same laptop:

| p95 (ms) | Before | After | Budget |
|---|---|---|---|
| Palette | 398 | 366 | 150 |
| Results page | 565 | 269 | 500 |
| Restricted palette | 131 | 72 | 150 |
| Restricted results | — | 127 | 500 |
| Exact build | 66 | 73 | 150 |
| Commit lookup | 54 | 52 | 150 |

The results page and both restricted scenarios are within budget; **the palette is not yet**.
Its slowest queries are still frequent words — `pay-10` (p50 490 ms), `fix`, `payment`, `cache`,
`timeout` — whose matches are all read from the heap before the 1000 most recent can be chosen:
the cap bounds the ranking, not the scan. Going further needs an `EXPLAIN ANALYZE` of these
queries; a lead is to read the most recent matches of each type in order, rather than all of them.

