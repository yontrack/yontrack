# Security findings — Yontrack 6.0

Outcome of the grilling session of 2026-09-23 on the findings half of the scorecard work
([2026-09-scorecard.md](../2026-09-scorecard.md), section *Findings*). That session settled the
model; this one settles what an agent needs to build it: the input formats, the licence line,
exposure and events, the UI, and how Yontrack's own CI moves over.

The findings section of the scorecard document still stands, except where this document says
otherwise. Where the two disagree, this one wins.

The issue breakdown follows in this directory, under a new `initiative: findings` label.

## Where we start from

- **Findings are a product feature, not CI wiring.** They are for every project that sends data
  to Yontrack. Yontrack's own CI is the first user, not the goal.
- **Six security stamps**, not five: `SECURITY.IMAGE.BACKEND`, `SECURITY.IMAGE.UI`,
  `SECURITY.CODE`, `SECURITY.DAST`, `SECURITY.DAST.ACTIVE` and `SECURITY.SECRETS`
  (`.yontrack/ci.yaml`). Five post CHML; `SECURITY.SECRETS` posts a `ThresholdNumber`, on the
  newest released build only.
- **How each gets its counts today**, all by bash and jq:
  - Trivy writes `trivy.json` and `trivy.sarif`, runs with `--ignore-unfixed`, and not with
    `--show-suppressed`, so suppressed findings never reach the script
    (`scripts/security-image-scan.sh`).
  - `SECURITY.CODE` does not read CodeQL's SARIF: it counts open alerts through the
    code-scanning API, deliberately, so that dismissals lower the count
    (`scripts/security-code-scan.sh`).
  - `SECURITY.SECRETS` counts GitHub secret-scanning alerts. There is no gitleaks.
  - DAST already normalises ZAP, graphql-cop and Nuclei into one findings document before
    counting (`scripts/security-dast.sh:116-158`).
- **Changing a stamp's data type** leaves old runs readable with their own type, but
  `ValidationStampMetricsChartProvider` skips runs whose type differs from the stamp's, so the
  chart history would drop at the switch.
- **6.0 is built on `v6`** (`doc/dev-guide/major-branch.md`). Every branch's CI posts to the same
  instance, `vars.YONTRACK_URL` — `self.dev.yontrack.com` — whatever branch it builds.
- **#1754** ("SARIF, Trivy JSON and GitHub alerts to CHML", milestone 6.0, `status:tospec`)
  asked for the same product feature, with counts as the target.
- **Licensing** goes through a `LicensedFeatureProvider` declaring a feature id and
  `LicenseControlService.isFeatureEnabled`, failing with `LicenseFeatureException`;
  `EnvironmentsLicense` is the template.

## Decisions

### Scope

- **Own initiative and own module.** `initiative: findings`, separate from `initiative:
  scorecard`; module `ontrack-extension-findings`. The scorecard depends on it, not the other way
  round. Tables go in `ontrack-database`, as for every extension.
- **Everything targets 6.0, on `v6`.** The indicators removal does not block findings: it opened
  6.0 only in the sense of "the moment `main` carries it", and `v6` made that moot.
- **Out of scope for 6.0**: GitHub code-scanning or Dependabot webhooks through the GitHub
  ingestion, a Jenkins pipeline-library step, a GitLab CI template, SBOM and components. Each is
  its own issue the day a real user asks.

### Vocabulary

Four `CONTEXT.md` entries. "Open" and "resolved" stay plain states.

