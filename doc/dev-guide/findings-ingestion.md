# Findings ingestion performance

*Introduced by [#1866](https://github.com/yontrack/yontrack/issues/1866), F14 of `initiative: findings`.
The design is the *Performance* section of
[`docs/grilling/2026-09-findings/README.md`](https://github.com/yontrack/yontrack/blob/main/docs/grilling/2026-09-findings/README.md#performance).*

A report of a security scan comes in through one door, the `validateBuildWithFindings` mutation,
and that door is **synchronous**: the report is read, and the validation run, the findings, their
observations and their exposure on the branch are written in one transaction, before the mutation
answers (`FindingsIngestionServiceImpl`). It keeps the contract simple — the CLI gets the run, or
an error naming the offending field — at the cost of holding the HTTP request for as long as the
ingestion takes. That cost is watched, not guessed.

## What is measured

Declared in `FindingsMetrics`, listed in the generated metrics reference of the user docs:

| Meter | Type | Tags | What |
|---|---|---|---|
| `ontrack_findings_ingestion` | timer | `format` | duration of an ingested report, from its reading to the writing of its exposure |
| `ontrack_findings_ingestion_findings` | distribution summary | `format` | findings in an ingested report, after the entries for the same finding are merged |

- Only the reports which are **ingested** are measured. A report rejected for its format, its
  licence or its content is fast and says nothing about the cost of the door; mixing it in would
  pull the percentiles down.
- `format` is `findings`, `sarif` or `trivy`, never what the client sent: an unknown format is
  rejected before anything is measured, so the tag cannot grow.
- Both meters are published as **histograms**, and the timer has a bucket at exactly 5 s, the
  threshold below. On the Prometheus endpoint the timer is `ontrack_findings_ingestion_seconds_*`.
- The timer runs inside the transaction: it leaves out the commit and GraphQL's reading of the
  payload. Both are small next to the reading of the report and the writing of the rows; if the
  two ever drift apart, the HTTP server metrics of `/graphql` tell.

The p95 by format, over a day:

```promql
histogram_quantile(
  0.95,
  sum by (le, format) (rate(ontrack_findings_ingestion_seconds_bucket[1d]))
)
```

Read it with the size of the reports beside it: a p95 which grows with
`ontrack_findings_ingestion_findings` is the volume, one which grows alone is a regression.

## The threshold: move parsing to a queue above 5 s

**When the p95 of `ontrack_findings_ingestion` exceeds 5 s on `self.dev.yontrack.com`, the
reading of the reports moves to a queue.**

`self.dev.yontrack.com` is the reference because it ingests the scans of Yontrack's own pipeline
(the `SECURITY.*` stamps, once #1869 switches them to findings), so its reports have realistic sizes
on a realistic database — a demo seeded with a handful of findings proves nothing. 5 s is the point
where a CI step waiting on the mutation starts to feel it, and where a burst of scans would hold
enough request threads and database connections to be noticed by everything else.

Moving to a queue means, in outline:

- the door checks what it can check without reading the report — the build, the stamp, the format
  and its licence — stores the report, and answers with an accepted ingestion rather than the run;
- the reading and the writing run on a queue (`ontrack-extension-queue`), in one transaction
  as today, once per report;
- the CLI (`yontrack validate … findings`) waits on the result, or stops waiting, by option.

That changes the contract the CLI and the users read — a malformed report no longer fails the
mutation — so it is an issue of its own, with its own grilling, not a tweak.

## What else was checked

- **Batch inserts.** The observations (`FindingJdbcRepository.insertObservations`) and the
  exposure (`saveExposures`, an `INSERT … ON CONFLICT … DO UPDATE`) are written with one JDBC
  batch each per report. The findings themselves are still written one by one — an `INSERT` for a
  new one, to get its ID back, an `UPDATE` for a known one. They are the first thing to batch if
  the timer points at the writing rather than the reading.
- **A large report, in the tests.** `FindingsIngestionPerformanceIT` generates a CodeQL-like SARIF
  report of 5 MB — generated, not stored as a fixture — and ingests it twice through the door: once
  with every finding new, once with every finding known. It logs the durations under
  `[findings][performance]` and asserts none: timings are not reliable in CI. Run it locally to see
  the order of magnitude before and after a change to the ingestion:

  ```bash
  ./gradlew :ontrack-extension-findings:integrationTest --tests '*FindingsIngestionPerformanceIT'
  ```

  The durations are in the test's standard output, in
  `ontrack-extension-findings/build/test-results/integrationTest/`.

## Where the time goes

On a laptop, when #1866 landed, `FindingsIngestionPerformanceIT` read its report — 5 MB, 11,663
results, 5,832 findings — in about 0.06 s, and ingested it:

| Scan | Total | Events | Search indexing | Findings written | Exposure |
|---|---|---|---|---|---|
| every finding new | ~11.7 s | ~7.2 s | ~3.2 s | ~1.1 s | ~0.2 s |
| every finding known | ~1.1 s | — | — | ~1.0 s | < 0.1 s |

Orders of magnitude only, on an idle local database, but the shape is what to remember:

- **The events dominate.** One event is posted per transition — `security_finding_new` for every finding
  new *on the branch* — each written and dispatched on its own. That is not only the first scan of
  a project: the first scan of **every new branch** reports all its findings as new there, so a
  feature branch pays it too.
- **The search indexing is next.** Only a finding new in the project is indexed, but
  `SearchIndexService.batchSearchIndex` asks Elasticsearch for each document before the bulk
  request, one round trip per finding.
- The reading of the report and the batched writes are not where the time is.

So when the threshold is crossed, look at what the time is made of before building the queue: a
p95 over 5 s driven by new branches comes from the events and the indexing, and a queue would take
that work off the request without making it any cheaper.
