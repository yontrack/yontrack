# Test coverage — design and issue breakdown

Outcome of the grilling session of 2026-09-17 on collecting, displaying and recording test coverage
for Yontrack.

The issues below are created, on milestone 5.5 under `initiative: coverage`. The *Blocked by*
column is recorded as native GitHub dependencies as well as stated here.

| # | Issue | Blocked by |
|---|---|---|
| #1818 | Backend coverage of the unit and integration tests, behind `-Pcoverage` | — |
| #1819 | JaCoCo agent in the KDSL and UI test stacks | — |
| #1820 | Coverage of the frontend Jest tests | — |
| #1821 | Coverage report job: per-type and merged reports, figures, CI artifacts | #1818, #1819, #1820 |
| #1822 | `COVERAGE.*` validation stamps with `metrics` data | #1821 |
| #1823 | Dev guide page `doc/dev-guide/coverage.md` and local merge | #1818, #1819, #1821 |

Three points were settled against the code while the issues were written, and the sections below
are left as the session recorded them — the issues carry the resolution:

- Applying the Gradle `jacoco` plugin *conditionally* on `-Pcoverage` is not workable under the
  STRICT dependency locking of #1752: the configurations it adds would appear and disappear with
  the property, so the lockfiles could only be right for one of the two invocations. #1818 applies
  the plugin unconditionally and gates the agent instead.
- The UI test matrix is **five** legs, not four: `main` has been sharded three ways since the
  session (`.github/workflows/ci.yml`). The `COVERAGE.UI` figure still merges all of them.
- "Every push build on `main` and `release/*`" needs a gate of its own. `setup.full_run` is true on
  any non-Dependabot push, working branches included, so #1821 adds a separate `coverage` output.

## Where we start from

- **No coverage anywhere**: no JaCoCo, Codecov or Sonar configuration in the build or in `.github`.
- Five test levels, each validated on the Yontrack build by `.yontrack/ci.yaml` with the `tests`
  data type, and all five required by BRONZE:

  | Stamp | Tests | CI job | Where the code under test runs |
  |---|---|---|---|
  | `BUILD` | JVM unit tests (`*Test.kt`, ~415 files) | `build` | Gradle test JVM |
  | `UI_UNIT` | Jest (~91 files), `coverageProvider: 'v8'` already set | `build` | Node (Jest) |
  | `INTEGRATION` | `*IT.kt` (~588 files, 35 modules) | `integration`, 5 shards | Gradle test JVM |
  | `KDSL.ACCEPTANCE` | `ACC*.kt` (65 files) | `kdsl`, 2 shards | `nemerosa/ontrack` container |
  | `PLAYWRIGHT` | Playwright (48 specs) | `ui-tests`, 4 legs (main ×2, ldap, oidc) | `nemerosa/ontrack` + `nemerosa/ontrack-ui` containers |

