# Demo seed and reset

The demo environment's state is a function of the build, not an accumulation. An
accumulating demo drifts until nobody knows what state it is in, so every deployment resets
it: the seed program deletes everything and recreates the dataset.

The reset goes **through the Yontrack API**, not the database. No Postgres hooks, no PVC
churn, and no dependency on ArgoCD sync timing — a Helm pre-upgrade hook would fire on
*every* sync, so an unrelated values tweak would silently destroy the demo.

Settings stay covered by CasC and users live in Keycloak, so projects, environments, labels,
estates and dashboards are the only things the seed has to reset. What they have in common is that
they outlive a project deletion: a label is global and carried by several projects, so
deleting every project leaves every label behind.

The token it runs with needs admin-level rights: it deletes projects, manages environments
and labels (`LabelManagement`) and shares a dashboard (`DashboardSharing`). It also generates a
second token for its own account, `ci-demo`, which the audit trail demo is created through, and
revokes it at the end of the reset - see [The audit trail](#the-audit-trail) below.

## Running it

```bash
export YONTRACK_URL=https://demo.dev.yontrack.com
export YONTRACK_TOKEN=<a token on that instance>
./gradlew :ontrack-demo-seed:run
```

Against the local dev stack, read the port out of `.yontrack-dev/instance.env` (see
[DEVELOPMENT.md](../../DEVELOPMENT.md)) and point `YONTRACK_URL` at it.

`installDist` produces a standalone launcher, which is what a workflow uses so it does not
carry a Gradle build with it:

```bash
./gradlew :ontrack-demo-seed:installDist
ontrack-demo-seed/build/install/ontrack-demo-seed/bin/ontrack-demo-seed
```

The launcher runs on whatever `java` it finds, so it needs a JDK 25 on the `PATH` or a
`JAVA_HOME` pointing at one — the same JDK the rest of the build requires.

| Variable                  | Default              | Meaning                                            |
|---------------------------|----------------------|----------------------------------------------------|
| `YONTRACK_URL`            | _required_           | Instance to reset                                  |
| `YONTRACK_TOKEN`          | _required_           | API token on that instance                         |
| `DEMO_SEED_REPOSITORY`    | `.`                  | Checkout the changelog is read from                |
| `DEMO_SEED_CHANGELOG_MAX` | `25`                 | How many commits the changelog project shows       |
| `DEMO_SEED_URL_PATTERN`   | demo or local hosts  | Which instances the program is allowed to wipe     |

### The URL guard

The program deletes **every** project on whatever it is pointed at, and a production URL
differs from the demo's by a few characters. It therefore refuses to run against anything
that is not a `demo.` host or a local instance. Naming another instance is possible through
`DEMO_SEED_URL_PATTERN`, which is the difference between a decision and an accident.

### What the target instance must have

The dataset is not self-contained: it can only reference features the target instance
actually runs. Two of them are off by default, are on automatically in the `dev` profile —
so the local dev stack needs nothing — and must be switched on explicitly anywhere else,
which for the demo means an environment variable in its Helm values:

| Feature | Property | Helm value |
|---------|----------|------------|
| **Simulated gate** node executor (`executorId: mock`), which the CANARY promotion workflow is built on | `ontrack.config.extension.workflows.mock.enabled` | `ONTRACK_CONFIG_EXTENSION_WORKFLOWS_MOCK_ENABLED` |
| **Mock SCM**, which the change log on `petclinic` is read from | `ontrack.config.extension.scm.mock.enabled` | `ONTRACK_CONFIG_EXTENSION_SCM_MOCK_ENABLED` |

A licensed feature is needed as well, for the same reason:

| Feature | Licensed feature | Where it comes from |
|---------|------------------|---------------------|
| **Native scanner formats**, which the SARIF code scan of `petclinic-billing` is posted in | `extension.findings.native-formats` | The development licence enables it; a production licence has to include it |
| **Delivery scorecard**, which the two estates are | `extension.scorecard` | Same |
| **Environments**, which the "Demo production" estate reads up to | `extension.environments` | Same |

Unlike the two properties, a missing licence is caught before the reset: the seed reads the
licence of the instance through `licenseInfo` and stops with "Nothing was deleted" when a
feature is not enabled, as it does when the mock SCM is off.

The audit trail is the exception: what it needs does **not** stop the reset. Each piece is asked
before anything is deleted, and when the instance cannot offer it the part of the demo needing it
is left out, with a `Leaving out ...` line in the log saying why:

| What | Needed by | Without it |
|------|-----------|------------|
| The licensed feature `extension.audit-trail` | `audit-trail-demo` and `audit-trail-tampered` | Both projects are left out, with their slots and deployments |
| An evidence storage in state `OK` (`auditTrailStorageState`) - the `ontrack.extension.audit-trail.storage.*` properties, `auditTrail.storage.*` in the chart | The evidence of both projects | The evidence is left out; the rest of both projects is seeded |
| The demonstration tampering switch, `ontrack.extension.audit-trail.demo-tampering.enabled` (`ONTRACK_EXTENSION_AUDITTRAIL_DEMOTAMPERING_ENABLED`) | `audit-trail-tampered` | The project is left out |
| `/rest/extension/audit-trail/` routed to the backend | The evidence, which is uploaded as `multipart/form-data`, and the tampering, which is a REST end point | Both are left out, as above |

The switch is seen from outside through the permanent error message it raises, and the REST route
by asking `GET /rest/extension/audit-trail/keys` for the public keys: anything but a JSON array is
an ingress sending the call to the Next UI.

**The deployed instances do not route it yet.** The chart's ingress
(`charts/yontrack/templates/ingress.yaml` in `yontrack/yontrack-chart`) sends `/graphql` and
`/hook` to the backend and everything else to the Next UI, so on v6.dev the seed leaves out the
evidence and `audit-trail-tampered` whatever the storage and the switch say. What is missing is a
`/rest/extension/audit-trail` path to the backend service in that template - which is also what a
pipeline posting evidence from outside the cluster needs, demo or not. It is a change of the chart,
not of this repository.

Never turn the switch on for an instance tracking real deliveries: while it is on, every trail of
the instance proves nothing, and every page says so. The local dev stack leaves it off too; to see
the tampered project locally, restart the backend with it:

```bash
ONTRACK_EXTENSION_AUDITTRAIL_DEMOTAMPERING_ENABLED=true scripts/dev-stack.sh restart backend
```

`scripts/dev-stack.sh` passes its environment on to the backend, and the dev stack has its MinIO,
so the whole audit trail demo is then seeded.

A third one is optional, and only on a long-lived instance:

| Feature | Property | Helm value |
|---------|----------|------------|
| **Persistent mock SCM**, which keeps the seeded commits across a backend restart | `ontrack.config.extension.scm.mock.persistent` | `ONTRACK_CONFIG_EXTENSION_SCM_MOCK_PERSISTENT` |

Without it the reset still works; what is lost is everything between one reset and the next
restart — see [the change log](#the-change-log) below.

Without the first, the reset fails partway through, after the deletions, with
`Workflow node executor ID "mock" not found`. Without the second it fails the same way,
when the seed posts the demo's commits to an endpoint that is not there. The dataset
validation cannot catch either, because it checks the dataset against Yontrack's rules, not
against the target's configuration.

Neither belongs on an instance tracking real deliveries: one lets a workflow report a gate
as passed without anything having been verified, the other lets a project claim an SCM that
answers with whatever anyone posted to it.

### The changelog project

One project is seeded from the real changelog since the last release — every commit between
the last release tag and `HEAD`, one build each — so the demo does not go stale between the
times someone remembers to extend the curated dataset.

That needs tags and history, so a workflow running the seed has to check out with
`fetch-depth: 0`. When git cannot answer — no tags, no history, no git — the seed prints a
warning and carries on with an empty changelog project. That is deliberate: a demo one
project poorer beats a reset that refuses to run. The cost is that it degrades quietly, so
a smoke test asserting the demo is fresh will not catch a checkout misconfigured this way;
the warning in the log is the only signal.

### The change log

`petclinic` is the only project with an SCM, and it is the **mock** one: the seed registers
the commits behind the demo's change logs itself, over REST, rather than pointing the project
at a real repository. A real Git configuration would trade a self-contained reset for one
depending on credentials and network egress.

The commit subjects are conventional-commit ones on purpose. The semantic change log groups
commits by their type and `SemanticChangelogRenderingServiceImpl` drops every commit carrying
none, so a project writing subjects any other way demonstrates an empty semantic view — which
is why the change log is not on the `yontrack` project, whose subjects come from this
repository's own history and are overwhelmingly `#1234 Some message`.

**The demo's change log survives a backend restart only where the mock SCM is persistent.**
`MockSCMExtension` keeps its repositories in a `mutableMapOf` on the bean, and unless
`ontrack.config.extension.scm.mock.persistent` is set it keeps nothing else: a pod restart
between two resets then leaves the project pointing at a repository the mock SCM no longer
has. The build commit properties are in the database and survive, so the change log is not
empty — it **fails**, with `Repository petclinic not found`, and so do the commit and issue
info panels, until the next reset. Setting the property makes the mock SCM write each
repository to the generic storage as it changes and read them back on start, which is what a
demonstration instance wants; the local dev stack sets it already, so
`scripts/dev-stack.sh restart backend` keeps whatever the seed registered. What it writes is
an implementation detail of the mock SCM, not a storage format to depend on: it is there so
that a demo does not go blank, and a future version is free to ignore whatever it finds.

Because those repositories outlive the projects the reset deletes, the seed **empties the
repository** before registering anything in it. Commit ids are derived from the branch and the
position of the commit on it, so a second run registering on top of the first would give every
commit a different id.

The seed registers all of it **over GraphQL** — `mockScmRegisterCommit`,
`mockScmRegisterIssue`, `mockScmDeleteRepository` — and not over the REST endpoints of
`MockSCMController` beside them. A deployed instance is reached through an ingress that routes
`/graphql` and `/hook` to the backend and everything else to the Next UI, so a
`POST /extension/scm/mock/commit` against the demo answers 404 from the UI whatever the backend
is configured with. That is what broke the first demo smoke run after this landed. The REST
endpoints stay for what only ever runs inside the cluster: the Playwright fixture in
`ontrack-web-tests/ontrack/extensions/scm/scm.js` and the files, branches and pull requests the
acceptance tests use.

Being reachable from outside also means the mutations are not content with an authenticated
user: each one checks a global function, so the token the seed runs with has to be an
administrator's — which it already had to be, to delete projects.

## Adding to the demo

A feature is not done until the demo seed shows it — the definition of done in `CLAUDE.md`
states the rule; this section says how to satisfy it.

The dataset is declarative, in
[`DemoContent`](../../ontrack-demo-seed/src/main/java/net/nemerosa/ontrack/demo/seed/DemoContent.kt).
Adding to the demo means adding entries there, not steps to a procedure.

Two rules keep the demo reproducible, and
`DemoSeedTest.running it twice in a row yields the same demo state` enforces them:

- **Nothing varies between runs.** No counters, no random data, no wall-clock names. Build
  creation times are the one exception and are expressed relative to the run
  (`BuildCreation.DaysAgo`), so the demo always reads as recent work.
- **Anything named has a fixed identity.** The dashboard and its widgets carry hard-coded
  UUIDs, because Yontrack rejects a second dashboard of the same name unless the UUID
  matches — a fresh UUID would make the second run fail.

`DemoDatasetValidation` checks the dataset against Yontrack's own rules — legal names, no
promotion to a level the branch does not declare, no link to a build that is never created,
no deployment a slot's admission rules would refuse — **before** the reset deletes anything,
and reports every problem at once. Destructive by design must not mean blank on failure.

### Security findings are scans, not statuses

`petclinic-billing` carries the security findings (#1867): a HIGH reported by the first builds of
`main`, fixed there by a dependency bump and still exposed on `release-2.3`; a CRITICAL under an
acceptance which expires 90 days after the reset; and a code scan in SARIF with a HIGH accepted by a
suppression. Its two stamps are `security-findings` ones, and a build does not say what status they
reached: it declares **scans** (`BuildSpec.scans`), each a list of findings, and the server computes
the status from them, as it does for any CI posting through `validateBuildWithFindings`.

A scan declares its findings, not its report. `FindingsReports` renders the report in the format
the scan names - the neutral format, or SARIF - so that one finding cannot drift apart between two
hand-written reports. Two consequences are checked before the reset:

- **SARIF cannot say when an acceptance expires**, so a SARIF scan declaring an expiry is refused
  rather than sent as an acceptance that never lapses. The expiring CRITICAL is in the neutral
  format for that reason.
- **A findings stamp takes no status**: a `ValidationSpec` on one is refused.

An acceptance's expiry is relative to the reset (`AcceptanceSpec.expiresInDays`), like a build's
creation time, so the demo never shows an acceptance which lapsed only because the dataset got old.

Scans are dated on the build's ladder like validations, above them and below the promotions, which
is what `validateBuildWithFindings` takes a `dateTime` for: the observations, the exposure and the
resolution of a finding all follow the time of the run, and a finding "first seen seconds ago" on a
build of last week is the wrong history. For the same reason the release branch is declared - and
so scanned - before `main`: its builds are the oldest, and a finding is first seen by the first
scan *ingested*, whatever the dates of the later ones.

The project has no SCM, so no branch model, and every branch counts for the state of a finding in
the project. That is what keeps the HIGH open for the project while it is resolved on `main`.

### A build's history is a ladder, not an instant

A build carries a creation time from the dataset; its validation runs and its promotion runs are
dated *by the seed*, one hour apart, climbing from that creation time. The validations take the
lower steps and the promotions climb on top of them, because a validation is what grants the
promotions naming it: a run dated after them reads as the stamp having run hours after the
promotion it granted (#1718).

Two things bound the ladder. It is squeezed into whatever time the build actually has behind it —
the newest build of the dataset is hours old, and an hour per step would date its top rungs in the
future, which reads as a defect in Yontrack rather than in the dataset. And on a branch with an auto
promotion, the promotion runs are still *created* before the validation runs, whatever their times
say: `AutoPromotionEventListener` promotes a build the moment a run completes the set a level names,
stamping that promotion with the time of the call, so seeding the runs first would add a second
same-level promotion dated at the reset. Everywhere else the validations are created first, as a
pipeline does it: the trail of a build records its changes in the order they are made, and a trail
reading "promoted to GOLD, then validated" is the wrong story (#1970).

`DemoSeedTest.nothing of the demo is dated after the reset which created it` pins the bounds for
builds, promotions and validations alike.

### A name matching nothing is a typo here, even where the product allows it

Some of what the dataset names is not checked by the server at all. A promotion dependency
and a slot admission rule both name their target by name, with nothing behind the name, so
Yontrack accepts a promotion that requires a level the branch does not have — it is what the
delivery map draws as an *unresolved checkpoint*.

`validate` refuses one anyway. Curated content is read as a demonstration, and nobody looking
at the demo can tell a deliberate dangling name from a mistake. The exception is deliberate
and marked as such: the `petclinic-ui` production slot exists precisely to show what a broken
admission rule looks like, and it is the only one.

### The delivery scorecard reads a history

A scorecard reads ninety days, and the rest of the demo has three weeks. `petclinic-visits` is the
project which has the ninety days: fifteen releases, about one a week, each built, tested on a `tests` stamp,
promoted to GOLD a few hours later and deployed to production the day after. Its own project rather
than more builds on `petclinic`, for the reason `petclinic-billing` is one: `petclinic`'s builds are
curated readings of the delivery map, and a dozen more would bury them.

Two estates read it, over labels declared for them (`DemoDataset.estates`): "Demo products", up to
GOLD, over `portfolio:product`, and "Demo production", up to the `production` environment, over
`runs-in:production`. They overlap on `petclinic` and `petclinic-visits`, so both scorecards have two
estate columns which do not say the same thing - a lead time in hours up to GOLD and in days up to
production. The targets are set so that each estate has readings meeting them and readings missing
them, and the flakiness of the tests has none. `DemoContent.estates` lists which reading of which
project does what, and every one of them is a build or a deployment of the dataset: 1.1.0 failing
its tests is the time to restore up to GOLD, 1.2.1 is the flaky build, 1.3.0 failing in production
and 1.3.1 restoring it the next morning are the time to restore in production.

Three things make it work:

- **Everything is dated.** A test run goes through `Build.validateWithData`, the one mutation taking
  the data of a run *and* a date - the typed `validateBuildByIdWithTests` stamps the run with the
  moment of the call. A deployment dated with `DeploymentSpec.at` is created, started and ended a
  quarter of an hour apart through the backdated pipeline mutations; one without is run at the reset,
  as before.
- **A slot's deployments are dated in order.** The server refuses a pipeline starting before the
  latest start of its slot, and a deployment at the reset is the latest start there is: a dated one
  after it would fail half-way through the reset. `validate` refuses both orders before anything is
  deleted. The same goes for a workflow on such a slot, which fires at the moment of the reset and
  can move a pipeline: `petclinic-visits`' slot has none.
- **The seed ends by computing the scorecards.** The readings are computed by a daily job, so a fresh
  demo would read "not computed" until the next night. The last step recomputes every project, which
  covers every set it is in, the estates' included - and waits for each.

The reset deletes the estates **first**: the server refuses to delete a label an estate selects its
projects by.

Only today is computed. The engine computes the snapshot of the day and nothing before it, so the
sparklines of the project scorecard page read "Not enough daily snapshots for a trend yet" on a fresh
demo, and fill in one day at a time on an instance which is not reset daily. Backfilling past days is
the ledger's question (`docs/grilling/2026-09-scorecard/README.md`, *What this hands to the ledger*),
not the seed's.

### The security readings read the findings projects

The security readings of the scorecard (#1912) read the scans of `petclinic-billing`,
`petclinic-visits` and `petclinic`, against what each estate expects (`EstateSpec.security`): "Demo
products" a dependency and a code scan within a week, a week for a CRITICAL and two for a HIGH; "Demo
production" a dependency scan, with the freshness of the settings. What each reading shows is a scan
of the dataset:

- **Maturity at every rung, and both routes to gating.** `petclinic-billing` is at 3 by a scan which
  failed: build 311's dependency scan fails on a CRITICAL nobody accepted, and that build is not
  promoted. `petclinic-visits` is at 3 by policy (#1982), the route teams should aim for: its SILVER
  is granted by an auto promotion requiring its `SECURITY.DEPENDENCIES` stamp, and no scan of it ever
  failed. The ladder is cumulative, so that reads 3 in "Demo production" and with no estate, where
  its fresh dependency scan covers it, and still 1 in "Demo products", which expects a code scan too.
  `petclinic` scans its dependencies on `main` and gates on nothing: 2 in "Demo production", 1 in
  "Demo products". `petclinic-ui` is at 0 - `SECURITY.SCAN` is a plain stamp, not a scan.
- **Remediation time.** `petclinic-billing` fixes a HIGH on `release-2.3` in eleven days and that
  CRITICAL on `main` in three, each reported on one branch only, so resolved for the project as
  soon as that branch fixes it: a median of seven days, within the target. `petclinic` takes eight
  days, within it too, and `petclinic-visits` fourteen days to bump spring-webmvc, and misses it.
- **Overdue findings.** `CVE-2024-38816`, still open on `release-2.3` sixteen days after its first
  scan, is the one HIGH past its fourteen days. The accepted findings are counted apart.
- **The fan-out.** `CVE-2024-38816` is reported by the three projects of "Demo products":
  `petclinic-billing` still exposes it on `release-2.3`; `petclinic-visits` reports it on
  spring-webflux under an acceptance and on spring-webmvc until a bump; `petclinic` reports it on
  spring-webmvc on `main` - its only scanned branch - until the bump of common-library to 3.2.1 in
  1.4.3, so the project is resolved as a whole. Searched there, it reads *exposed in 1, accepted in
  1, resolved in 1*, and it opens the ranked list of the tab: no finding is open in more projects,
  and none of the others is above MEDIUM.

A finding of a project is keyed by its scanner, external ID and location, which is why the CVE on
spring-webflux and on spring-webmvc are two findings of `petclinic-visits`. And a finding is first
seen by the first scan *ingested*: each finding the readings time is reported on one branch only, in
the order its builds are seeded, so that its first observation and its resolution are the dates of
its builds. `DemoSecurityReadingsSeedTest` pins all of it - the rung of each project in each estate,
the fan-out summary and the head of the ranked list are read off the dataset there; the readings
themselves are the server's, and are checked by seeding the dev stack.

An auto promotion requiring a scan is held to the rule [below](#auto-promotion-has-to-reproduce-the-dataset-not-add-to-it):
`petclinic-visits`' 1.6.1 and 1.6.2 satisfy it and declare SILVER, and 1.5.1 and 1.6.0, whose scan
warns on the open HIGH, were promoted by hand. `DemoSeedTest` reads a scan's status off its findings
and a test run's off its failed tests for that, as the server would.

The estate view and its fan-out are in the release-notes screenshots
(`ontrack-web-tests/screenshots/catalogue.js`, `estate`, `estate-ranked-findings` and
`estate-fanout`).

### The long tables are a project of their own

A sticky table header (#1932) only shows on a table which overflows what it is shown in, and the
curated projects are too small for most of theirs to. `petclinic-e2e` is there for its size: thirty
nightly builds and thirty end-to-end suites on `main`, so its branch matrix scrolls down once more
builds are loaded and sideways on a wide screen; `E2E.SMOKE` run on every build, so its history
scrolls; every suite run on the latest build, so the validations of its build page overflow their
section; and fourteen feature branches, listed with `main` by the "End-to-end suites" widget of the
demo dashboard, which overflows too.

Its own project for the reason `petclinic-visits` is one: that many builds and stamps on a curated
project would bury its readings. It sits in its own section of `DemoContent`, to be trimmed there.
`DemoTablesSeedTest` pins what each table needs.

### Auto promotion has to reproduce the dataset, not add to it

`PromotionLevelSpec.autoPromotion` configures a real server behaviour: the build reaching
everything it names *is promoted*, by the server, at the moment the last validation lands. A
rule the dataset's own builds satisfy without declaring the promotion therefore adds a
promotion run nobody wrote down, stamped with the time of the reset rather than the build's
own, and the counts every view shows stop matching the dataset.

`DemoSeedTest` pins this: every build satisfying an auto promotion must already declare it.
That is what keeps `fullPromotions` off the changelog project, whose builds stop at BRONZE
with both stamps green and would otherwise promote themselves to SILVER on the next reset.

### The build which fails is load-bearing

`petclinic/maintenance` ends on a build whose `BUILD` validation FAILED and which is promoted
nowhere. It is the demo's only build that arrived somewhere and failed, and it is there for the
delivery map: a promotion level names a build which *was promoted* and a slot one which *was
deployed*, so a validation stamp is the only checkpoint able to show a build which got there and
did not succeed. Without it the documentation explains a reading nothing on screen shows.

A checkpoint names the **latest** build to have run its stamp, which is what makes this fragile:
adding a greener build after the failing one takes the reading away without touching anything that
looks related. `DemoSeedTest.the demo shows a validation stamp whose latest build arrived and
failed` fails when that happens.

It also declares one validation rather than four, which is the point rather than an omission: a
build that does not compile never runs its tests, so the aggregate checkpoint and every promotion
level stay a build behind while the branch head shows a failure.

### Slot workflows are configured after the deployments

`SlotSpec.workflows` is walked in a pass of its own, once every deployment has run — unlike
`admissionRules`, which are added with the slot and *before* them.

A slot workflow on `CANDIDATE` or `RUNNING` is a hard gate: the server refuses to start, and to
finish, a deployment whose check is not ok. Configuring one before the deployments would therefore
leave every deployment in the dataset waiting on a workflow — and a workflow runs asynchronously, so
the seed would either have to poll for a gate it configured itself or fail at random. A reset that
is destructive by design cannot be flaky.

The cost is that the demo's slot workflows read *Not started* on the delivery map. That is a real
state and worth showing — a gate nobody has run is the interesting case — and workflows which have
**run** are shown on the promotion side instead, by the CANARY pair, one passing and one failing.

### Deployments are a sequence, not a slot property

`DemoDataset.deployments` is an ordered list, run after every slot exists, rather than a
`deployed` field on each slot. An `environment` admission rule asks what *another* slot is
holding at that moment, so a demo where a build passes through staging on its way to
production, and staging then takes another build, is a sequence no per-slot field can
express. `InMemoryDemoTarget` enforces the admission rules as the server does, so an order
the server would refuse fails in the unit tests rather than half-way through a real reset.

### The audit trail

Two projects show the audit trail (#1970), and `DemoAuditTrailSeedTest` pins what each shows:

- **`audit-trail-demo`**: build `121` (2.4.0) is created, given its properties, validated with its
  evidence, promoted and linked through the `ci-demo` token (`BuildSpec.token`), which is what its
  trail shows as the actor of those entries. A person - the account the seed runs as - then gives
  the failed `UNIT.TESTS` run the status FIXED with a comment (`ValidationSpec.statusChanges`;
  Yontrack does not allow FAILED to PASSED, FIXED is the passed status a failed run can be given),
  deletes a log attached by mistake (`EvidenceSpec.deleted`), and deploys the build to staging, then
  to production, overriding the change approval there (`DeploymentSpec.overrides`). Last,
  `LEGACY.LINT` is deleted (`ValidationStampSpec.deleted`), and both builds of the project record
  `validation.deleted` with the reason `cascade/validation-stamp-deleted`.
- **`audit-trail-tampered`**: build `7`, whose fourth entry - the FAILED scan - is rewritten as PASSED
  once everything is seeded (`BuildSpec.tampering`). Its verification breaks at seq 4.

The evidence files are in `src/main/resources/demo/evidence`: small, written or generated once and
committed, so that their digests are the same on every reset. One of each kind the evidence table
handles - a PDF and a PNG shown inline, a CycloneDX SBOM in JSON and a JUnit summary in plain text,
an HTML ZAP report which the server only ever serves as a download - and no real data in any of
them.

The entries are numbered by the server, in the order the seed makes its calls - and the backdating
of a build is a call of its own, so every build's trail starts with `build.created` then
`build.updated`. A tampering therefore names the type of entry it expects as well as its seq, and
the seed refuses to rewrite anything else. `InMemoryDemoTarget` writes the trails in the same order,
which is what lets the unit tests check that seq 4 is the scan.

`ProjectSpec.requires` and `DemoTarget.unavailable` are what leave a project out on an instance
which cannot offer what it needs - see [What the target instance must have](#what-the-target-instance-must-have).
A project which may be left out is not linked to from another one: `validate` refuses it.

## How it is put together

| Piece               | Role                                                                  |
|---------------------|-----------------------------------------------------------------------|
| `DemoContent`       | What the demo shows, as data. The file a feature adds itself to.       |
| `DemoDataset`       | The vocabulary `DemoContent` is written in.                           |
| `DemoSeed`          | Deletes everything, then walks the dataset.                            |
| `DemoTarget`        | The Yontrack API, as far as the seed needs it.                        |
| `KdslDemoTarget`    | The one implementation that talks to a real instance, through the KDSL. |
| `GitChangelogSource`| Commits since the last release tag.                                    |
| `DemoSeedConfig`    | Environment variables, and the URL guard.                              |
| `DemoDatasetValidation` | Rejects a bad dataset before the reset deletes anything.           |
| `FindingsReports`   | Renders the report of a security scan, in the neutral format or in SARIF. |
| `EvidenceFiles`     | The files the dataset attaches as evidence, from the module's resources. |

The `DemoTarget` seam is what makes the acceptance criterion testable: the unit tests run
the whole seed twice against an in-memory instance and compare the two states, with no
server involved.

## What it does not do

- **The shared dashboard is not selected for visitors.** Yontrack only ever selects a
  dashboard for the account that saved it, so a visitor lands on the built-in dashboard and
  picks `Yontrack demo` from the list.
- **Another account's private dashboards survive.** The reset deletes every dashboard the
  seeding account can see: the shared ones and its own. Yontrack does not expose anyone
  else's private dashboards, so those are out of reach.
- **The change log does not survive a backend restart unless the mock SCM is persistent.**
  See above: without `ontrack.config.extension.scm.mock.persistent` the mock SCM holds its
  commits in memory only, and the change log fails rather than reads empty once they are gone.
- **Commits are not backdated.** The mock SCM stamps a commit with the time it is registered
  and its REST endpoint takes no time, so a change log between two builds dated a week apart
  shows commits dated within the same second of the reset. Validation runs used to share this
  limitation and no longer do (#1718); the mock SCM's endpoint still takes no time, so fixing
  this one needs a server-side change of its own.
- **Favourites belong to the seeding account.** The dataset marks a couple of projects and
  branches as favourites so that the mobile UI — whose home screen is the current user's
  favourites and nothing else — is populated rather than blank on a phone (#1720). A
  favourite is per user and Yontrack has no shared scope for one, unlike a dashboard, so what
  the seed marks is marked for whatever account `YONTRACK_TOKEN` belongs to. If visitors
  browse the demo as a different account from the one that seeds it, their mobile home is
  still empty — and shows the empty state, which at least says what favourites are.
- **The scorecard has no history.** See [above](#the-delivery-scorecard-reads-a-history): the
  readings of the day are computed, not the ones before it.
- **`KdslDemoTarget` has no automated test.** The seed is destructive by definition, so it
  cannot share an instance with the acceptance suite. Changes to it are verified by running
  the program against a throwaway instance — the local dev stack does fine — twice, and
  diffing the resulting state.
