# Evidence of the CI stamps

*Introduced by [#2128](https://github.com/yontrack/yontrack/issues/2128), part of
`initiative: v6-audit-trail`.*

Yontrack's own CI reports to the Yontrack instance that manages Yontrack, which has the audit trail
on. Every test and scan stamp attaches the report it was computed from and a readable HTML version
as **evidence** on its validation run, so that the trail of a build pins what each stamp was
computed from.

Every call goes through `scripts/ci-evidence.sh`, in place of `yontrack`:

```bash
scripts/ci-evidence.sh junit BUILD "**/build/test-results/test/*.xml" --tool gradle -- \
  validate --validation BUILD \
  junit --pattern "**/build/test-results/test/*.xml" --fail-when-no-results \
  $run_time_flag $YONTRACK_RUN_INFO
```

The `--` is followed by the `yontrack` call as it would be written anyway. The glob before it is the
same as the `--pattern` after it: the zip holds what the stamp counted, and the script reads a glob
the way the CLI does.

## What each stamp attaches

| Stamp | Reported by | Raw evidence | HTML evidence |
|---|---|---|---|
| `BUILD` | `ci.yml` | `BUILD-junit.zip` | `BUILD-report.html` |
| `UI_UNIT` | `ci.yml` | `UI_UNIT-junit.zip` | `UI_UNIT-report.html` |
| `INTEGRATION` | `ci.yml`, `integration-report` | `INTEGRATION-junit.zip` | `INTEGRATION-report.html` |
| `KDSL.ACCEPTANCE` | `ci.yml`, `kdsl-report` | `KDSL.ACCEPTANCE-junit.zip` | `KDSL.ACCEPTANCE-report.html` |
| `KDSL.HA` | `ci.yml`, `kdsl-ha` | `KDSL.HA-junit.zip` | `KDSL.HA-report.html` |
| `PLAYWRIGHT` | `ci.yml`, `ui-tests-report` | `PLAYWRIGHT-junit.zip` | `PLAYWRIGHT-report.html` |
| `GITLAB.REAL` | `gitlab-real.yml` | `GITLAB.REAL-junit.zip` | `GITLAB.REAL-report.html` |
| `BITBUCKET.REAL` | `bitbucket-real.yml` | `BITBUCKET.REAL-junit.zip` | `BITBUCKET.REAL-report.html` |
| `DEMO.SMOKE` | `demo-smoke.yml` | `DEMO.SMOKE-junit.zip` | `DEMO.SMOKE-report.html` |
| `SEARCH.PERFORMANCE` | `search-perf.yml`, through `scripts/search-perf-validate.sh` | `SEARCH.PERFORMANCE-report.json` | `SEARCH.PERFORMANCE-report.html` |
| `SECURITY.IMAGE.BACKEND`, `SECURITY.IMAGE.UI` | `ci.yml`, `security-rescan.yml` | `<STAMP>-trivy.json` | `<STAMP>-report.html` |
| `SECURITY.CODE` | `codeql.yml` | `SECURITY.CODE-findings.json` | `SECURITY.CODE-report.html` |
| `SECURITY.SECRETS` | `security-rescan.yml` | `SECURITY.SECRETS-findings.json` | `SECURITY.SECRETS-report.html` |
| `SECURITY.DAST` | `dast-passive.yml` | `SECURITY.DAST-findings.json`, `SECURITY.DAST-manifest.json` | `SECURITY.DAST-report.html` |
| `SECURITY.DAST.ACTIVE` | `dast-active.yml` | `SECURITY.DAST.ACTIVE-findings.json`, `SECURITY.DAST.ACTIVE-manifest.json` | `SECURITY.DAST.ACTIVE-report.html` |

- **`<STAMP>-junit.zip`** holds the JUnit XML files unmodified, one zip per stamp: attaching each
  XML would mean hundreds of evidences and trail entries per run. A stamp with several shards or
  legs attaches from the job that already collects them.
- The Trivy, findings and search performance files are the ones the validation already sends or
  reads, under a stable name.
- **A FAILED stamp attaches what exists**: it is the run whose evidence is most worth having.

Out of scope, deliberately: `COVERAGE.*` (`coverage-reports` is over the 50 MB limit, and these are
figures rather than outcomes), `DOCS`, `LICENSE.DEV`, the release receipts, CodeQL's SARIF (the
alerts API is what `SECURITY.CODE` counts), an SBOM, and the native Gradle, Playwright and Jest HTML
reports. `release/5.5` keeps its own workflows and CLI pin; its released builds still get evidence
through the rescan, which runs from `main`.

## The renderer

