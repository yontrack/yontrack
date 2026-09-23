# Security findings — issue breakdown

Breakdown of [README.md](README.md) into agent-sized issues. Created on 2026-09-23; #1754 was
rewritten as F7 rather than duplicated.

Every issue carries `initiative: findings`, `type: enhancement`, `security`, `status:todo` and
`ready-for-agent`, and links back to the README section it implements. Dependencies are recorded
as native GitHub dependencies as well as prose. Base branch is `v6` unless stated.

| #   | GitHub | Issue                                                         | Repo / base          | Depends on        |
|-----|--------|---------------------------------------------------------------|----------------------|-------------------|
| F1 | [#1854](https://github.com/yontrack/yontrack/issues/1854) | `CONTEXT.md`: finding, observation, exposure, acceptance      | `yontrack` / `v6`    | —                 |
| F2 | [#1855](https://github.com/yontrack/yontrack/issues/1855) | Findings module, tables and `ProjectFindingsView`             | `yontrack` / `v6`    | F1                |
| F3 | [#1856](https://github.com/yontrack/yontrack/issues/1856) | `security-findings` data type, neutral format and the door    | `yontrack` / `v6`    | F2                |
| F4 | [#1857](https://github.com/yontrack/yontrack/issues/1857) | Exposure, resolution and acceptance                           | `yontrack` / `v6`    | F3                |
| F5 | [#1858](https://github.com/yontrack/yontrack/issues/1858) | `security_finding_new` / `security_finding_resolved` events   | `yontrack` / `v6`    | F4                |
| F6 | [#1859](https://github.com/yontrack/yontrack/issues/1859) | Stamp chart continuity across compatible data types           | `yontrack` / `v6`    | F3                |
| F7 | [#1754](https://github.com/yontrack/yontrack/issues/1754) | Native formats licence and SARIF input (#1754, rewritten)      | `yontrack` / `v6`    | F3                |
| F8 | [#1860](https://github.com/yontrack/yontrack/issues/1860) | Trivy JSON input                                              | `yontrack` / `v6`    | F7                |
| F9 | [#1861](https://github.com/yontrack/yontrack/issues/1861) | Findings GraphQL read API                                     | `yontrack` / `v6`    | F4                |
| F10 | [#1862](https://github.com/yontrack/yontrack/issues/1862) | Search findings by external id                                | `yontrack` / `v6`    | F9                |
| F11 | [#1863](https://github.com/yontrack/yontrack/issues/1863) | KDSL: post and read findings, acceptance tests                | `yontrack` / `v6`    | F7, F8, F9        |
| F12 | [#1864](https://github.com/yontrack/yontrack/issues/1864) | UI: project Security section and findings page                | `yontrack` / `v6`    | F9                |
| F13 | [#1865](https://github.com/yontrack/yontrack/issues/1865) | UI: finding page and findings on the run detail               | `yontrack` / `v6`    | F12               |
| F14 | [#1866](https://github.com/yontrack/yontrack/issues/1866) | Ingestion performance watch                                   | `yontrack` / `v6`    | F7                |
| F15 | [#1867](https://github.com/yontrack/yontrack/issues/1867) | Demo seed: findings                                           | `yontrack` / `v6`    | F7, F13           |
| F16 | [#1868](https://github.com/yontrack/yontrack/issues/1868) | User documentation: security findings                         | `yontrack` / `v6`    | F8, F17           |
| F17 | [yontrack-cli#78](https://github.com/yontrack/yontrack-cli/issues/78) | CLI: `validate … findings`                                    | `yontrack-cli` / `main` | F3             |
| F18 | [#1869](https://github.com/yontrack/yontrack/issues/1869) | Switch Yontrack's own security stamps to findings             | `yontrack` / `main`  | all, and 6.0 deployed on `self.dev.yontrack.com` |

Milestone `6.0` for F1–F17. F18 lands after the 6.0 release, see its section.

---

## F1 ([#1854](https://github.com/yontrack/yontrack/issues/1854)) — `CONTEXT.md`: finding, observation, exposure, acceptance

The four entries of README *Vocabulary*, each with its _Avoid_ list, before any code names them.

- Done when `CONTEXT.md` carries the four entries, and "open"/"resolved" are used as plain states.

## F2 ([#1855](https://github.com/yontrack/yontrack/issues/1855)) — Findings module, tables and `ProjectFindingsView`

- New module `ontrack-extension-findings` with its `ExtensionFeature`.
- A migration in `ontrack-database` creating the finding, observation and exposure tables
  (README *Model*): finding keyed by `(project, scanner, externalId, location)` with
  `firstSeen`, `lastSeen`, `resolvedAt`, `maxSeverity`, FK to the project `ON DELETE CASCADE`;
  observation per finding and validation run, with severity, raw severity, installed version,
  acceptance (statement, expiry, source), cascading with the run; exposure keyed by finding,
  branch and stamp with its start, cascading with the branch.
- Repository layer with JDBC batch inserts.
- Project function `ProjectFindingsView`, granted to every built-in role that has project view.
- Tests: repository IT (cascade on run and branch deletion, findings surviving build purge);
  role grants test using `Roles.*`.
- No mobile impact: no UI.

## F3 ([#1856](https://github.com/yontrack/yontrack/issues/1856)) — `security-findings` data type, neutral format and the door

- Validation data type, alias `security-findings`, config = CHML warning/failed thresholds;
  `computeStatus` as CHML. Run data stores counts only under CHML's keys plus `accepted`.
  Accepted findings excluded from thresholds; UNKNOWN counted, never a threshold.
- Typed mutation `validateBuildWithFindings(project, branch, build, validation, description,
  runInfo, format, kind, scanner, report)`, synchronous, one transaction. `format` accepts
  `findings` here; `sarif` and `trivy` come with F7 and F8.
- Neutral format parser (README *Neutral format*), rejecting unknown fields, with a JSON schema
  on the Resources page (`/add-json-schema`).
- Location normalisation: a purl is stored without version and qualifiers; the version goes to
  the observation.
- Writes findings (upsert, `firstSeen`/`lastSeen`/`maxSeverity`) and observations. Exposure is F4.
- `.yontrack/ci.yaml` config injection accepts the `security-findings` alias.
- Tests: unit tests for the parser and the purl normalisation; IT posting reports and checking
  rows, counts and status.

## F4 ([#1857](https://github.com/yontrack/yontrack/issues/1857)) — Exposure, resolution and acceptance

- Exposure maintained at ingestion per `(stamp, branch)`: exposed while the latest scan of that
  stamp on that branch reports the finding; absence resolves it there with reason `ABSENT`.
- Project-level open state: exposed on any branch matched by the branch model (every branch when
  none), disabled branches excluded.
- Branch deletion removes exposure rows silently.
- Acceptance read-only; expiry evaluated at read time; accepted is neither open nor resolved.
- Tests: IT covering fixed on `main` / still on `release/x`, the UI stamp never resolving a
  backend finding, reappearance, expiry, disabled and deleted branches.

## F5 ([#1858](https://github.com/yontrack/yontrack/issues/1858)) — `security_finding_new` / `security_finding_resolved` events

- Project-scoped event types with the branch as entity (README *Events*); `new` on a new exposure
  including a return, with `reopened` in the context; `resolved` on resolution. Nothing per
  observation; nothing for a finding first seen accepted; nothing on branch deletion.
- Context: severity, external id, location, scanner, kind, branch.
- Tests: IT with a notification subscription on the branch and on the project.
- Generated docs pick the event types up; regenerate with `:ontrack-docs:integrationTest`.

## F6 ([#1859](https://github.com/yontrack/yontrack/issues/1859)) — Stamp chart continuity across compatible data types

- `ValidationStampMetricsChartProvider` also reads runs whose data type the stamp's type declares
  compatible; `security-findings` declares CHML compatible.
- Tests: switching a stamp from CHML keeps the earlier runs in the chart.
- No mobile impact: the mobile UI shows no stamp charts (to be checked in the issue).

## F7 ([#1754](https://github.com/yontrack/yontrack/issues/1754)) — Native formats licence and SARIF input (#1754, rewritten)

- Licensed feature `extension.findings.native-formats`, "Native scanner formats", through a
  `LicensedFeatureProvider`, on the `EnvironmentsLicense` pattern.
- Without it, a `sarif` (and later `trivy`) post is rejected naming the feature; no run created.
  Stored findings stay readable.
- SARIF 2.1 parser per README *SARIF 2.1*: scanner, kind, externalId, location without region,
  title, url, severity mapping, acceptance from `suppressions[]`; never reading messages,
  snippets or context regions.
- Tests: unit tests on SARIF samples from CodeQL, Trivy and Semgrep; licence IT with and
  without the feature.
- #1754 is rewritten to this body, moved to `initiative: findings`, milestone 6.0 kept.

## F8 ([#1860](https://github.com/yontrack/yontrack/issues/1860)) — Trivy JSON input

- Parser per README *Trivy JSON*: vulnerabilities only, versionless purl with `PkgName`
  fallback, severity and source, acceptance from `ExperimentalModifiedFindings`.
- Behind the F7 licence.
- Tests: unit tests on a `trivy image --format json --show-suppressed` sample, including the same
  CVE in two packages.

## F9 ([#1861](https://github.com/yontrack/yontrack/issues/1861)) — Findings GraphQL read API

- `Project.findings(filter)`, `ValidationRun.findings`, root `findings(externalId)` across
  projects, filtered by `ProjectFindingsView`. Filters: severity, state, branch, scanner, kind.
- Finding type exposes its exposure per branch, observations and acceptance.
- Tests: GraphQL ITs including a user without `ProjectFindingsView`.

## F10 ([#1862](https://github.com/yontrack/yontrack/issues/1862)) — Search findings by external id

- Core `SearchIndexer` on the external id; a result lists the project and the branches exposed
  and links to the finding page (F13).
- Tests: IT searching `CVE-…` across two projects, and a user who cannot see one of them.
- Mobile: search is shared with the mobile UI — state which way it goes (README says no
  findings on mobile; results must not link to a missing mobile page).

## F11 ([#1863](https://github.com/yontrack/yontrack/issues/1863)) — KDSL: post and read findings, acceptance tests

- KDSL to post a `security-findings` run in each format and to read `project.findings`.
- Acceptance tests covering the three formats, the licence refusal, and resolution across two
  branches.

## F12 ([#1864](https://github.com/yontrack/yontrack/issues/1864)) — UI: project Security section and findings page

- Project page **Security** section: open findings by severity and branch exposure, linking to
  the findings page.
- Project findings page: table filtered by severity, state, branch, scanner, kind.
- Playwright spec in `ontrack-web-tests`.
- Mobile: none this cycle, stated in the issue per `doc/dev-guide/ui/mobile-impact.md`.

## F13 ([#1865](https://github.com/yontrack/yontrack/issues/1865)) — UI: finding page and findings on the run detail

- Finding page: exposure per branch, observation timeline, acceptance with statement and expiry,
  link to `url`.
- Validation run detail: the findings of that scan.
- Playwright spec. Mobile: none this cycle, stated.

## F14 ([#1866](https://github.com/yontrack/yontrack/issues/1866)) — Ingestion performance watch

- Micrometer timer `ontrack_findings_ingestion` tagged by format, distribution summary of the
  findings count per report.
- IT ingesting a 5 MB SARIF file, logging its duration, asserting none.
- Dev-guide note: move parsing to a queue when p95 exceeds 5 s on `self.dev.yontrack.com`.

## F15 ([#1867](https://github.com/yontrack/yontrack/issues/1867)) — Demo seed: findings

- `DemoContent`: one project, a HIGH fixed on `main` after a few builds and still exposed on
  `release/x`, a CRITICAL under an unexpired acceptance, the CVE findable by search.
- Posted through the API. The demo runs with the dev licence, so SARIF is used for one of the
  scans.

## F16 ([#1868](https://github.com/yontrack/yontrack/issues/1868)) — User documentation: security findings

- Mkdocs page under `ontrack-docs/docs/content/`, added to `nav:`: the model, the three formats
  with the SARIF and Trivy mappings and the licence line, the CLI command, worked examples for
  Trivy and CodeQL.
- Verified with `./gradlew :ontrack-docs:buildDocs`.

## F17 ([yontrack-cli#78](https://github.com/yontrack/yontrack-cli/issues/78)) — CLI: `validate … findings`

- In `yontrack/yontrack-cli`: `yontrack validate … findings --format findings|sarif|trivy --kind
  <kind> [--scanner <name>] --report <file>`, calling `validateBuildWithFindings`. Sends the file,
  parses nothing.
- Can ship before 6.0: against a 5.x server it fails with the server's error.

## F18 ([#1869](https://github.com/yontrack/yontrack/issues/1869)) — Switch Yontrack's own security stamps to findings

Blocked until `self.dev.yontrack.com` runs 6.0 with a full licence, i.e. after the 6.0 release;
no milestone until then.

- `.yontrack/ci.yaml`: the six stamps to `security-findings`, keeping their thresholds.
- `SECURITY.IMAGE.*` (in `ci.yml` and `security-rescan.yml`): `trivy` format, Trivy run with
  `--show-suppressed`, `--ignore-unfixed` kept.
- `SECURITY.CODE` (`codeql.yml`): alerts API to the neutral format, dismissals as acceptances.
- `SECURITY.SECRETS` (`security-rescan.yml`): alerts API to the neutral format, alert number as
  location, never the value.
- `SECURITY.DAST`, `SECURITY.DAST.ACTIVE`: the normaliser's output to the neutral format.
- Needs the F17 CLI release bumped in the workflows.