- **Finding**: one known weakness — a vulnerability, a code issue, a secret, a DAST alert — at
  one location of one project, identified by `(scanner, externalId, location)`. _Avoid_:
  vulnerability (secrets and code issues are not), CVE (one kind of external id), issue (the
  tracker's), alert.
- **Observation**: one sighting of a finding by one scan of one build. _Avoid_: occurrence,
  detection.
- **Exposure**: a finding is exposed on a branch while the latest scan of the same stamp on that
  branch reports it. _Avoid_: open (the project-level roll-up), affected.
- **Acceptance**: a decision recorded outside Yontrack, read by it, that a finding is tolerated,
  possibly until an expiry. _Avoid_: suppression (the scanner's mechanism), waiver, exception.

### Model

What the scorecard document settled stands: the finding key, observations carrying the severity,
`CRITICAL | HIGH | MEDIUM | LOW | UNKNOWN` plus the raw string, findings surviving build purge
while observations cascade with their run, resolution per `(stamp, branch)` rolled up per
project, the resolution reason `ABSENT`. Added here:

- **A purl location carries no version and no qualifiers.** `pkg:maven/org.x/y`, not
  `pkg:maven/org.x/y@1.2.3`. The installed version goes on the observation. Bumping a package to a
  version that is still vulnerable must not resolve the finding and open a new one: "resolved"
  means fixed, not bumped, and the 6.1 remediation reading would otherwise flatter itself. An
  SBOM still joins through the observation's version.
- **Exposure is stored**, in a table keyed by finding, branch and stamp, with the time it
  started, maintained at ingestion. It is what lets ingestion tell a new exposure from a known
  one.
- **A deleted branch** takes its exposure rows with it, and fires no `resolved` event: nothing was
  fixed. **A disabled branch** keeps its rows and is left out of the project-level open state.
- **Unfixed vulnerabilities** are findings like any other: `fixedVersion` is optional. Whether to
  scan for them is the project's choice.

### Acceptance

- **Read-only in Yontrack**, as settled: the scanner-side files stay the source of truth.
- **Expiry is evaluated at read time.** No job. An acceptance past its expiry stops counting when
  it is read; the `reopened` event fires at the next scan reporting the finding unaccepted. For
  Trivy this is moot — Trivy stops honouring an expired entry itself — and the nightly rescan
  follows any expiry closely.
- **A finding first seen already accepted fires no `new` event.**
- **Counts.** Accepted findings stay out of the threshold counts and are stored as a separate
  `accepted` count, never as open or resolved.

### Input

- **One door**: a typed GraphQL mutation,
  `validateBuildWithFindings(project, branch, build, validation, description, runInfo, format,
  kind, scanner, report)`, in the manner of `validateBuildWithCHML`. The report is JSON. No REST
  endpoint.
- **Synchronous.** The run, its observations, the findings and the exposure are written in one
  transaction, and the events fire in order. A bad report fails the CI step.
- **Three formats**, parsed by Yontrack itself, so that every client — CLI, KDSL, a direct
  GraphQL call — benefits from one implementation:

  | `format`   | What                                | Licence            |
  |------------|-------------------------------------|--------------------|
  | `findings` | Yontrack's neutral format           | core               |
  | `sarif`    | SARIF 2.1                           | native formats     |
  | `trivy`    | Trivy JSON, vulnerabilities only    | native formats     |

- **The validation data type** keeps the alias `security-findings`; its config stays CHML's
  warning/failed thresholds, so `computeStatus` behaves as CHML does. UNKNOWN is counted and shown
  but never trips a threshold.
- **The run's `VALIDATION_RUN_DATA`** holds the counts only, under the same keys as CHML, plus
  `accepted`.

#### Neutral format

```json
{
  "scanner": "zap",
  "kind": "DAST",
  "findings": [
    {
      "externalId": "10038",
      "location": "",
      "severity": "MEDIUM",
      "rawSeverity": "Medium",
      "title": "Content Security Policy (CSP) Header Not Set",
      "url": "https://www.zaproxy.org/docs/alerts/10038/",
      "fixedVersion": null,
      "installedVersion": null,
      "acceptance": {
        "statement": "…",
        "expiresAt": "2026-12-31",
        "source": "security/dast/suppressions.yaml"
      }
    }
  ]
}
```

- `scanner` is a free string; `kind` is `IMAGE | CODE | SECRETS | DAST | DEPENDENCIES | OTHER`.
- Required per finding: `externalId`, `location` (may be empty), `severity`, `title`. The rest is
  optional, and so is `expiresAt` inside `acceptance`.
- **The schema rejects unknown fields.** That is how a secret value has nowhere to go.
- Published as a JSON schema on the Resources page (`/add-json-schema`).
- The documentation gives the location conventions for hand-written converters: a versionless
  purl for dependencies, a path without line for code, empty for a DAST rule, the provider's alert
  number for a secret.

#### SARIF 2.1

- **`scanner`** is `runs[].tool.driver.name` lowercased, overridable by the caller. **`kind`** is
  required from the caller and applies to every run in the file: a SARIF file cannot say whether
  Trivy scanned an image, a filesystem or dependencies.
- **`externalId`** is `result.ruleId`.
- **`location`** is the first location's `artifactLocation.uri`, without region: no line, no
  column. Empty when there is none. Several results of one rule in one file collapse into one
  finding.
- **`title`** is the rule's `shortDescription`, else its `name`. **`url`** is the rule's
  `helpUri`.
- **Never read**: `result.message`, `region.snippet`, `contextRegion`. That is where a secret
  scanner puts the value.
- **Severity**: `properties.security-severity` (rule or result) by GitHub's thresholds — ≥ 9.0
  CRITICAL, ≥ 7.0 HIGH, ≥ 4.0 MEDIUM, > 0 LOW. Otherwise `level`: `error` HIGH, `warning` MEDIUM,
  `note` LOW, `none` or absent UNKNOWN. `rawSeverity` records which was read,
  `security-severity=7.5` or `level=error`. This matches what GitHub code scanning shows.
- **Acceptance**: a `suppressions[]` entry with `status` `accepted` or absent. Statement from
  `justification`, no expiry — SARIF has no field for one. `underReview` and `rejected` do not
  count.

#### Trivy JSON

- **Only `Results[].Vulnerabilities[]`.** `Secrets`, `Misconfigurations` and `Licenses` are
  ignored in 6.0; Trivy's secret results carry the matched text.
- `externalId` is `VulnerabilityID`. `location` is `PkgIdentifier.PURL` stripped of version and
  qualifiers, falling back to `PkgName`, documented as not a purl. `installedVersion` is
  `InstalledVersion`.
- `severity` is `Severity`, which has UNKNOWN natively. `rawSeverity` is
  `Severity (SeveritySource)`, e.g. `HIGH (nvd)`.
- `title` is `Title`, `url` `PrimaryURL`, `fixedVersion` `FixedVersion`.
- **Acceptance** from `ExperimentalModifiedFindings` (Trivy run with `--show-suppressed`) whose
  `Status` is `ignored` or `not_affected`: statement from `Statement`, source from `Source`, no
  expiry — Trivy's `ModifiedFinding` has none.
- `scanner` is `trivy`; `kind` comes from the caller, as for SARIF.
- **Why Trivy JSON and not Trivy's SARIF:** Trivy's SARIF writer puts the package path or the
  target in the location, never the purl, and its `ruleId` is the CVE — so one CVE in two OS
  packages of the same image would collapse into one finding, and the SBOM join would be lost.

### Licence

- **One licensed feature for every native format**: `extension.findings.native-formats`,
  "Native scanner formats". SARIF and Trivy JSON in 6.0; any future native input — another
  scanner's format, GitHub alert ingestion — goes behind the same switch.
- **Core stays unlicensed**: the findings model, the neutral format, the project Security
  section and findings pages, the run detail, the search, the events, the permission, GraphQL and
  KDSL. Without a licence, findings work fully for anyone who writes a converter; the licence
  buys not having to.
- **Without the licence**, a `sarif` or `trivy` post is rejected with a message naming the
  feature, and no run is created: the CI step fails loudly.
- **A lapsed licence** stops new native input and never hides data. Stored findings stay readable
  and keep resolving through neutral posts.

### Chart continuity

- The findings data type stores its counts under CHML's keys, and the stamp metrics chart also
  reads runs of a type the stamp's type declares compatible. Switching a stamp from CHML to
  `security-findings` keeps its chart history. A general rule in
  `ValidationStampMetricsChartProvider`, not a findings special case.
- A stamp coming from `ThresholdNumber` (our `SECURITY.SECRETS`) has no severities to continue
  from; its chart restarts.

### Events

- **Per branch exposure, the branch as entity.** `security_finding_new` when a finding becomes
  exposed on a branch where it was not, including a return after resolution, flagged
  `reopened: true` in the event context. `security_finding_resolved` when a branch's latest scan
  of that stamp no longer reports it.
- A subscription on branch `main` therefore answers "new HIGH on `main`"; one on the project sees
  every branch.
- None per observation.

### Security

- **A new project function, `ProjectFindingsView`**, granted by default to every built-in role
  that has project view. The default behaves as project view; an administrator can take it away
  from a role when DAST findings on a live deployment are sensitive.
- No secret value is ever stored: the neutral schema has no field for one, the SARIF parser never
  reads messages or snippets, the Trivy parser ignores the secret class.

### Reading surfaces

- **Search.** A core `SearchIndexer` on the external id: searching `CVE-2021-44228` lists the
  projects and branches exposed and lands on the finding page. The estate view's fan-out table
  stays in 6.1, licensed, as the richer answer.
- **GraphQL**: `Project.findings(filter)`, `ValidationRun.findings`, and `findings(externalId)`
  across projects, each filtered by `ProjectFindingsView`.
- **KDSL**: posting a `security-findings` run in each format and reading `project.findings`. The
  acceptance tests need both.
- **CasC**: nothing new; the stamp's data type config already goes through the stamp CasC.
- **Not exported** through `MetricsExportService`.

### UI

- **Project page — Security section**: open findings by severity and their branch exposure,
  linking to the findings page. Named for what it will hold, not for CVEs.
- **Project findings page**: a table filtered by severity, state (open, accepted, resolved),
  branch, scanner and kind.
- **Finding page**: exposure per branch, the timeline of observations, the acceptance with its
  statement and expiry, the link to `url`.
- **Validation run detail**: the findings of that scan.
- **Mobile UI: no findings in this cycle.** Stated, not silent.

### Performance

The synchronous door is kept under watch:

- A Micrometer timer `ontrack_findings_ingestion` tagged by format, and a distribution summary of
  the findings count per report.
- JDBC batch inserts for observations and exposure.
- An integration test ingesting a 5 MB SARIF file, logging its duration, asserting none — timings
  are not reliable in CI.
- A documented threshold for moving parsing to a queue: p95 above 5 s on
  `self.dev.yontrack.com`.

### CLI

In `yontrack/yontrack-cli`: `yontrack validate … findings --format findings|sarif|trivy --kind
<kind> [--scanner <name>] --report <file>`. It sends the file; it parses nothing.

### Documentation

A mkdocs page under `ontrack-docs/docs/content/`, added to `nav:` in `ontrack-docs/mkdocs.yml`:
the model (finding, observation, exposure, acceptance, resolution), the three formats with the
SARIF and Trivy field mappings and the licence line, the CLI command, and worked examples for
Trivy and CodeQL. It is the contract external users read.

### Demo

`DemoContent` gets one project with findings on two branches: a HIGH present for a few builds and
then fixed on `main`, still exposed on `release/x`; a CRITICAL under an unexpired acceptance; the
search finding the CVE. The seed posts `security-findings` runs through the API like any other
validation.

### #1754

Reused, not duplicated: rewritten as the SARIF issue of this initiative, moved from
`initiative: security-scans` to `initiative: findings`, milestone 6.0 kept. Of its other
candidates, Trivy JSON is now its own issue here and GitHub alert ingestion is out of scope.

### Yontrack's own CI

The last issue of the initiative, on `main`, blocked until `self.dev.yontrack.com` runs 6.0 with
a full licence — in practice, after the 6.0 release. It switches the six stamps, in `ci.yml`,
`codeql.yml`, the DAST workflows and the nightly `security-rescan.yml`:

| Stamps                         | Source                          | Format                                                         |
|--------------------------------|---------------------------------|----------------------------------------------------------------|
| `SECURITY.IMAGE.*`             | Trivy                           | `trivy`, run with `--show-suppressed`                           |
| `SECURITY.CODE`                | CodeQL, code-scanning alerts API | `findings`, dismissals as acceptances, rule id and path        |
| `SECURITY.SECRETS`             | secret-scanning alerts API      | `findings`, alert number as location, never the value          |
| `SECURITY.DAST`, `.DAST.ACTIVE` | the existing normaliser          | `findings`                                                     |

- `--ignore-unfixed` stays on our Trivy runs in 6.0.
- CodeQL stays on the alerts API rather than SARIF because dismissals live in GitHub, not in the
  SARIF file: sending SARIF would bring dismissed alerts back as open.
- Both licensed formats are then exercised: Trivy JSON on every build by our CI, SARIF by the
  KDSL acceptance tests and the demo.

## What this hands to the scorecard

- Findings, observations and the stored exposure are the inputs of `security.maturity` and
  `security.remediation` (6.1). Accepted findings are their own count, as the readings expect.
- Remediation time is measured on versionless locations, so a bump that leaves the CVE in place
  is not a remediation.
- The fan-out table of the estate view reads the same cross-project query the search uses.

## Sources

- [2026-09-scorecard.md](../2026-09-scorecard.md), section *Findings*, which this session started
  from.
- [2026-09-harvesting-brief.md](../2026-09-harvesting-brief.md), block 3.
- [2026-09-dast.md](../2026-09-dast.md) for the DAST severities, suppressions and per-rule
  counting.
- `scripts/security-image-scan.sh`, `scripts/security-code-scan.sh`, `scripts/security-dast.sh`,
  `scripts/security-rescan.sh`, `.yontrack/ci.yaml`, `.github/workflows/ci.yml`, `codeql.yml`,
  `dast-passive.yml`, `dast-active.yml`, `security-rescan.yml`.
- `doc/dev-guide/major-branch.md` for `v6`.
- `CHMLValidationDataType`, `ValidationStampMetricsChartProvider`, `StructureJdbcRepository`
  (run data typing), `EnvironmentsLicense`.
- Trivy: `pkg/report/sarif.go` (SARIF locations) and `pkg/types/finding.go` (`ModifiedFinding`).
- [#1754](https://github.com/yontrack/yontrack/issues/1754).
