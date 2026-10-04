# The next-major branch

The next major is developed on a long-lived branch of its own — `v<N+1>` while `main` is still
building N.x — and lands on `main` only at the cutover. 6.0 was built that way on `v6`, which became
`main` at the cutover (#1989). There is no such branch today; `v7` would be the next one.

This page says what that branch is, what has to be set up for it, what the pipeline does
differently on it, and how it ends. It is written from what `v6` did.

## What the branch is

**A second `main`, not a feature branch.** It carries its own `VERSION` file (`7.0`), it is built
by the same `ci.yml`, it goes through the same promotion chain, and it has a deployment environment
of its own. The only thing it does not do is release: `GOLD` is a human gate, and nobody grants it
on the major branch.

Apart from `VERSION`, the pipeline, the environment definition and the versioning rules below
live on `main` and are inherited by the major branch through the regular merges from `main`. That
is deliberate, and section [Keeping it in step](#keeping-it-in-step) says why.

## Versioning

`VersionCalculator` sends a branch matching `v<major>` down the same path as `main`
(`computeMainVersion`): it reads the branch's own `VERSION` file, scans `git tag -l` for
`7.0.<n>`, and answers the next free patch. With no `7.0.*` tag yet, that is `7.0.0`, and `ci.yml`
appends the `-rc-<run>` suffix just as it does on `main`. **A `v7` build is `7.0.0-rc-<run>`.**

That version is the `release` property on the Yontrack build, the durable GHCR tag, and what the
slot's auto-versioning writes into the gitops repository — one identifier across the three, exactly
as on `main`. See [ADR 0006](../../docs/adr/0006-build-identity-through-the-release.md).

The match is anchored and digits-only, in `VersionCalculator` and in `ci.yml` alike:

| Branch              | Version               |
|---------------------|-----------------------|
| `v7`                | `7.0.0-rc-42`         |
| `v7-spring-boot-5`  | `7.0-v7-spring-boot-5-1a2b3c4` |

A branch working *towards* the next major is still a feature branch and keeps the feature version
shape. Feature branches for next-major work are cut from a freshly fetched `origin/v7` and merged
back into it, never into `main`. Their issues say so on a `**Base branch:**` line, and
`/fix-issue` and `/run-initiative` then follow it — both have to learn that base again when the
branch opens, since today they know only `main` and `release/5.5`.

## Setting it up

Everything below lands on **`main`**, and reaches the major branch through the next merge.

### The environment

An environment of its own — `v6.dev.yontrack.com` for 6.0 — defined in `.yontrack/ci.yaml`
alongside the demo and self environments, with a slot on the `yontrack` project admitting **the
major branch at BRONZE**, anchored (`^v7$`): the eligible-builds query uses PostgreSQL's unanchored
`~`, so a bare `v7` would also admit `v7-spring-boot-5`. Deployment is immediate, as on the demo
slot: a `CANDIDATE` workflow moves the pipeline to running, and the `RUNNING` workflow
auto-versions a values file in `yontrack/yontrack-infra-gitops` with auto-approval. ArgoCD does
the rest.

The values file and everything below it — namespace, chart values, DNS, certificate, Keycloak
client — live in `yontrack/yontrack-infra-gitops`, not here. **Until that side exists, the slot
deploys into a PR that nothing consumes.**

### Seeding it

The environment has no data of its own, and the seed is what gives it some. The slot's third node
dispatches [demo-smoke.yml](../../.github/workflows/demo-smoke.yml) with a `target` of its own,
exactly as the demo slot dispatches it for itself: it polls until the deployed version is the
version answering, resets and re-seeds the instance, asserts the seeded dataset over GraphQL, signs
in through Keycloak, and reports `DEMO.SMOKE` on the build. **The environment's data is therefore a
function of the build deployed, not an accumulation** — the same contract as the demo's.

`demo-smoke.yml` resolves everything per-instance from `target`, so the new target adds:

* an option of the `target` input;
* its URL (`vars.V7_URL`), its credentials and a concurrency group of its own, so that its runs
  never queue behind the demo's;
* a `DEMO_SEED_URL_PATTERN` allowing its exact host, since the seed refuses any URL that is not
  `demo.` or localhost.

The slot dispatches it with `branch` spelled out — the workflow's default is `main`, and a 7.0
version would not resolve there — and with `reference: v7`, so that the seed program and the
Playwright spec that run are the ones belonging to the code under test.

**The target needs three repository secrets of its own**, and the workflow fails at the door with a
named error until they exist:

| Secret | What it is |
|---|---|
| `V7_TOKEN` | API token on the instance — the seed deletes and recreates every project with it |
| `V7_USERNAME` | Keycloak user the Playwright sign-in check uses |
| `V7_PASSWORD` | its password |

Nothing is shared with the demo: two instances, two databases, two Keycloak realms.

### SILVER

A `custom.configs` block conditioned on `^v7$` in `.yontrack/ci.yaml` adds `DEMO.SMOKE` to
`SILVER`, exactly as the `^main$` one does, and replaces the `On SILVER` message so that it names
the right instance. Scoped to the one branch rather than to `^v\d+$`: it names an environment.

## The pipeline on the major branch

Same `ci.yml`, same `.yontrack/ci.yaml`, and the branch conditions do the rest.

| | `main` | `v7` |
|---|---|---|
| Builds, tests, promotions to BRONZE | yes | yes |
| Test coverage (`COVERAGE.*`) | yes | yes |
| Deployed on BRONZE | demo.dev.yontrack.com | its own environment |
| Smoke-tested and seeded (`DEMO.SMOKE`) | yes | yes |
| What SILVER means | deployed and verified | deployed and verified |
| Real GitLab / Bitbucket pipeline tests | yes | **no** |
| DAST scanned | yes | no — but the CasC reload runs on both |
| Released by `release.yml` | yes | no — nobody grants GOLD on the major branch |

**No third-party pipeline tests:** the real GitLab and Bitbucket Cloud runs are scoped `^main$` and
metered against free minutes in the fixture namespaces. Extending them to the major branch would
roughly double that spend for no extra signal — they exercise integrations, not the code under
change.

The DAST row needs no exception in the workflow. `reloadCasc` re-applies whatever CasC the
*instance* carries, not a file sent from the runner, so on another instance it restores that
instance's own declaration after the seed; and the project-role assertion that follows passes on an
instance that declares no DAST group at all.

**Where its CI reports.** Every branch reports to self.dev.yontrack.com, which runs the current
major's last release. When the next major builds something the current one cannot take, it needs a
detour of its own. 6.0's security findings were one: 5.x could not take them, so `v6` mirrored them
onto v6.dev as well (#1869). That mirror is dormant since the cutover, and #1875 removes it.

## Keeping it in step

`main` keeps moving while the major branch is open, so the major branch takes regular merges from
`main`. Two things make that cheap, and both are the reason pipeline changes land on `main`:

* **Few conflicting spots.** `VERSION` differs by design — take the major branch's side, every
  time. Anything else landed on the major branch alone conflicts there too, so keep that list
  short.
* **The slots are synchronised, not merged.** `EnvironmentsInjection.defineSlots` uses
  `syncForward` over the slots of the project: a slot the incoming configuration does not mention
  is **deleted**. A slot declared only on the major branch would therefore be torn down by the next
  `main` build and rebuilt by the next major-branch build, endlessly. Environments are safe
  (`keepEnvironments = true`), slots are not. Whatever the environments block says has to say the
  same thing on every branch that builds.

## Deprecations and removals

The next-major branch is where the previous major's deprecations are removed.
[ADR 0018](../../docs/adr/0018-deprecation-and-removal-across-majors.md) is the policy; on this
branch it comes down to:

* **Deprecated in N, removed in N+1.** Every item marked `Removed in V7` goes on `v7`. An item that
  cannot go is re-marked `Removed in V8` and listed on the migration page as carried over, never
  kept silently.
* **New deprecations are for the next major**: `Removed in V8. Use X instead. See #NNNN`, a runtime
  warning and a line on the migration page for an external item.
* **The upgrade floor is the last Flyway version of the previous major's `.0`** — `V68` for 6.0.
  A 6.0 instance starts on any 5.x database and on no older one. So the cleanup may delete the
  code-based data conversions present in 5.0.0 — startup migrations, readers of legacy stored
  formats — and keeps those added after 5.0.0 until V7, unless a Flyway migration converts the
  data instead. Aliases on input only (API, CasC) are not data conversions: they follow the
  deprecation rule. The floor is documented on the migration page, not checked at startup: the
  next major's page states its own.
* The marker test, `DeprecationMarkersRepositoryTest` in `ontrack-model`, checks the markers and
  the migration page on every `./gradlew test`. Its baseline
  (`ontrack-model/src/test/resources/deprecation/markers-baseline.txt`) lists the items which did
  not conform when the policy was adopted; each removal deletes its lines, and none is added.

## The cutover

When the major is feature-complete, its branch becomes `main`. This is how 6.0 did it (#1989,
decided in [the cutover grilling](../../docs/grilling/2026-10-v6-cutover/README.md)).

### The order

1. **Freeze.** No agent session running, no `/run-initiative` in flight, every open branch based on
   `v6` merged into it or abandoned, and `main` merged into `v6` — so that
   `git merge-base --is-ancestor origin/main origin/v6` succeeds.
2. **Cut the maintenance branch** from `main`'s HEAD: `release/5.5`. It keeps the previous major
   alive, and [Patch releases](patch-release.md) has the exception that allows it. The git branch of
   the previous patch line, `release/5.4`, is deleted; its Yontrack branch is kept.
3. **Fast-forward `main` to `v6`, and then to the cutover commit**, in one push, so CI runs once.
4. **Retire `v6`**: delete the branch, its secrets and variable, its environment in the gitops
   repository, and its Yontrack branch; rewrite the `**Base branch:** v6` lines of the open issues.
5. **Release the first alpha**: `6.0-alpha.0`. See [Releasing](release.md#pre-releases).

### The cutover commit

One commit, prepared on a branch off `v6` and reviewed before the cutover (#1988). It:

* rewrites every use of `v6` as a base branch — `CLAUDE.md`, the agent docs, `/fix-issue` and
  `/run-initiative` — to `main`, with `release/5.5` and its cherry-picks for 5.x fixes;
* removes the v6 environment from the pipeline: its environment, slot and `^v6$` block in
  `.yontrack/ci.yaml`, and its target in `demo-smoke.yml`;
* points the nightly search performance (`search-perf.yml`) at `main`;
* sets `VERSION` to `6.0-alpha`, so that `main` builds `6.0-alpha.N`;
* moves the gate below from the merge to the GA release;
* amends the [patch rules](patch-release.md) for the last minor of the previous major.

### The gate

**The X.0.0 GA release** — not the merge — waits on a gate: **no `Removed in V6` marker left and
an empty baseline.**

```bash
# Prints nothing - the marker test, the deprecation tests and their fixtures excepted, which name
# the format itself
git grep -n "Removed in V6" -- ':!*.md' ':!*.graphql' \
    ':!ontrack-model/src/test/java/net/nemerosa/ontrack/model/deprecation' \
    ':!ontrack-model/src/test/resources/deprecation' \
    ':!ontrack-service/src/test/java/net/nemerosa/ontrack/service/deprecation/DeprecatedConfigurationPropertiesCheckTest.kt' \
    ':!ontrack-service/src/test/java/net/nemerosa/ontrack/service/deprecation/DeprecationServiceImplTest.kt' \
    ':!ontrack-ui-graphql/src/test/java/net/nemerosa/ontrack/graphql/deprecation/DeprecationInstrumentationTest.kt'
# Prints 0
grep -v '^#' ontrack-model/src/test/resources/deprecation/markers-baseline.txt | grep -c .
```

It moved from the merge to the release because the merge had nothing to protect: what the gate
guards is what users receive, and only a release delivers anything to them. The pre-releases ship
meanwhile, and [Releasing](release.md#from-alpha-to-ga) lists the gate among the criteria for GA.

Every V5 deprecation is then either gone or explicitly carried over to V7. The marker test then
moves one major on — `Removed in V7` and `Removed in V8` — for the next major's branch.

### What the merge does not do

**Move the security stamps back to self.dev** (#1875). The findings mirror stops with the `v6`
branch, but self.dev only runs 6.0 once 6.0.0 is released, so the switch cannot happen at the
merge. The last step of `security-images` in `ci.yml`, and of the `report` job in `codeql.yml`,
holds the line: on `main` and `release/*`, a 6.x build warns while self.dev still runs 5.x, and
fails once it runs 6.x, until #1875 has landed.

## See also

* [Minor cutover](minor-cutover.md) — moving `main` from one minor to the next
* [Patch releases](patch-release.md) — fixing the previous minor from a `release/X.Y` branch
* [Releasing](release.md) — GOLD publishes, RELEASE records that it did
