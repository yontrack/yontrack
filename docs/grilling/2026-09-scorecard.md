# Delivery scorecard and security findings — Yontrack 6.x

Outcome of the grilling session of 2026-09-16 on blocks 3 and 4 of the delivery-harvesting brief
([2026-09-harvesting-brief.md](2026-09-harvesting-brief.md)): a model for vulnerability findings,
and a reading surface across projects. Both ship in **6.x**.
Blocks 1 and 2 (the portable ledger and its import) are not decided here; where a decision below
touches them, it says so.

The session settled 41 questions. This document records the decisions and the reasons, not the
questions. The issue breakdown comes in a following session and will be
`2026-09-scorecard-issues.md`, under a new `initiative: scorecard` label. The `6.0` milestone
already exists on GitHub (it holds one unrelated issue, #1734); `6.1` does not.

> **Amended 2026-09-23.** Findings were split out into their own initiative (`initiative:
> findings`) and their own module (`ontrack-extension-findings`), and specified in full by a
> second session: [2026-09-findings/README.md](2026-09-findings/README.md). Where the *Findings*
> section below and that document disagree, that document wins. It also corrected three facts
> here: there are six security stamps, not five (`SECURITY.DAST.ACTIVE`); 6.0 is built on the
> `v6` branch, not on `main`; and findings are a product feature for every project, not wiring
> for Yontrack's own CI.

## Where we start from

- **Two half-dead reporting modules.** `ontrack-extension-indicators` was born and died of not
  being used: it owns no tables (its state sits in `ENTITY_DATA_STORE` and `STORAGE` rows under
  its own categories), no frontend page, no KDSL binding, no CasC entry, no authored docs, but
  publishes 25 GraphQL object types, an enum, six root queries and two `Project` field
  contributors, plus the `ontrack_indicator` export metric. Five modules ship computers for it —
  `scm`, `general`, `github` (repository compliance checks), `jenkins` (pipeline file and pipeline
  library indicators, with their settings and a frontend form) and `sonarqube` — each confined to
  an `indicator(s)` sub-package. Its branch computer is hardcoded to a branch named `master`.
- `ontrack-extension-delivery-metrics` computes the right things — lead time, success rate,
  time to recovery, time since event — but only over promotions, and only exports them through
  `MetricsExportService` to Elasticsearch or InfluxDB. The end-to-end export is off by default
  (a settings entry), the time-since-event family is on. It owns no tables, nothing depends on it
  but `ontrack-ui`, and it feeds the five delivery chart providers users see on promotion levels.
  Its TTR measures dependency pairs: a downstream build whose upstream is not promoted counts as
  an outage, which is not what anyone means by recovery.
- **Vulnerabilities are not a domain concept.** Security scans reach Yontrack as CHML counts on
  five validation stamps (`SECURITY.IMAGE.BACKEND`, `SECURITY.IMAGE.UI`, `SECURITY.CODE`,
  `SECURITY.DAST`, and `SECURITY.SECRETS` as a threshold number), none in any promotion. The
  `yontrack validate … chml` CLI posts four integers; Trivy's UNKNOWN severity is dropped because
  CHML has no slot for it; the same CVE in two packages counts twice. `.trivyignore.yaml` and
  `security/dast/suppressions.yaml` are the only places where a finding id, a statement and an
  expiry meet, and they are files read by bash. Yontrack can say "5.3.1 had 3 HIGH" and cannot
  say which ones, nor when they went away.
- **Nothing models third-party components**: no purl, no SBOM, no package entity. Build links and
  auto-versioning relate Yontrack projects only.
- **No default branch on a project.** What exists is `BranchModelMatcherService`, a per-project
  predicate over branches backed by the Git branching-model property, already read by
  delivery-metrics and SonarQube.
- **Two licensed features today**: environments, and CI configuration injection. Everything else
  is core.
- Extension tables live centrally in `ontrack-database` (the environments tables are the template:
  `V45__1236_environments.sql` and following). Next free migration is `V82`. Migrations never ship
  in a patch.

## Decisions

### The shape of 6.x

- **6.x is the release where Yontrack reads its own history**: across projects, over time, and
  honest about where it cannot see. Not "the release where indicators were deleted".
- **The removal opens 6.x and stands alone.** Deleting `ontrack-extension-indicators` is the first
  6.x change and does not wait for its replacement. (6.0 is built on the `v6` branch — see
  `doc/dev-guide/major-branch.md` — so the removal no longer gates anything else, findings
  included.) Scope, in full: the module; the `indicator(s)` sub-packages of `scm`, `general`,
  `github`, `jenkins` and `sonarqube`, including the Jenkins pipeline-library settings and their
  frontend form; the `ontrack_indicator` metric. A migration purges the rows under the indicator
  categories in `ENTITY_DATA_STORE` and `STORAGE`. **No replacement for the GitHub compliance
  checks**: they interrogate GitHub rather than delivery history, and would be rebuilt as
  something else if anyone wants them.
- **Delivery-metrics folds into the scorecard** and its module is deleted. Its measures become the
  first family of readings, its five chart providers are kept and fed by the reading engine, its
  InfluxDB/Elastic path survives only as an output of readings. Its lead time and TTR are **not
  carried over as computed today**: both are re-defined off promotions, against a deployment
  marker (below). The regex branch filter of its export settings is dropped for the branch model.
- **6.0** = removal + finding model + the delivery family + test stability. **6.1** = the security
  readings and the estate view. Security readings need weeks of findings before they say
  anything; the model must be in 6.0 so history accumulates from the first 6.x install.

### Vocabulary

Three words, each to get a `CONTEXT.md` entry with its _Avoid_ list before implementation starts.

- **Reading**: one measurement of one project at one moment, taken by Yontrack from its own data,
  for one estate. _Avoid_: indicator (the dead word), metric (Micrometer's, and held by
  `MetricsChart` and `MetricsValidationDataType`), gauge (Micrometer's), score (a presentation).
- **Scorecard**: the readings of one project, and what a project page shows. _Avoid_: dashboard
  (Yontrack has those; the demo reset deletes them).
- **Estate**: a group of projects read together, with the expectations they are read against.
  Plural intended: an instance can hold several estates and a project can be in more than one.
  _Avoid_: portfolio (the dead module's word), group (accounts), label (the project-labelling
  feature, which is the selection mechanism, not the thing selected).

Module `ontrack-extension-scorecard`, feature name "Delivery scorecard". Findings live in their
own module, `ontrack-extension-findings`, which the scorecard depends on.

### Findings

> Superseded in detail by [2026-09-findings/README.md](2026-09-findings/README.md): the input
> formats (neutral, SARIF, Trivy JSON), the licence line, versionless purls, stored exposure,
> events per branch, the UI and the switch of Yontrack's own CI. The bullets below remain as the
> model that session started from.

- **A finding** is keyed by `(scanner, externalId, location)` and belongs to a **project**. The
  same CVE on `main` and `release/5.3` is one finding. `location` is an opaque string whose shape
  is documented per scanner: a purl for Trivy (normalised whenever the scanner gives one), empty
  for DAST, which counts one finding per rule as the DAST session decided. Counting is one per
  `(externalId, location)`, which reproduces Trivy's per-package count.
- **An observation** is one sighting of a finding by one scan of one build: the validation run,
  the time, the scanner-asserted severity, and the acceptance if any. Severity lives on the
  observation, not the finding, because CVEs get re-scored and scanners disagree.
- **Severity** is `CRITICAL | HIGH | MEDIUM | LOW | UNKNOWN`, plus the scanner's raw string kept
  beside it for provenance. CHML is what every scanner emits in practice, UNKNOWN is the one real
  gap, and the raw string keeps a scanner-neutral scale possible later without a migration of
  meaning.
- **Components and SBOM are refused for this cycle, accommodated by design.** An SBOM would be a
  per-build fact entering through the same door into a build-components table; the purl-shaped
  `location` is what would join to it. Two things then become answerable: *gone* (the component
  is no longer in the build) as distinct from *absent from the scan*, and fan-out by package
  instead of by CVE.
- **The door is a validation run.** A new validation data type, alias `security-findings`,
  carries the report: per report a `scanner` (free string: trivy, codeql, gitleaks, zap, nuclei)
  and a `kind` (fixed enum `IMAGE | CODE | SECRETS | DAST | DEPENDENCIES | OTHER`), and the
  finding list. Its config is CHML's warning/failed thresholds so `computeStatus` behaves
  identically. It **replaces** CHML on the six security stamps; `.yontrack/ci.yaml` swaps the
  type; the CLI gains `validate … findings --report <file>`; CHML stays for anyone else. One stamp
  may aggregate several scanners, as the DAST passive scan already does. This is the door block 2
  must generalise, not duplicate: the ledger imports a scan as a validation run with data.
- **The tables are the model, the run's JSON keeps only the counts.** Finding and observation
  rows go to real tables in `ontrack-database`, explicitly not `ENTITY_DATA` (one overwritten
  blob per entity and key, the SonarQube precedent) and not `ENTITY_DATA_STORE` (cross-project
  aggregation wants columns). `VALIDATION_RUN_DATA.DATA` holds the severity counts, CHML-shaped
  for charts. The posted file remains the CI artifact if audit is needed.
- **Findings survive build purge; observations do not.** Observations cascade with their
  validation run. Findings reference the project and carry denormalised `firstSeen`, `lastSeen`,
  `resolvedAt`, `maxSeverity`, so a year of remediation history outlives build retention and
  fan-out always has its row.
- **Resolution is per branch, rolled up per project.** A finding is *exposed* on a branch while
  the latest scan **of the same stamp on that branch** reports it; absence from that latest scan
  resolves it there. The comparison scope is `(stamp, branch)`, so the UI image scan can never
  resolve a backend finding. The project-level state is open while any model-matched branch is
  exposed. "Fixed on `main`, still shipping on `release/5.3`" is exactly the question an estate
  asks.
- **Resolution carries a reason, extensible.** Today the only reason is `ABSENT`. Room is left
  for `COMPONENT_REMOVED` once an SBOM can tell the difference; naming two states we cannot yet
  tell apart is the indicators mistake in miniature.
- **Acceptance is in the model and read-only in Yontrack.** The scanner-side files
  (`.trivyignore.yaml`, `security/dast/suppressions.yaml`) stay the source of truth; the scan
  report carries suppressed findings with their statement and expiry, and Yontrack records an
  acceptance observation. An expired acceptance reopens the finding automatically. No UI to
  declare one in 6.x. Accepted findings are neither open nor resolved in any reading: they are a
  third count, so remediation never flatters itself.
- **Two events**, project-scoped: `security_finding_new` (severity, id, branch) and
  `security_finding_resolved`. None per observation. "New HIGH on `main`" is the first
  notification anyone sets up.

### Readings

- **Derived, never pushed.** Readings are computed from builds, promotions, validations,
  deployments and findings already in the instance. No REST update, no computer interrogating
  another system. This is what makes the harvesting work pay off: import an organization's
  history and its scorecard exists.
- **Materialised, not live.** A daily job computes readings into a table over a trailing window
  (90 days by default, per reading). Two reasons: build retention purges the inputs, so a reading
  computed today over last year's builds is not recomputable next year; and an estate of forty
  projects must not fan out to forty live computations. Recomputation on demand per project stays
  available.
- **Keyed by estate.** The marker, expected scan kinds, freshness and targets are estate
  configuration, so the same project reads differently in two estates. The table is keyed by
  `(estate, project, reading)`; the job runs per estate; the project page shows one column per
  estate the project belongs to. Estate roll-ups (median across projects, count of unknowns) are
  computed at view time from stored project readings, not stored.
- **One table**: estate, project, reading key, computed at, window start and end, numeric value,
  `basis`, `unknownReason`, and a `details` JSONB for the counts behind the number. Ordinal
  readings store their rung as the number.
- **`basis` is `MEASURED | ESTIMATED | UNKNOWN`.** Unknown is a first-class value distinct from
  bad: a project with no test data, no slot, or no scan reads unknown, never zero. `ESTIMATED` is
  reserved for facts entering through the ledger with a confidence; 6.0 never produces it. The
  estate view can be read measured-only or measured-plus-estimated. This is the ledger's
  provenance idea arriving where it is finally visible.
- **A reading records which branches fed it.** The rule is fixed per reading, never a hardcoded
  name: lead time, frequency, success rate and MTTR are scoped by the marker; test stability and
  the security readings read the branches matched by the project's branch model, and every
  branch when no model is configured, saying which case applied.
- **The deployment marker** is declared per estate: an **environment** (needs the environments
  license) or a **promotion level name** (core). Both are `MEASURED`; the marker kind is recorded
  in the reading's details. The default is the highest-ordered environment where the project owns
  a slot. A core instance, or a project in no estate, falls back to the highest promotion level.
- **Fixed, named catalogue, no extension point in 6.x.** Any addition is a new issue against a
  real use case. Start concrete, generalise once.

| Reading | Release | Definition |
|---|---|---|
| `delivery.leadTime` | 6.0 | Build creation to the marker reached (pipeline `DONE` in the environment, or the promotion run). Commit-based start reserved as a later refinement. |
| `delivery.frequency` | 6.0 | Markers reached per window. |
| `delivery.successRate` | 6.0 | Slot: `DONE` over `DONE` + failed + cancelled. Promotion: builds promoted over builds on model branches. |
| `delivery.mttr` | 6.0 | Failed deployment in a slot to the next successful one in the same slot; same idea with the promotion marker. The pair-based algorithm is not carried over. |
| `quality.testStability` | 6.0 | Over stamps carrying `test-summary` data on model branches: share of builds whose test stamps all pass, plus pass-after-fail flips on the same build (flakiness). No such stamp reads unknown. |
| `security.maturity` | 6.1 | Ladder: 0 none, 1 reported (a scan in the window), 2 covered (every kind the estate expects, fresher than N days), 3 gating (a security stamp in a promotion, or a threshold that failed at least once). |
| `security.remediation` | 6.1 | CRITICAL and HIGH: median time from first observation to resolution over the window, and the count of open findings older than the target. Accepted reported separately. |

### Estates

- **Selection by labels.** An estate is a small named entity: name, description, one or more
  project labels (all required), the deployment marker, the expected scan kinds and freshness,
  and its targets. An explicit project list becomes a label anyway the first time someone
  maintains it.
- **Targets on the estate only.** A project in two estates may be green in one and red in the
  other, which is honest: the expectation belongs to whoever is reading. Remediation takes two
  numbers, days for CRITICAL and days for HIGH.
- Managed through GraphQL, KDSL and CasC; administered under global settings; readable by every
  authenticated user, with each project's readings filtered by the viewer's project-view right.
- **Licensing.** Findings, the project scorecard and the delivery charts are core. Estates and
  the estate view are a new licensed feature, "Delivery scorecard", gated the way environments is.
  Native scanner formats for findings (SARIF, Trivy JSON) are a separate licensed feature, see
  [2026-09-findings/README.md](2026-09-findings/README.md).

### UI

- **Project page**: a scorecard section with the readings per estate, and a **Security** section
  with open findings by severity and their branch exposure. The section is named for what it will
  hold, not for CVEs: SBOM results will sit beside findings one day.
- **Validation run detail**: the findings of that scan.
- **A "Scorecards" user-menu item** lists estates and opens the estate view: projects × readings,
  unknown rendered distinctly, measured-only toggle, and a fan-out table (one CVE, the projects
  exposed, on which branches, since when). There is deliberately no "all projects" estate: an
  estate must be declared to be read, which forces its targets to exist.
- **Promotion level pages** keep their delivery charts, computed live by the same engine with that
  level as marker, not stored.
- **Mobile UI**: no scorecard and no findings in this cycle. Stated, not silent.

### Export

Readings go through `MetricsExportService` as one metric, `ontrack_reading`, tagged by estate,
project and reading, replacing both `ontrack_indicator` and the `ontrack_dm_*` family. Findings are
not exported. This is the only surviving role of the InfluxDB and Elasticsearch exporters for this
data.

## What this hands to the ledger (blocks 1 and 2)

- Scans enter as validation runs with `security-findings` data: the ledger needs no second door.
- Readings are derived, so an imported history produces its scorecard with nothing else to feed.
- The `basis = ESTIMATED` value and the `unknownReason` column are the slots the ledger's
  confidence and provenance land in.
- The open questions that remain the ledger's own — import mode and the event hazard, ledger
  semantics, ordering, dedupe of promotion and validation runs, promotion checks on import,
  backdatable deployments — are unchanged by this session.

## Sources

- [2026-09-harvesting-brief.md](2026-09-harvesting-brief.md), the session handover this session started from.
- `docs/grilling/2026-09-dast.md` for the DAST severities, suppressions and per-rule counting.
- `scripts/security-image-scan.sh`, `.github/workflows/ci.yml` (`security-images` job) and
  `.yontrack/ci.yaml` for how counts reach Yontrack today.
- `ontrack-extension-delivery-metrics` (`PromotionLevelTTRMetrics`, `EndToEndPromotionMetrics`,
  `TimeSinceEventServiceImpl`) and `ontrack-extension-indicators`
  (`AbstractBranchIndicatorComputer`, `IndicatorStoreImpl`).
