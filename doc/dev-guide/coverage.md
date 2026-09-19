# Test coverage

Every push build on `main` and `release/*` measures how much of Yontrack its tests execute, and
records the answer on the build as six `COVERAGE.*` validation stamps. The same reports and the
same figures come out of a checkout with two commands.

This page says **how**. The decisions and their reasons are in the design record,
[`docs/grilling/2026-09-coverage/README.md`](../../docs/grilling/2026-09-coverage/README.md).

## What is measured

| Figure | Source | Covers |
|---|---|---|
| `COVERAGE.UNIT` | JaCoCo, Gradle test JVM | the JVM unit tests (`*Test.kt`) |
| `COVERAGE.INTEGRATION` | JaCoCo, Gradle test JVM | the integration tests (`*IT.kt`) |
| `COVERAGE.KDSL` | JaCoCo, backend container | the KDSL acceptance tests (`ACC*.kt`) |
| `COVERAGE.UI` | JaCoCo, backend container | the Playwright legs (main, ldap, oidc) |
| `COVERAGE.TOTAL` | JaCoCo, all four merged | the backend, by the whole suite |
| `COVERAGE.UI_UNIT` | Jest | the frontend, by the Jest suite |

Two collection mechanisms, because the code under test runs in two places. The unit and
integration tests run in a Gradle test JVM, so the Gradle `jacoco` plugin attaches the agent
directly (#1818). The KDSL and Playwright suites drive a `nemerosa/ontrack` **container**, so the
agent gets in through the coverage-only Compose override `compose/docker-compose-coverage.yml` and
its data is pulled out over TCP with `jacococli dump` before the stack goes down (#1819). The
released image is untouched either way.

### What is not measured

- **Frontend coverage from the Playwright tests.** Only the backend is measured while Playwright
  runs; `COVERAGE.UI` is backend coverage exercised *through* the UI, not frontend coverage.
  Measuring the frontend there needs production source maps in the released image, or a separate
  coverage image, and neither is worth its cost yet. The only frontend figure is
  `COVERAGE.UI_UNIT`, from Jest.
- **Pull requests.** Coverage is collected on push builds of `main` and `release/*` only.
- **Per-test origin.** "Which test covers this line" is not answered. Origin is per test *type*,
  at class level — see [the Sessions page](#the-sessions-page).
- **Anything external.** No Codecov, no SonarQube.

### The denominator

All production modules count. Excluded are `ontrack-test-utils`, `ontrack-it-utils`, the
`ontrack-kdsl*` modules, `ontrack-web-tests`, `ontrack-web-core`, `ontrack-demo-seed`, `buildSrc`
and generated code — test tooling, clients of the product, and code that is not JVM production
code at all.

**A class no test ever touches counts as 0%, and that is the point.** JaCoCo's own default is to
report only the classes it saw executed, which measures the tests rather than the code: a module
nobody exercises would simply not appear, and deleting its tests would *raise* the percentage. The
exclusion list is applied on the report, never on collection, and it lives in exactly one place:
the `CR_EXCLUDED_MODULES` array at the top of
[`scripts/coverage-report.sh`](../../scripts/coverage-report.sh).

The frontend follows the same principle through `collectCoverageFrom` in
`ontrack-web-core/jest.coverage.js`, which lists the production roots explicitly so that a file no
test imports counts as 0% too.

## Reading the figures

Each backend stamp records `line`, `branch` and `unique_line`; `COVERAGE.TOTAL` and
`COVERAGE.UI_UNIT` record `line` and `branch`. All are percentages from 0 to 100 with one decimal.
Line coverage is the headline and branch coverage sits beside it.

### `unique_line`

**The figure most likely to be misread.** `unique_line` is the percentage of lines covered by that
test type **and by no other backend type**, over the same denominator as `line`. The four are
therefore directly comparable and sum to at most `COVERAGE.TOTAL`'s `line`.

It is not a measure of how good a test level is. It is the answer to one question: *what would be
lost if this whole level were deleted?* A type with a high `line` and a `unique_line` near zero
covers a great deal that something else already covers. That may be exactly right — the KDSL suite
is there to prove the API works end to end, not to reach lines nothing else reaches — or it may be
a level that is no longer earning its run time. The figure states the fact; the judgement is
yours.

`unique_line` is measured *against* the other types, so it is only honest when all four reported.
When a type's execution data is missing, the other types' `unique_line` is dropped rather than
sent inflated (`scripts/coverage-validate.sh`).

### `COVERAGE.UI_UNIT`'s `branch`

Read it with care. The Jest suite runs under `coverageProvider: 'v8'`, and v8 reports **a file no
test ever loaded as one uncovered branch** rather than as its real branch count. The denominator is
therefore badly undercounted and the percentage flatters itself. `line` has no such problem: an
unloaded file reports every one of its lines as uncovered, which is what the denominator decision
asks for. The comment in `ontrack-web-core/jest.coverage.js` has the detail.

It is also **the one figure that does not reproduce**. Two consecutive runs of
`:ontrack-web-core:testCoverage` on the same commit and the same machine gave 55.1 and 54.1, on
branch totals of 3139 and 3108: the denominator itself moves, because which files v8 attributes
branch ranges to depends on what it happened to load and optimise. So do not read a couple of
points between your run and CI's as a difference in the code, or as the two paths having diverged —
everything else here is exact. `line` is stable to a hundredth over the same runs.

### Why `COVERAGE.KDSL` and `COVERAGE.UI` look high

Because they measure a **running instance**, not just the test's own calls. The agent is attached
to the backend container for its whole life, so its data includes Spring's startup, the scheduled
jobs that fire while the suite runs, and the API calls the specs make to seed their own data.

This is accepted as is, with **no counter reset** after startup. Someone eventually notices that a
65-file acceptance suite appears to cover half the codebase; this is the answer. The `unique_line`
figure is the corrective: startup and jobs are covered by the integration tests too, so they add
nothing to a type's unique share.

## The six stamps

They are declared in [`.yontrack/ci.yaml`](../../.yontrack/ci.yaml) with the `metrics` data type,
and they are **recording only**:

- in **no promotion** — not BRONZE, not SILVER, not GOLD;
- **no thresholds** — `metrics` has no notion of pass or fail, so a run is PASSED whenever its
  figures exist;
- **nothing is gated on them.** A coverage drop does not redden `main`.

Like the `SECURITY.*` stamps, they exist to be charted and looked at.

A stamp goes **FAILED** in one case only: its test type lost its execution data — a shard whose
artefact never arrived, or a dump that produced nothing. The description then names the missing
sessions. The types that did report keep their `line` and `branch` and lose their `unique_line`,
and `COVERAGE.TOTAL` follows any incomplete type. Partial figures are never sent: a merged figure
computed over four fifths of the suite reads as a real coverage drop, which is worse than no
figure at all.

### Where the CI reports live

The `coverage` job of `.github/workflows/ci.yml` uploads a `coverage-reports` artefact with
**30-day retention**, holding the four per-type JaCoCo reports, the merged one and the Jest report.
The validation run description links to it. After thirty days the figures survive on the build and
the reports do not; re-run the suite locally.

## Running it locally

Two steps: run the suites with `-Pcoverage`, then merge and report.

```bash
# Collect. -Pcoverage is what turns the agent on; without it nothing is written.
./gradlew test integrationTest -Pcoverage
./gradlew :ontrack-web-core:testCoverage -Pcoverage    # the frontend, for COVERAGE.UI_UNIT

# Merge, report and print the figures
./gradlew coverageReport
```

Take as much of the first step as you have time for. `./gradlew test -Pcoverage` alone is a
perfectly good local run: the report then shows the unit type with real figures and the other
three honestly at 0%, and `coverageReport` prints which sessions were not produced.

One figure does not survive a partial run. `unique_line` means "covered by this type and by no
other", so with only the unit tests collected every unit line is unique and `unique_line` comes out
equal to `line` — against CI's 3.8, because there four other types cover most of the same lines.
`line` and `branch` are read from one report each and are comparable with CI whatever else ran —
exactly, for the four backend types. A local `./gradlew test -Pcoverage` on the commit this page
was written for gave `COVERAGE.UNIT` 20.2 / 21.9, which is what the `coverage` job of the same
commit recorded. The one exception is `COVERAGE.UI_UNIT`'s `branch`, which does not reproduce
against itself either — see [above](#coverageui_units-branch).

The acceptance suites are the expensive half, and they work the same way:

```bash
./gradlew kdslAcceptanceTest -Pcoverage
COVERAGE_SESSION=ui-main ./gradlew uiTest -Pcoverage    # see "One stack, two names" below
```

`coverageReport` writes to `build/coverage/`:

| Path | What |
|---|---|
| `build/coverage/exec/<type>/<session>/*.exec` | the execution data, laid out as CI lays it out |
| `build/coverage/reports/{unit,integration,kdsl,ui}/html` | one report per backend test type |
| `build/coverage/reports/merged/html` | the merged report, with its *Sessions* page |
| `build/coverage/reports/coverage-metrics.json` | the six sets of figures |

The figures are also printed to the console, as the same markdown table the CI run summary shows.

### It is the same code CI runs

`coverageReport` reimplements nothing. It is three Gradle tasks, each an entry point to a script
the `coverage` job of `ci.yml` calls as well:

| Task | Runs | Which decides |
|---|---|---|
| `coverageStage` | `scripts/coverage-report.sh stage` | which `.exec` belongs to which session |
| `coverageJacocoReport` | `scripts/coverage-report.sh report` | the exclusion list, and the `jacococli` invocations |
| `coverageFigures` | `scripts/coverage-metrics.sh` | the six sets of figures |

The exclusion list, the report invocations and the figure computation are defined once, in
`scripts/`. If CI and a local run could disagree about what "72.4" means, the number would be
worthless — so a change to any of the three belongs in the script, not in the workflow and not in
the build file.

Gradle contributes the two things a shell script cannot know: where the execution data is
(`COVERAGE_TREE` is the checkout itself, where CI passes the `coverage-classes` artefact), and the
JaCoCo command line tool, resolved from the locked `jacocoCli` configuration and passed as
`COVERAGE_JACOCO_CLI` so that **a local run needs no network**.

`coverageFigures` warns instead of failing when it cannot compute the figures. The usual cause is
a Jest summary that is not there because `:ontrack-web-core:testCoverage` was never run; the JaCoCo
reports are unaffected and worth keeping.

### What a local run cannot reproduce

**The shards.** CI runs the integration tests five ways, the KDSL suite two and the main Playwright
leg three, and each leg tags its execution data with its own session. A checkout runs each suite
once. The merged figures are the same — the same lines are covered either way — but the
[*Sessions* page](#the-sessions-page) is shorter, and the sessions are named `integration` rather
than `integration-1` … `integration-5`.

This is why `coverageReport` does not run the completeness check CI runs. The expected set of a
local run is the unsharded one (`CR_LOCAL_SESSIONS` in `scripts/coverage-report.sh`), and a missing
type is *stated* rather than failed:

```
Sessions staged: integration unit
Not produced by this run: kdsl ui-ldap ui-main ui-oidc
  The reports cover what ran; a type with no execution data reports 0%.
```

**One stack, two names.** `kdslAcceptanceTest` is the Compose variant of both the KDSL suite and
the main Playwright leg. Only the CI workflow tells the two apart, by setting `COVERAGE_SESSION`,
so locally `./gradlew uiTest -Pcoverage` dumps its backend as `kdsl` and the two suites' coverage
lands in one pile. Set `COVERAGE_SESSION=ui-main` on the `uiTest` run to keep them apart — and note
that running both suites in one invocation cannot work, since they share the one dump task.

**Stale execution data.** CI always starts from an empty runner; a checkout does not.
`coverageStage` empties `build/coverage/exec` on every run, but it stages whatever `.exec` files
the build directories hold — including a `build/jacoco/kdsl.exec` from three weeks ago. It prints
every file it stages, so read that list. `./gradlew clean` settles it.

### The Sessions page

`build/coverage/reports/merged/html` is the merged report, and its **Sessions** page
(`jacoco-sessions.html`, linked from the report's header) is the class-level origin view: for each
session, the classes that session executed. It is how you answer
"which test type covers this class" without a line-level report, which is deliberately not planned.

The per-type reports answer the same question from the other end: open `kdsl/html` to see the
codebase as the KDSL suite alone sees it.

### The port caveat

A local KDSL or UI coverage run publishes the JaCoCo agent's `tcpserver` port like every other port
of that stack: **never assume 6300**. Each checkout gets its own slot so that several worktrees can
run at once, and the port is `YONTRACK_KDSL_JACOCO_PORT` in `.yontrack-kdsl/instance.env`. The dump
task reads it from `KdslStack` and needs nothing from you; you need it only if you attach
`jacococli dump` to a stack by hand. See
[`docs/adr/0013-parallel-kdsl-acceptance-stacks.md`](../../docs/adr/0013-parallel-kdsl-acceptance-stacks.md).

## Uploading the stamp icons

One-off admin, not automation. `CIBranchConfig.validations` maps a stamp name to a data-type
configuration and has no image field, and this repository holds no instance-level CasC, so nothing
in `.yontrack/ci.yaml` can carry an icon. It is also not something a workflow should do on every
push: an image is a property of the stamp, not of a build.

The six PNGs are in [`.yontrack/images/validations/`](../../.yontrack/images/validations/), each
named exactly after its stamp. Upload each against the **predefined** validation stamp, so that the
icon applies to every branch where `ci.yaml` creates the stamp.

There is no GraphQL mutation for a stamp image, so this is REST — and the REST API is **not** the
URL you read the UI at. On the self-hosted instance `https://self.dev.yontrack.com` is the Next.js
front end: it proxies `/graphql` and answers `/rest/...` with its own 404 page. `$YONTRACK_BACKEND_URL`
below is the backend.

```bash
STAMP=COVERAGE.UNIT

# The predefined stamp's id. It has to exist first: ci.yaml creates only the branch-level stamps,
# so create the predefined one once from the UI or with `yontrack validation setup`.
ID=$(curl -s -X POST -H "X-Ontrack-Token: $YONTRACK_TOKEN" -H 'Content-Type: application/json' \
  "$YONTRACK_URL/graphql" \
  -d "{\"query\":\"{ predefinedValidationStamps(name: \\\"$STAMP\\\") { id name } }\"}" \
  | jq -r '.data.predefinedValidationStamps[0].id')

# The image goes in the body as raw base64, and is decoded as image/png
base64 -i ".yontrack/images/validations/$STAMP.png" | tr -d '\n' > /tmp/icon.b64
curl -s -X PUT -H "X-Ontrack-Token: $YONTRACK_TOKEN" \
  -H 'Content-Type: text/plain' \
  --data-binary @/tmp/icon.b64 \
  "$YONTRACK_BACKEND_URL/rest/predefinedValidationStamps/$ID/image"
```

`PUT /rest/predefinedValidationStamps/{id}/image` is
`PredefinedValidationStampController.putPredefinedValidationStampImage`. The branch-level
equivalent, if one branch should ever differ, is `PUT /rest/validationStamps/{id}/image`.
[`.yontrack/images/validations/README.md`](../../.yontrack/images/validations/README.md) is the
copy of this procedure that sits beside the images.

## Where the pieces are

| What | Where |
|---|---|
| The JaCoCo version, session IDs and file paths | `buildSrc/src/main/kotlin/net/nemerosa/ontrack/build/Coverage.kt` |
| The agent on the Gradle test JVMs | the `jacoco` block of `build.gradle.kts` |
| The agent in the backend container | `compose/docker-compose-coverage.yml`, `ontrack-kdsl-acceptance/build.gradle.kts` |
| The frontend denominator and reporters | `ontrack-web-core/jest.coverage.js` |
| The exclusion list, and the reports | `scripts/coverage-report.sh` (tests: `scripts/coverage-report-test.sh`) |
| The six sets of figures | `scripts/coverage-metrics.sh`, `scripts/coverage-metrics.py` |
| What may be sent to which stamp | `scripts/coverage-validate.sh` |
| The local entry point | the `coverageReport` task of `build.gradle.kts` |
| The CI job | the `coverage` job of `.github/workflows/ci.yml` |
| The stamps | `.yontrack/ci.yaml` |
