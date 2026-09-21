# Demo seed and reset

The demo environment's state is a function of the build, not an accumulation. An
accumulating demo drifts until nobody knows what state it is in, so every deployment resets
it: the seed program deletes everything and recreates the dataset.

The reset goes **through the Yontrack API**, not the database. No Postgres hooks, no PVC
churn, and no dependency on ArgoCD sync timing — a Helm pre-upgrade hook would fire on
*every* sync, so an unrelated values tweak would silently destroy the demo.

Settings stay covered by CasC and users live in Keycloak, so projects, environments, labels
and dashboards are the only things the seed has to reset. What they have in common is that
they outlive a project deletion: a label is global and carried by several projects, so
deleting every project leaves every label behind.

The token it runs with needs admin-level rights: it deletes projects, manages environments
and labels (`LabelManagement`) and shares a dashboard (`DashboardSharing`).

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

### A build's history is a ladder, not an instant

A build carries a creation time from the dataset; its validation runs and its promotion runs are
dated *by the seed*, one hour apart, climbing from that creation time. The validations take the
lower steps and the promotions climb on top of them, because a validation is what grants the
promotions naming it: a run dated after them reads as the stamp having run hours after the
promotion it granted (#1718).

Two things bound the ladder. It is squeezed into whatever time the build actually has behind it —
the newest build of the dataset is hours old, and an hour per step would date its top rungs in the
future, which reads as a defect in Yontrack rather than in the dataset. And the promotion runs are
still *created* before the validation runs, whatever their times say: `AutoPromotionEventListener`
promotes a build the moment a run completes the set a level names, stamping that promotion with the
time of the call, so seeding the runs first would add a second same-level promotion dated at the
reset.

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
- **`KdslDemoTarget` has no automated test.** The seed is destructive by definition, so it
  cannot share an instance with the acceptance suite. Changes to it are verified by running
  the program against a throwaway instance — the local dev stack does fine — twice, and
  diffing the resulting state.
