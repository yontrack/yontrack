# Delivery scorecard — Yontrack 6.0 and 6.1

Outcome of the grilling session of 2026-09-28 on the scorecard half of
[2026-09-scorecard.md](../2026-09-scorecard.md). That session settled the shape (readings,
scorecard, estates, the removals); this one settles what an agent needs to build it: the
definitions down to the sample, the table, the job, the estates, the slot failure signal and
backdated deployments, the UI, the export, the demo and the 6.1 security readings.

The scorecard document still stands, except where this document says otherwise. Where the two
disagree, this one wins. Findings are out of scope here: they were specified by
[2026-09-findings/README.md](../2026-09-findings/README.md) and have shipped (#1854–#1868).

The issue breakdown is [issues.md](issues.md), under a new `initiative: scorecard` label.

## Where we start from

- **Findings are live on `v6`**: finding, observation and stored exposure tables, the
  `security-findings` data type, the events, the UI. The 6.1 security readings have their input.
- **Both modules to remove are still there**, on `v6` as on `main`. Latest migration is `V87`.
- **Indicators footprint**: the module (143 main files); `indicator(s)` sub-packages in `scm` (1),
  `general` (1), `github` (16, the compliance checks), `jenkins` (12, pipeline file and library
  computers, their settings and CasC) and `sonarqube` (2); `.withDependency(indicatorsExtensionFeature)`
  in the Jenkins and SonarQube features and four tests building the feature. Storage: one
  `ENTITY_DATA_STORE` category (`…indicators.model.Indicator`) and six `STORAGE` stores (categories,
  types, views, portfolios, configurable state, and the Jenkins pipeline-library settings, which
  bypass `SETTINGS`). Roles `PROJECT_INDICATOR_MANAGER` and `GLOBAL_INDICATOR_MANAGER`. Metrics
  `ontrack_indicator` (export) and `ontrack_indicators_computing_ms` (Micrometer). Frontend: one
  settings form, `jenkins-pipeline-libraries-indicators-form.js`. No KDSL, no mkdocs page.
  `scm` depends on it through `api`.
- **Delivery-metrics footprint**: five chart providers on promotion levels
  (`promotion-level-lead-time`, `-frequency`, `-success-rate`, `-ttr`, and `e2e-lead-time`), served
  live through `getChart`; five core dashboard widgets (`home/Promotion{LeadTime,Frequency,
  Stability,TTR}Chart`, `home/E2ELeadTimeChart`); seven `ontrack_dm_*` exported metrics — the
  three end-to-end ones (off by default, settings `EndToEndPromotionMetricsExportSettings` in
  `SETTINGS`, CasC `e2e-promotion-metrics`) and the four time-since-event ones (a job per project
  with a branching model, every 30 minutes, on by default).
- **The promotion-level TTR chart** measures, per branch, from an unpromoted build's creation to
  the next promotion — but keeps overwriting its reference with the **most recent** unpromoted
  build. Several users rely on it.
- **Slot pipelines have no failure.** Statuses are `CANDIDATE | RUNNING | CANCELLED | DONE`.
  `CANCELLED` is both a manual cancel and "superseded by a more recent pipeline"; the reason is only
  a change message. The slot-based `successRate` and `mttr` of the scorecard document assumed a
  failed deployment the model does not have.
- **Deployments cannot be backdated**: every pipeline time is `Time.now`, no mutation takes one.
  Builds, validation and promotion runs can.
- **Test data**: `TestSummaryValidationDataType`, alias `tests` (not `test-summary`), data
  `passed`/`skipped`/`failed`. Several runs per build and stamp are allowed; the latest is current.
- **Branch model**: `BranchModelMatcherService` returns `null` for a project without SCM, and the
  default model (`main|master|develop`, `release/.*`) for a project with SCM and no property.
- **CasC fails at startup on an unknown key** ("No CasC context is defined").
- **Labels** are selectable in AND through GraphQL; the labels UI initiative has shipped.
- **Licensing never alters the GraphQL schema.** Queries, fields and mutations of a licensed
  feature stay in the schema and raise a licence error when used unlicensed — as environments do.
- The mobile UI displays deployments and has a cancel sheet. `yontrack-cli` has `slot pipeline`
  commands.

## Decisions

### Releases

- **6.0** (milestone `6.0`): both removals, the slot `FAILED` status and deployment backdating,
  the scorecard engine with the delivery and test readings, **estates as an entity** (licensed,
  GraphQL, KDSL, CasC, admin page) with their per-estate readings on the project scorecard, the
  export, the demo, the docs.
- **6.1** (milestone `6.1`): the security readings, the estate security fields, the estate view and
  its findings fan-out. Written now so that the 6.0 table and engine are checked to carry them.
- Every issue targets `v6`.

### Vocabulary

Four `CONTEXT.md` entries, before any code names them. `basis` gets none: it is a column whose
values explain themselves.

- **Reading**: one measurement of one project at one moment, taken by Yontrack from its own data,
  for one set (no estate, or one estate). _Avoid_: indicator, metric, gauge, score.
- **Scorecard**: the readings of one project, and what a project page shows. _Avoid_: dashboard.
- **Estate**: a group of projects selected by labels and read together, with the marker and
  targets they are read against. _Avoid_: portfolio, group, label.
- **Marker**: the event a delivery reading measures up to — an environment reached, or a promotion
  granted. _Avoid_: target (the estate's), release, deployment (only one kind of marker).

### Removals

- **Indicators**: the module, the five sub-packages, the Jenkins pipeline-library settings and
  their form, both metrics, the two roles, the feature dependencies and tests. A migration deletes
  the `ENTITY_DATA_STORE` category, the six `STORAGE` stores and every stored grant of the two
  roles. No replacement for the GitHub compliance checks.
- **Delivery-metrics**: the module goes once its charts run on the scorecard engine. A migration
  deletes the `SETTINGS` rows of `EndToEndPromotionMetricsExportSettings`. All seven
  `ontrack_dm_*` metrics go, with no transition release; the time-since-event family is not
  carried over (a freshness reading is a later issue against a real use case).
- **Removed CasC keys are tolerated**: `jenkins-pipeline-library-indicator` and
  `e2e-promotion-metrics` are ignored with a WARN naming the key and "removed in 6.0", for the
  whole of 6.x. The mechanism is a registry of removed keys, introduced with the indicators removal
  and reused by the delivery-metrics one.
- The 6.0 migration notes (deprecations session, D2) list the indicators, the `ontrack_dm_*`
  metrics with their mapping to `ontrack_reading`, and the TTR start change below.

### Charts

- **Kept, all five**, with their chart names, their GraphQL `getChart` path, their dashboard widgets
  and their interval/period options unchanged. They move into `ontrack-extension-scorecard`.
- **Charts and readings share one sample function** (below). A promotion-level chart calls it live
  with that level as marker and that level's branch as scope, and buckets the samples by period. A
  chart and the reading beside it can never disagree.
- **E2E lead time** stays a live chart and widget with its meaning unchanged (upstream build
  creation to a downstream promotion across build links). It is not a reading: it is not per
  project. Only its export goes.
- **The TTR chart's start changes**: the outage starts at the **first** unpromoted build after a
  promoted one, not the last. For B1 promoted → B2, B3, B4 unpromoted → B5 promoted, the sample is
  B2.creation → B5.promotion. Numbers grow for any outage longer than one broken build; the
  migration notes say so.
- **Success rate** applies the in-flight exclusion below, in the chart as in the reading.

### The engine

- **Samples → aggregate.** Each reading has a computer returning samples for a scope (a project,
  its in-scope branches, a marker, an interval). The reading aggregates them over its window; a
  chart buckets them by period. Computers are unit-tested on plain sample lists.
- **Duration readings store the median** in `value`; p90, mean, min, max and the sample count go
  in `details`. No minimum sample count: the count is always shown beside the value.
- **Sets.** Every non-disabled project always gets a **no-estate set** of readings (estate `NULL`,
  promotion marker, no targets), plus one set per estate it belongs to. The core scorecard is the
  same whether or not the project joins an estate; the no-estate set is never judged green or red.
- **Scope** is recorded on each reading: delivery readings follow the marker; test readings (and,
  in 6.1, security readings) read the branches matched by the branch model, and **every branch when
  the matcher is `null`** (no SCM), as findings already does, saying which case applied.

### The promotion marker with no estate

- Per model branch, the branch's **last promotion level** in its order. Samples are pooled across
  branches; `details` records the level name per branch. No project property: an estate is the
  override.

### The catalogue — 6.0

| Reading | Promotion marker | Environment marker |
|---|---|---|
| `delivery.leadTime` | Build creation → its first promotion run at the level. | Build creation → `end` of that build's **first** `DONE` pipeline in the slot. Redeploys don't reset it. |
| `delivery.frequency` | Promotion runs, normalised **per week**; raw count in `details`. | `DONE` pipelines, per week; raw count in `details`. |
| `delivery.successRate` | Builds promoted over builds on the scope, **excluding builds in flight**: created within the window-median lead time before the window's end. The exclusion is stated in `details`. | `DONE` / (`DONE` + `FAILED`). `CANCELLED` is left out entirely. |
| `delivery.mttr` | The TTR chart's samples: first unpromoted build after a promoted one → next promotion on that branch. `MEASURED`. | `FAILED` pipeline → next `DONE` in the same slot. Documented as time to restore the deployment path, not an incident MTTR, which Yontrack cannot see. |
| `quality.testPassRate` | Among builds with at least one run on a test stamp, the share whose **latest** run on every test stamp passed. | same |
| `quality.testFlakiness` | Among the same builds, the share where some test stamp has a `FAILED` run followed by a `PASSED` run. | same |

- **Test stamps** are the stamps whose data type is `TestSummaryValidationDataType`.
- `details.markerKind` (`PROMOTION` or `ENVIRONMENT`) says which definition applied.

### `unknownReason`

`basis = UNKNOWN` always carries one of: `NO_MARKER` (no promotion level, or no slot in the marker
environment), `NO_SAMPLES` (nothing reached the marker in the window), `NO_TEST_STAMP`,
`NO_FAILURE` (MTTR with no failure in the window), `NOT_LICENSED` (environment marker without the
environments licence). 6.1 adds `NO_TARGET`.

- `NO_FAILURE` is rendered **neutral** ("no failure in window"), not as unknown-grey. MTTR never
  reads 0.
- `ESTIMATED` is never produced in 6.x.

### Storage

- **One table, one row per set, project, reading and day**: estate (nullable, FK `ON DELETE
  CASCADE`), project (FK `ON DELETE CASCADE`), reading key, day, computed at, window start and end,
  `value`, `basis`, `unknownReason`, `details` JSONB. Uniqueness treats a `NULL` estate as a value
  (`NULLS NOT DISTINCT` or an equivalent index).
- **Daily snapshots are appended**, not overwritten: "6.x reads its own history" must not need an
  external TSDB, and the project page gets a trend for free. A recompute on the same day overwrites
  that day's row. Snapshots past the retention are purged by the daily job.

### The job

- **One daily job for the no-estate set** over every non-disabled project, **plus one job per
  estate**, through the job framework. Default cron: 02:00 server time.
- **A project whose computation throws gets no row that day.** It is logged and counted by
  `ontrack_readings_errors`; the UI shows the last snapshot with its date. An exception is not
  something Yontrack cannot see, so it is never `UNKNOWN`.
- A Micrometer timer `ontrack_readings_computation`, tagged by estate.
- **Recompute on demand**: a "Recompute" command on the project scorecard, enabled by
  `ProjectConfig`, overwrites today's snapshot for every set the project is in; an estate recompute
  from the estate admin needs `EstateManagement`. Both are queued as jobs, never run inline.

### Settings

A core global settings page, "Delivery scorecard", with CasC: default window (90 days), snapshot
retention (730 days), the job's cron. Estates override the window per reading. 6.1 adds the
no-estate freshness (7 days).

### Slot `FAILED`

- A terminal status, reachable from `RUNNING` only (a candidate that never started is cancelled).
  Sets `end`. Message optional. Same right as finishing a deployment.
- New event `slot-pipeline-failed`. Slot workflows gain a `FAILED` trigger.
- `getLastDeployedPipeline` still returns the last `DONE`: a failure does not change what the slot
  runs.
- GraphQL `failSlotPipeline`, KDSL, and the CLI (`yontrack slot pipeline fail`).
- **Desktop UI**: status icon and label, matrix and drawer, deployment page timeline, a "Mark as
  failed" command beside "Finish".
- **Mobile UI: displays `FAILED`, no action.** It renders statuses and would otherwise show
  something wrong; a failed deployment is reported by the CI that ran it, not decided on a phone.

### Backdated deployments

- An optional `dateTime` on `startSlotPipeline`, `startSlotPipelineDeployment`,
  `finishSlotPipelineDeployment`, `failSlotPipeline` and `cancelSlotPipeline`, stored on the
  pipeline and its change history. KDSL and the CLI (`--date`) expose it.
- **Constraints**: not in the future; not before the build's creation; not before the pipeline's
  previous change; a pipeline's start not before the start of the slot's latest pipeline.
  Out-of-order insertion stays the ledger's open question.
- The auto-cancel of the slot's active pipeline takes the new pipeline's start time.
- Rights are the action's own, as for validation and promotion `dateTime`.
- Events and workflows fire as usual; the import "event hazard" stays the ledger's.

### Estates

- **Fields in 6.0**: `name` (unique), `description`, `labels` (at least one, all required), `marker`
  — `ENVIRONMENT {environment, qualifier = ""}` or `PROMOTION {levelName}` — a window override per
  reading, and an optional target per reading.
- **Default marker** when unset: the highest-ordered environment where the project owns a slot,
  else the no-estate promotion rule.
- **Qualifier**: an environment marker reads the default qualifier (`""`) only, unless the estate
  names one. Pooling qualifiers would double-count frequency.
- **Targets**: one threshold per reading, direction fixed by the reading — lead time ≤, frequency
  ≥, success rate ≥, MTTR ≤, pass rate ≥, flakiness ≤. **Two states**, met or missed. No target:
  shown, not judged.
- **Deleting a label an estate uses is refused**, with a message naming the estates: dropping one
  label from an all-required list silently widens the estate. The join table's FK keeps `ON DELETE
  CASCADE` per the rule, as a backstop.
- **Security**: a global function `EstateManagement`, granted to the built-in roles holding
  `LabelManagement`. Estates are readable by every authenticated user; each project's readings are
  filtered by project view.
- **Licence**: a new licensed feature, "Delivery scorecard", in the manner of environments, read
  on every call as `FindingsLicense` does. **The schema never changes**: unlicensed, estate
  queries, fields and mutations raise the licence error; the job skips estates; stored snapshots are
  kept and come back with the licence. An environment marker without the environments licence reads
  `UNKNOWN (NOT_LICENSED)`.
- Managed through GraphQL, KDSL and CasC; an admin page under the configurations user-menu group.

### API

- `Project.scorecard`: the sets the project is in, each with its readings (value, basis,
  unknownReason, window, computedAt, details, target and met/missed) and their daily history.
- Root `estates`, `estate(name)`; mutations to create, update and delete an estate; `recompute`
  mutations for a project and an estate.

### UI

- **Project page — Scorecard section**: a compact table, readings × sets ("Project" plus one column
  per estate), value against target, `UNKNOWN` rendered distinctly with its reason on hover, the
  sample count, the "Recompute" command. Follows the Security section's pattern.
- **Project scorecard page**: per reading, a sparkline of daily snapshots, the window, the branches
  that fed it, the marker used and the `details`. It answers "why is this number what it is".
- **Estates admin page**.
- **Promotion-level pages** keep their charts, TTR included.
- **Mobile UI: no scorecard and no estates in 6.x.** Only the slot `FAILED` status reaches it,
  display only.

### Export

`ontrack_reading`, through `MetricsExportService`, at each computation: tags `estate` (`-` for the
no-estate set), `project`, `reading`, `basis`; field `value`; timestamp `computedAt`, which the
Elastic exporter requires. A re-export job (`MetricsReexportJobProvider`) replays stored snapshots.
Findings are not exported.

### Demo

- **Two estates over overlapping projects**, so one project reads differently in each: "Demo
  products" (promotion marker `GOLD`) and "Demo production" (environment marker `production`),
  selected by labels, targets set.
- About 90 days of backdated builds, promotions, `tests` runs and deployments, including a
  `FAILED` deployment followed by a `DONE`, and a project whose tests fail then pass on the same
  build. The findings demo project is in "Demo products".
- The seed ends by triggering a recompute.
- KDSL gains a "validate with data and a date" call; the seed uses the backdated pipeline
  mutations.

### Documentation

mkdocs pages, each added to `nav:` in `ontrack-docs/mkdocs.yml`:

- 6.0: "Delivery scorecard" (readings, sets, basis, the catalogue with exact definitions and
  unknown reasons); "Estates" (licensed); the promotion-level chart docs updated (TTR start); the
  environments pages for `FAILED` and backdating; the migration-notes entries.
- 6.1: the security readings and the estate view.

## 6.1

### Estate security fields

Expected scan kinds (from the findings `kind` enum), freshness N days, remediation targets in days
for CRITICAL and for HIGH. Added by a 6.1 migration.

### Readings

| Reading | Definition |
|---|---|
| `security.maturity` | Ladder. 0 none. 1 reported: a `security-findings` run on an in-scope branch in the window. 2 covered: every expected kind has a run fresher than N days — **with no estate**, some scan fresher than the global freshness (7 days). 3 gating: a `security-findings` stamp is required by a promotion level on an in-scope branch, or such a run was `FAILED` in the window. The rung is the value. |
| `security.remediationTime` | CRITICAL and HIGH findings resolved at project level in the window: median from first observation to resolution. |
| `security.overdue` | Open CRITICAL findings older than the CRITICAL target plus open HIGH older than the HIGH target. `UNKNOWN (NO_TARGET)` with no estate. |

- Both remediation readings carry the accepted count in `details`: accepted findings are neither
  open nor resolved.
- Remediation is measured on versionless locations, so a bump that leaves the CVE in place is not
  a remediation.

### Estate view

- A **"Scorecards"** user-menu item lists the estates. There is no "all projects" estate.
- **Estate page**: projects × readings, coloured by target, unknown distinct, a roll-up row
  (median, unknown count, missed count), sortable; each row links to the project scorecard page.
- **Fan-out tab**: one finding → the projects exposed, on which branches, since when, through the
  findings cross-project query.
- **The measured-only toggle** appears only once an estate holds an `ESTIMATED` reading, which 6.x
  never produces.
- Licensed ("Delivery scorecard"). No mobile.

## What this hands to the ledger

- Backdated deployments exist, within monotonic constraints; out-of-order insertion and the event
  hazard remain the ledger's.
- Readings are derived and snapshotted daily, so an imported history produces its scorecard on the
  next computation — but only for the days computed after the import. Backfilling snapshots for
  imported past days is the ledger's question.
- `basis = ESTIMATED` and `unknownReason` are still the slots its confidence and provenance land in.

## Sources

- [2026-09-scorecard.md](../2026-09-scorecard.md), which this session started from.
- [2026-09-findings/README.md](../2026-09-findings/README.md) for the findings model and its
  licence pattern.
- [2026-09-deprecations/README.md](../2026-09-deprecations/README.md) for the migration notes.
- `ontrack-extension-indicators`, the `indicator(s)` sub-packages, `JenkinsPipelineLibraryIndicatorSettings*`.
- `ontrack-extension-delivery-metrics`: `PromotionLevel*ChartProvider`, `E2ELeadTimeChartProvider`,
  `EndToEndPromotionsJdbcHelper`, `TimeSinceEventServiceImpl`, `EndToEndPromotionMetricsExportSettings`.
- `ontrack-extension-chart` (`ChartProvider`, `GetChartOptions`), core dashboard widgets under
  `ontrack-model/.../dashboards/widgets/`.
- `ontrack-extension-environments`: `SlotPipelineStatus`, `SlotServiceImpl`, `SlotPipelineMutations`,
  `EnvironmentsEvents`, `EnvironmentsLicense`; `FindingsLicense`.
- `TestSummaryValidationDataType`, `BranchModelMatcherServiceImpl`, `GitBranchModelMatcherProvider`,
  `LabelManagementServiceImpl`, `ConfigContext` (CasC), `MetricsExportService` and its InfluxDB and
  Elastic exporters, `ontrack-demo-seed` (`KdslDemoTarget`).