- The backend image is built by Jib with its default entrypoint: only `JAVA_TOOL_OPTIONS` reaches
  the JVM (the compose files' `JAVA_OPTIONS` does not).
- Existing validation data types that fit: `percentage` (one integer, thresholds), `fraction`,
  `metrics` (a map of named decimals, chartable).
- The repository is public.

## Decisions

### Scope

- **Backend code** is measured with **JaCoCo** for the unit, integration, KDSL and UI tests.
- **Frontend code** is measured from the **Jest** tests only.
- **Out of scope:**
  - frontend coverage from the Playwright tests: it needs production source maps in the released
    image, or a separate coverage image;
  - Codecov, SonarQube or any external service;
  - coverage on pull requests;
  - per-test origin ("which test covers this line");
  - a line-level origin report (see *Display*);
  - thresholds and any gating.

### Origin

- Coverage is attributed **per test type**. Modules show up naturally in the JaCoCo reports.
- Every agent run uses a JaCoCo **session ID** naming its type and shard (`unit`, `integration-3`,
  `kdsl-1`, `ui-oidc`…), so the merged report's *Sessions* page shows origin at class level.
- The UI figure merges all four Playwright legs.
- Backend coverage from KDSL and UI tests includes startup, scheduled jobs and the API calls the
  specs make to seed data. **Accepted as is**, with no counter reset after startup.

### Figures

- **Counters:** line coverage is the headline figure, and branch coverage is recorded next to it.
- **Per backend type:** `line`, `branch` and `unique_line`. `unique_line` is the percentage of lines
  covered by this type and by **no other backend type**.
- **Merged backend total:** `line` and `branch`.
- **Jest:** `line` and `branch`. There is no `unique_line`, because no other frontend type exists
  to compare against.
- Values are percentages from 0 to 100, with one decimal.
- **Denominator:** all production modules. Excluded are `ontrack-test-utils`, `ontrack-it-utils`,
  the `ontrack-kdsl*` modules, `ontrack-web-tests`, `ontrack-demo-seed`, `buildSrc` and generated
  code. On the frontend, the same principle means that `collectCoverageFrom` lists all production
  sources, so files no test imports count as 0% (as JaCoCo does for classes).

### Collection (#1818, #1819, #1820)

- **Unit and integration tests (#1818):**
  - use the Gradle `jacoco` plugin, enabled only by `-Pcoverage`;
  - produce one `.exec` file per test task, collected per job or shard as a CI artifact.
- **KDSL and UI tests (#1819):**
  - a CI-only **compose override** mounts the agent jar into the backend container and sets
    `JAVA_TOOL_OPTIONS=-javaagent:...,output=tcpserver`. The released image is unchanged;
  - it applies to the `kdsl`, `kdsl-ldap` and `kdsl-oidc` compose files;
  - before the stack goes down, `jacococli dump` fetches the data. This works however the
    container stops;
  - the same override works locally.
- **Jest (#1820):** `jest --coverage`, producing lcov and a JSON summary, only when coverage is enabled.
- **Report inputs:** the JaCoCo report needs the exact class files that ran. The `build` job
  uploads the production classes and sources, which Jib also packages into the image, as an
  artifact.

### When

- **Every push build on `main` and `release/*`**, the builds CI registers in Yontrack. The
  workflow sets `-Pcoverage` on those builds only.
- **Pull requests collect no coverage.**
- **Overhead:** watched informally through the run times already recorded on the test stamps. No
  back-off rule for now; a nightly run is the fallback if it becomes a problem.

### Display (#1821)

- A new **coverage report job** runs after `integration`, `kdsl` and `ui-tests`, even if they
  failed. It produces:
  - one **JaCoCo HTML report per backend type**, plus a **merged** one with its *Sessions* page;
  - the Jest HTML report.
- The reports are **CI artifacts** with **30-day retention**. The Yontrack validation run
  description links to them.
- Line-level origin (a source view with badges showing which types hit each line) is **not
  planned**. Class-level origin from the Sessions page and the per-type reports are enough for now.

### Recording in Yontrack (#1822)

- Six stamps use the **`metrics`** data type and are declared in `.yontrack/ci.yaml`:

  | Stamp | Metrics |
  |---|---|
  | `COVERAGE.UNIT` | `line`, `branch`, `unique_line` |
  | `COVERAGE.INTEGRATION` | `line`, `branch`, `unique_line` |
  | `COVERAGE.KDSL` | `line`, `branch`, `unique_line` |
  | `COVERAGE.UI` | `line`, `branch`, `unique_line` |
  | `COVERAGE.TOTAL` | `line`, `branch` |
  | `COVERAGE.UI_UNIT` | `line`, `branch` |

- **Icons:** one per stamp and named after it. Each is a 128×128 PNG under 16 KB: a 75% coverage
  ring on a background whose colour identifies the test type. #1822 moved them out of this dated
  directory to [`.yontrack/images/validations/`](../../../.yontrack/images/validations/), beside
  the `ci.yaml` that names the stamps; the README there says how they are uploaded.
- **Record only:** the stamps are PASSED whenever figures exist. They are in **no promotion**, like
  the `SECURITY.*` stamps.
- **Collection failure** (a missing shard or leg, or a failed dump) sends the stamp as **FAILED**,
  with the reason in the description. Partial figures are never sent.
- A later "no regression" rule, failing when coverage drops by more than X points against the
  previous build, needs new code, since no existing data type compares builds. It will be decided
  once a baseline exists.

### Local runs (#1823)

- `./gradlew test integrationTest kdslAcceptanceTest -Pcoverage`, then a merge and report task,
  produces the same reports and figures locally.
- The procedure is documented in `doc/dev-guide/coverage.md`. It is contributor-facing, so there is
  no mkdocs page.

### Definition of done

- **Demo seed:** nothing to add. The change is CI and tooling, not a user-visible feature.
- **Mobile UI:** no impact. No web UI change.