`scripts/ci-evidence.py`, Python standard library only, makes one self-contained HTML file per
stamp, and the zips. The native reports do not do: Gradle's are multi-file, per module and per
shard; Playwright's come per matrix leg and traces can push them past the size limit; Jest has only
`jest-junit` configured; Trivy's HTML template is not part of the scan step.

- **Header:** stamp, build, commit, workflow run, generation time and the SHA-256 of each raw file.
  A build named on the command line has its commit asked of the instance: a rescan runs from `main`
  on a released build, so `$GITHUB_SHA` would name the wrong one.
- **Tests:** totals, per-suite counts, each test's status and time, and failures with their stack
  traces. No `system-out` or `system-err`: they stay in the zip. The counts are read off the suite
  headers, as the CLI computes the stamp.
- **Scans:** each finding with its severity and accepted state, counted as the stamp counts them:
  it mirrors `TrivyFindingsReportParser` (vulnerabilities only, never the matched text of a secret)
  and `FindingsConsolidation` (one ID on one location is one finding, an expired acceptance counts
  again). If those change, so does this.
- **Search performance:** a table of the figures, and the failures.

It loads nothing, and every value is escaped: test names and finding titles are untrusted, and the
file is opened locally. HTML evidence is download only, which the evidence UI already enforces.

`SECURITY.DAST*` keep their disclosure rule: see *Disclosure* in `security/dast/README.md`.

## Metadata

Every evidence carries `--evidence-tool`, `--evidence-tool-version` and `--evidence-source-url`, the
workflow run. The CLI applies them to every evidence of a command, so the HTML carries the metadata
of the tool that produced its raw file. A version is never written a second time:

| Tool | Version from |
|---|---|
| `gradle` | `gradle/wrapper/gradle-wrapper.properties` |
| `jest`, `playwright` | the lock file of `ontrack-web-core`, `ontrack-web-tests` |
| `trivy` | `trivy version` |
| `codeql` | the `codeql-version` output of `github/codeql-action/init` |
| `github-secret-scanning` | the version of the GitHub API that answers, as it has none of its own |
| `yontrack-dast` | the scanners' own reports: `scripts/security-dast.sh tool-versions` |
| `searchPerfTest` | the version measured: the test is part of it |

## When evidence goes wrong

A missing evidence is an audit gap, so it is strict everywhere: no `--evidence-optional`. But
evidence must never lose the stamp:

1. **Only files that exist and are not empty are passed.** The CLI checks every `--evidence` before
   it creates the validation run: a bad one would record no run at all, and stall a promotion. So
   when the bundling or the renderer fails, the validation is still sent, with what exists.
2. **The step then fails**, so the gap shows up in red. That includes a version that could not be
   read, and a validation saying anything but FAILED with no report behind it: a wrong glob must
   not go unseen. A refused upload behaves the same: the run is recorded and the command fails.
3. **A job with no report at all** still sends its bare `--status FAILED`, with no evidence, as it
   always did, and adds no failure of its own: the real one stays visible.

Red has a cost in `build`: its stamp is recorded, but the steps after it are skipped, the Docker
image among them, and so are the jobs that need `build`. Their stamps are absent from that build
until the run is re-run, as for any other failure of `Validate BUILD`.

A Spaces outage therefore turns the pipeline red. That is accepted: CI already depends on the
instance to record its validations. A `storage-not-configured` or `storage-unreachable` refusal goes
to whoever runs that instance.

No promotion requires evidence: strict mode already makes a missing upload fail.

## Which builds

- **Every full run** of `ci.yml`: test artifacts expire after 7 days, so evidence cannot be added
  later to a build promoted afterwards.
- **`security-rescan.yml` attaches on every run**, on the released builds it targets: about 1,500
  image reports a year, roughly 4 GB at about 3 MB each (an estimate). Attaching only on change would
  leave nights with no proof a scan ran.

## Verifying it

`yontrack audit-trail verify` only checks a JSON export offline, not the evidence. Ask the instance:

```graphql
{
  builds(project: "yontrack", branch: "main", name: "<build>") {
    auditTrail {
      verification(includeEvidence: true) {
        chainIntact
        endorsementsValid
        missingEvidence
        alteredEvidence
      }
    }
  }
}
```

## Tests

```bash
scripts/ci-evidence-test.sh
```

`scripts/security-dast-test.sh` asserts the disclosure rule on the HTML,
`scripts/search-perf-validate-test.sh` the evidence of its stamp. Like the other suites of
`scripts/`, they are run by hand.

## Adding a stamp

Call `scripts/ci-evidence.sh` instead of `yontrack`, on both the passing and the FAILED branches, in
a job that has the repository checked out, then add it to the table above. A tool the script does
not know takes `--tool-version` from its pin. A step with commands after the validation has to keep
its exit code: see `Validate SECURITY.CODE` in `codeql.yml`.
