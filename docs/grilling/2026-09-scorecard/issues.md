# Delivery scorecard — issue breakdown

Breakdown of [README.md](README.md) into agent-sized issues. Created on 2026-09-28.

Every issue carries `initiative: scorecard` (a new label), `type: enhancement`, `status:todo` and
`ready-for-agent`, and links back to the README section it implements. Dependencies are recorded
as native GitHub dependencies as well as prose. Base branch is `v6` for the 6.0 issues, except S5
(`yontrack-cli` / `main`), and `v6` for the security wave too, moved
from 6.1 to 6.0 on 2026-10-02 (`doc/dev-guide/major-branch.md`).

## 6.0 — milestone `6.0`

| #   | GitHub | Issue                                                                      | Repo / base             | Depends on     |
|-----|--------|----------------------------------------------------------------------------|-------------------------|----------------|
| S1  | [#1892](https://github.com/yontrack/yontrack/issues/1892) | `CONTEXT.md`: reading, scorecard, estate, marker                           | `yontrack` / `v6`       | —              |
| S2  | [#1893](https://github.com/yontrack/yontrack/issues/1893) | Remove `ontrack-extension-indicators`; CasC removed-key tolerance          | `yontrack` / `v6`       | —              |
| S3  | [#1894](https://github.com/yontrack/yontrack/issues/1894) | Slot pipeline `FAILED` status                                              | `yontrack` / `v6`       | —              |
| S4  | [#1895](https://github.com/yontrack/yontrack/issues/1895) | Backdated slot pipelines                                                   | `yontrack` / `v6`       | S3             |
| S5  | [yontrack-cli#82](https://github.com/yontrack/yontrack-cli/issues/82) | CLI: `slot pipeline fail` and `--date`                                     | `yontrack-cli` / `main` | S3, S4         |
| S6  | [#1896](https://github.com/yontrack/yontrack/issues/1896) | Scorecard module, readings table, engine, job; lead time and frequency     | `yontrack` / `v6`       | S1             |
| S7  | [#1897](https://github.com/yontrack/yontrack/issues/1897) | Success rate and MTTR under the promotion marker                           | `yontrack` / `v6`       | S6             |
| S8  | [#1898](https://github.com/yontrack/yontrack/issues/1898) | Test pass rate and flakiness                                               | `yontrack` / `v6`       | S6             |
| S9  | [#1899](https://github.com/yontrack/yontrack/issues/1899) | Charts onto the engine; remove `ontrack-extension-delivery-metrics`        | `yontrack` / `v6`       | S2, S7         |
| S10 | [#1900](https://github.com/yontrack/yontrack/issues/1900) | Estates: entity, licence, security, GraphQL, CasC, per-estate jobs         | `yontrack` / `v6`       | S6             |
| S11 | [#1901](https://github.com/yontrack/yontrack/issues/1901) | Environment-marker readings                                                | `yontrack` / `v6`       | S3, S10        |
| S12 | [#1902](https://github.com/yontrack/yontrack/issues/1902) | KDSL: estates and readings, acceptance tests                               | `yontrack` / `v6`       | S10, S11       |
| S13 | [#1903](https://github.com/yontrack/yontrack/issues/1903) | `ontrack_reading` export and re-export job                                 | `yontrack` / `v6`       | S6             |
| S14 | [#1904](https://github.com/yontrack/yontrack/issues/1904) | UI: project Scorecard section and project scorecard page                   | `yontrack` / `v6`       | S7, S8         |
| S15 | [#1905](https://github.com/yontrack/yontrack/issues/1905) | UI: estates admin page                                                     | `yontrack` / `v6`       | S10            |
| S16 | [#1906](https://github.com/yontrack/yontrack/issues/1906) | Demo seed: two estates, backdated deployments, test runs                   | `yontrack` / `v6`       | S4, S11, S14   |
| S17 | [#1907](https://github.com/yontrack/yontrack/issues/1907) | User documentation and 6.0 migration notes                                 | `yontrack` / `v6`       | S9, S11, S13   |

## 6.0, security wave — milestone `6.0` (first planned for 6.1)

| #   | GitHub | Issue                                                                      | Repo / base             | Depends on     |
|-----|--------|----------------------------------------------------------------------------|-------------------------|----------------|
| S18 | [#1908](https://github.com/yontrack/yontrack/issues/1908) | Estate security fields and `security.maturity`                             | `yontrack` / `v6`       | S10            |
| S19 | [#1909](https://github.com/yontrack/yontrack/issues/1909) | `security.remediationTime` and `security.overdue`                          | `yontrack` / `v6`       | S18            |
| S20 | [#1910](https://github.com/yontrack/yontrack/issues/1910) | Estate view                                                                | `yontrack` / `v6`       | S14, S15       |
| S21 | [#1911](https://github.com/yontrack/yontrack/issues/1911) | Findings fan-out on the estate view                                        | `yontrack` / `v6`       | S20            |
| S22 | [#1912](https://github.com/yontrack/yontrack/issues/1912) | Demo seed and documentation, security wave                                 | `yontrack` / `v6`       | S19, S21       |

---

## S1 ([#1892](https://github.com/yontrack/yontrack/issues/1892)) — `CONTEXT.md`: reading, scorecard, estate, marker

The four entries of README *Vocabulary*, each with its _Avoid_ list, before any code names them.
Docs-only, `[skip ci]`.

- Done when `CONTEXT.md` carries the four entries, and "indicator" appears only in an _Avoid_ list.
- No mobile impact, no demo: vocabulary only.

## S2 ([#1893](https://github.com/yontrack/yontrack/issues/1893)) — Remove `ontrack-extension-indicators`; CasC removed-key tolerance

README *Removals*.

- Delete the module and its wiring (`settings.gradle.kts`, `ontrack-ui` runtime dependency, the
  `api` dependency from `scm`, the `implementation` ones from `jenkins`, `sonarqube`, `github`,
  `general`, the CI test shard entry, `doc/modules.puml`).
- Delete the `indicator(s)` sub-packages of `scm`, `general`, `github`, `jenkins`, `sonarqube`, the
  `.withDependency(indicatorsExtensionFeature)` calls, and the feature construction in the four
  Jenkins/SonarQube tests.
- Delete the Jenkins pipeline-library indicator settings, their CasC context and the frontend form
  `jenkins-pipeline-libraries-indicators-form.js`.
- Metrics `ontrack_indicator` and `ontrack_indicators_computing_ms` go with the module.
- Migration: delete the `ENTITY_DATA_STORE` rows of category
  `net.nemerosa.ontrack.extension.indicators.model.Indicator`, the `STORAGE` rows of the six stores
  listed in README *Where we start from*, and every stored grant of `PROJECT_INDICATOR_MANAGER` and
  `GLOBAL_INDICATOR_MANAGER`.
- **CasC removed-key tolerance**: a registry of removed keys; a key in it is ignored with a WARN
  naming it and "removed in 6.0" instead of failing startup. Register
  `jenkins-pipeline-library-indicator`. S9 registers `e2e-promotion-metrics`.
- Regenerate the three `ontrack.graphql` dumps.
- Tests: CasC IT with the removed key (starts, warns); migration IT (rows gone, other categories
  untouched); build compiles without the module.
- No mobile impact: nothing indicator-related exists in `/mobile`. No demo: a removal.

## S3 ([#1894](https://github.com/yontrack/yontrack/issues/1894)) — Slot pipeline `FAILED` status

README *Slot `FAILED`*.

- `SlotPipelineStatus.FAILED`, terminal, from `RUNNING` only, sets `end`, optional message, same
  right as finishing a deployment. `getLastDeployedPipeline` unchanged.
- Event `slot-pipeline-failed`; slot workflow trigger `FAILED`.
- GraphQL mutation `failSlotPipeline`; KDSL binding.
- Desktop UI: `SlotPipelineStatusIcon`/`Label`, the matrix and drawer, the deployment page
  timeline, a "Mark as failed" command beside "Finish".
- **Mobile impact: yes, display only** — `useMobileDeployments` and the deployment views render
  `FAILED`; no fail action on mobile.
- Tests: service IT (transitions, rejection from `CANDIDATE`, last deployed unchanged), event and
  workflow trigger IT, KDSL acceptance, UI test for the command.
- Demo: covered by S16.

## S4 ([#1895](https://github.com/yontrack/yontrack/issues/1895)) — Backdated slot pipelines

README *Backdated deployments*.

- Optional `dateTime` on `startSlotPipeline`, `startSlotPipelineDeployment`,
  `finishSlotPipelineDeployment`, `failSlotPipeline`, `cancelSlotPipeline`; stored on the pipeline
  and its change rows.
- Constraints: not in the future; not before build creation; not before the pipeline's previous
  change; start not before the slot's latest pipeline start. Violations are user errors.
- The auto-cancel of the active pipeline takes the new start time.
- KDSL exposes `dateTime` on the pipeline calls.
- Tests: IT per constraint, auto-cancel time, events still fired.
- No mobile impact: API only. Demo: used by S16.

## S5 ([yontrack-cli#82](https://github.com/yontrack/yontrack-cli/issues/82)) — CLI: `slot pipeline fail` and `--date` (`yontrack-cli`)

- `yontrack slot pipeline fail` calling `failSlotPipeline`; `--date` on the pipeline commands.
- Tests in the CLI's style (`fakeYontrack_test.go`).
- No mobile impact, no demo.

## S6 ([#1896](https://github.com/yontrack/yontrack/issues/1896)) — Scorecard module, readings table, engine, job; lead time and frequency

README *The engine*, *Storage*, *The job*, *Settings*, *The promotion marker with no estate*,
*API*, and the first two catalogue rows.

- Module `ontrack-extension-scorecard`, feature "Delivery scorecard"; depends on
  `ontrack-extension-chart`.
- Migration: the readings table (one row per set, project, reading, day; nullable estate; FKs
  `ON DELETE CASCADE`; uniqueness with a `NULL` estate as a value). The estate FK is added in S10.
- The engine seam: sample computers per reading, aggregation over a window (median in `value`,
  p90/mean/min/max/count in `details`), recorded scope and marker.
- The no-estate set: every non-disabled project, promotion marker per model branch (last level),
  branch-model scope with the `null`-matcher rule.
- `delivery.leadTime` and `delivery.frequency` under the promotion marker; unknown reasons
  `NO_MARKER`, `NO_SAMPLES`.
- The daily job (cron setting, default 02:00), retention purge, error handling
  (`ontrack_readings_errors`, no row), timer `ontrack_readings_computation`.
- Global settings "Delivery scorecard" (window 90 days, retention 730 days, cron) with CasC.
- Project recompute mutation (`ProjectConfig`), queued.
- GraphQL `Project.scorecard` with the sets, readings and daily history.
- Tests: unit tests of the computers on sample lists; IT for the job, retention, recompute, error
  isolation; GraphQL IT.
- No mobile impact: no UI. Demo: S16.

## S7 ([#1897](https://github.com/yontrack/yontrack/issues/1897)) — Success rate and MTTR under the promotion marker

README catalogue rows `delivery.successRate` and `delivery.mttr`.

- Success rate with the in-flight exclusion stated in `details`.
- MTTR from the **first** unpromoted build after a promoted one to the next promotion, per branch;
  unknown reason `NO_FAILURE`, never 0.
- Tests: unit tests including the B1…B5 example of README *Charts*; in-flight edge cases.
- No mobile impact, no UI. Demo: S16.

## S8 ([#1898](https://github.com/yontrack/yontrack/issues/1898)) — Test pass rate and flakiness

README catalogue rows `quality.testPassRate` and `quality.testFlakiness`.

- Test stamps: data type `TestSummaryValidationDataType`. Latest run per stamp for the pass rate;
  a `FAILED` then `PASSED` run on the same build and stamp for flakiness.
- Unknown reason `NO_TEST_STAMP`.
- Tests: unit tests on runs; IT with several runs per build and stamp.
- No mobile impact, no UI. Demo: S16.

## S9 ([#1899](https://github.com/yontrack/yontrack/issues/1899)) — Charts onto the engine; remove `ontrack-extension-delivery-metrics`

README *Charts*, *Removals*.

- Move the five chart providers into the scorecard module, same names, parameters and options;
  the four promotion-level ones call the engine's sample functions (TTR with the new start,
  success rate with the in-flight exclusion). E2E keeps its recursive query.
- Dashboard widgets unchanged; `ChartsOntrackExtensions` and `ACCE2EPromotions` still pass.
- Delete the module, the E2E export, its jobs and settings form
  (`end-to-end-promotion-metrics-export-form.js`), the time-since-event family.
- Migration: delete the `SETTINGS` rows of `EndToEndPromotionMetricsExportSettings`.
- Register `e2e-promotion-metrics` as a removed CasC key (S2's mechanism).
- Tests: port `PromotionLevelChartsIT`; a test asserting chart and reading agree on the same
  interval.
- No mobile impact: the mobile UI shows no charts. Demo: the existing `home/PromotionFrequencyChart`
  widget in `DemoContent` keeps working.

## S10 ([#1900](https://github.com/yontrack/yontrack/issues/1900)) — Estates: entity, licence, security, GraphQL, CasC, per-estate jobs

README *Estates*.

- Migration: estate table, estate–label join table (FK `ON DELETE CASCADE`), per-reading window and
  target storage; the readings table's estate FK.
- Fields, default marker, qualifier, targets with met/missed.
- Licensed feature "Delivery scorecard" (`LicensedFeatureProvider`), read on every call; schema
  unchanged, licence error on use; the job skips estates when unlicensed; snapshots kept.
- Global function `EstateManagement`, granted to the roles holding `LabelManagement`.
- Label deletion refused while an estate uses the label, naming the estates.
- GraphQL: `estates`, `estate(name)`, create/update/delete, estate recompute; `Project.scorecard`
  gains the estate sets.
- CasC context for estates.
- One daily job per estate; promotion-marker estates computed with S6–S8's computers.
- Tests: IT for selection by labels (AND), label deletion refusal, licence on/off, role grants with
  `Roles.*`, CasC IT.
- No mobile impact: no mobile scorecard in 6.x. Demo: S16.

## S11 ([#1901](https://github.com/yontrack/yontrack/issues/1901)) — Environment-marker readings

README catalogue (environment column), *Estates* (qualifier).

- Lead time to first `DONE`, frequency of `DONE` per week, success rate `DONE / (DONE + FAILED)`,
  MTTR `FAILED` → next `DONE` in the slot.
- Default qualifier unless the estate names one; `NOT_LICENSED` without the environments licence.
- Tests: IT on backdated pipelines (S4), including `CANCELLED` excluded.
- No mobile impact, no UI. Demo: S16.

## S12 ([#1902](https://github.com/yontrack/yontrack/issues/1902)) — KDSL: estates and readings, acceptance tests

- KDSL bindings to manage estates and read a project's scorecard; acceptance tests for both
  markers.
- No mobile impact, no demo.

## S13 ([#1903](https://github.com/yontrack/yontrack/issues/1903)) — `ontrack_reading` export and re-export job

README *Export*.

- Export at each computation; tags `estate` (`-`), `project`, `reading`, `basis`; field `value`;
  timestamp `computedAt`.
- Re-export job (`MetricsReexportJobProvider`) replaying stored snapshots.
- Tests: IT with a capturing `MetricsExportExtension`.
- No mobile impact, no demo.

## S14 ([#1904](https://github.com/yontrack/yontrack/issues/1904)) — UI: project Scorecard section and project scorecard page

README *UI*.

- Project page section: readings × sets, value vs target, `UNKNOWN` distinct with its reason,
  `NO_FAILURE` neutral, sample counts, "Recompute" (`ProjectConfig`).
- Project scorecard page: sparkline per reading, window, branches fed, marker, details.
- `useQuery` from `@components/services/GraphQL`; no antd `List`.
- Tests: UI test on a seeded project.
- **No mobile impact, because the scorecard is desktop-only in 6.x** (README *UI*); no mobile route
  is added.
- Demo: S16.

## S15 ([#1905](https://github.com/yontrack/yontrack/issues/1905)) — UI: estates admin page

- User-menu item in the configurations group, gated on `EstateManagement`; list, create, edit,
  delete, recompute; label picker; marker choice; windows and targets per reading.
- Tests: UI test.
- No mobile impact: admin is desktop-only. Demo: S16.

## S16 ([#1906](https://github.com/yontrack/yontrack/issues/1906)) — Demo seed: two estates, backdated deployments, test runs

README *Demo*; `doc/dev-guide/demo-seed.md`.

- Estates "Demo products" (`GOLD`) and "Demo production" (`production`) over overlapping projects
  selected by labels, with targets.
- ~90 days of backdated builds, promotions, `tests` runs and pipelines, including a `FAILED` then
  `DONE`, and a flaky build.
- KDSL "validate with data and a date"; `KdslDemoTarget` uses backdated pipelines.
- The seed ends with a recompute.
- `DemoSeedTest` still passes (nothing dated after the reset, idempotent).
- No mobile impact beyond S3's display.

## S17 ([#1907](https://github.com/yontrack/yontrack/issues/1907)) — User documentation and 6.0 migration notes

README *Documentation*.

- mkdocs pages "Delivery scorecard" and "Estates", in `nav:`; promotion-level chart docs (TTR
  start); environments pages (`FAILED`, backdating).
- Migration-notes entries: indicators removed, `ontrack_dm_*` → `ontrack_reading` mapping, the TTR
  start change, removed CasC keys.
- Verified with `./gradlew :ontrack-docs:buildDocs`. Not `[skip ci]`.

## S18 ([#1908](https://github.com/yontrack/yontrack/issues/1908)) — Estate security fields and `security.maturity`

README *Security wave*.

- Migration: expected kinds, freshness, CRITICAL/HIGH remediation targets on the estate; GraphQL,
  CasC, admin page fields.
- Global setting: no-estate freshness (7 days).
- The maturity ladder over findings on in-scope branches.
- Tests: unit tests per rung, IT with findings.
- No mobile impact. Demo: S22.

## S19 ([#1909](https://github.com/yontrack/yontrack/issues/1909)) — `security.remediationTime` and `security.overdue`

- Median first observation → project-level resolution for CRITICAL+HIGH resolved in the window;
  overdue count against the estate targets; `NO_TARGET` for the no-estate set; accepted count in
  `details`.
- Tests: IT on findings with backdated observations, a version bump that is not a remediation.
- No mobile impact. Demo: S22.

## S20 ([#1910](https://github.com/yontrack/yontrack/issues/1910)) — Estate view

- "Scorecards" user-menu item (licensed); estate page with projects × readings, target colours,
  unknown distinct, roll-up row, sort, links to the project scorecard page.
- Measured-only toggle hidden while no `ESTIMATED` reading exists.
- **No mobile impact, because the estate view is desktop-only in 6.x.** Demo: S22.

## S21 ([#1911](https://github.com/yontrack/yontrack/issues/1911)) — Findings fan-out on the estate view

- Tab: one finding → projects exposed, branches, since when, through the findings cross-project
  query; filtered by project view.
- No mobile impact. Demo: S22.

## S22 ([#1912](https://github.com/yontrack/yontrack/issues/1912)) — Demo seed and documentation, security wave

- The demo estates gain expected kinds and targets; the findings demo project lights up the
  security readings and the fan-out.
- mkdocs: security readings, estate view.
