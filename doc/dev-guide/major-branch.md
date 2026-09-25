# The next-major branch

The next major is developed on a long-lived branch of its own — `v6` while `main` is still
building 5.x — and lands on `main` only at the cutover. This page says what that branch is, what
the pipeline does differently on it, and what it costs.

## What `v6` is

**A second `main`, not a feature branch.** It carries its own `VERSION` file (`6.0`), it is built
by the same `ci.yml`, it goes through the same promotion chain, and it has a deployment environment
of its own. The only thing it does not do is release: `GOLD` is a human gate, and nobody grants it
on `v6`.

Apart from `VERSION`, the pipeline, the environment definition and the versioning rules below
live on `main` and are inherited by `v6` through the regular merges from `main`. That is
deliberate, and section [Keeping `v6` in step](#keeping-v6-in-step) says why. The one exception is
the security findings mirror, which exists on `v6` alone — see
[The security findings mirror](#the-security-findings-mirror).

## Versioning

`VersionCalculator` sends a branch matching `v<major>` down the same path as `main`
(`computeMainVersion`): it reads the branch's own `VERSION` file, scans `git tag -l` for
`6.0.<n>`, and answers the next free patch. With no `6.0.*` tag yet, that is `6.0.0`, and `ci.yml`
appends the `-rc-<run>` suffix just as it does on `main`. **A `v6` build is `6.0.0-rc-<run>`.**

That version is the `release` property on the Yontrack build, the durable GHCR tag, and what the
slot's auto-versioning writes into the gitops repository — one identifier across the three, exactly
as on `main`. See [ADR 0006](../../docs/adr/0006-build-identity-through-the-release.md).

The match is anchored and digits-only, in `VersionCalculator` and in `ci.yml` alike:

| Branch              | Version               |
|---------------------|-----------------------|
| `v6`                | `6.0.0-rc-42`         |
| `v6-spring-boot-4`  | `6.0-v6-spring-boot-4-1a2b3c4` |

A branch working *towards* the next major is still a feature branch and keeps the feature version
shape. Feature branches for `v6` work are cut from a freshly fetched `origin/v6` and merged back into it,
never into `main`.

## The pipeline on `v6`

Same `ci.yml`, same `.yontrack/ci.yaml`, and the branch conditions do the rest.

| | `main` | `v6` |
|---|---|---|
| Builds, tests, promotions to BRONZE | yes | yes |
| Test coverage (`COVERAGE.*`) | yes | yes |
| Deployed on BRONZE | demo.dev.yontrack.com | v6.dev.yontrack.com |
| Smoke-tested and seeded (`DEMO.SMOKE`) | yes | yes |
| What SILVER means | deployed and verified | deployed and verified |
| Real GitLab / Bitbucket pipeline tests | yes | **no** |
| DAST scanned | yes | no — but the CasC reload runs on both |
| Security findings mirrored onto v6.dev | no | yes — see below |
| Released by `release.yml` | yes | no — nobody grants GOLD on `v6` |

One of those is worth spelling out. **No third-party pipeline tests:** the real GitLab and Bitbucket
Cloud runs are scoped `^main$` and metered against free minutes in the fixture namespaces.
Extending them to `v6` would roughly double that spend for no extra signal — they exercise
integrations, not the code under change.

The DAST row needs no exception in the workflow. `reloadCasc` re-applies whatever CasC the
*instance* carries, not a file sent from the runner, so on `v6` it restores `v6`'s own declaration
after the seed; and the project-role assertion that follows passes on an instance that declares no
DAST group at all.

### The security findings mirror

self.dev.yontrack.com, where every branch's CI reports, runs 5.x until 6.0 is released, and so
cannot take the security findings `v6` is building. `v6`'s CI therefore reports CHML to self.dev
like every other branch, and **in addition** sends the reports themselves to v6.dev, which runs 6.0
with the licence for the native formats (#1869):

| Stamp | Workflow | Mirrored as |
|---|---|---|
| `SECURITY.IMAGE.BACKEND`, `SECURITY.IMAGE.UI` | `ci.yml` | the Trivy JSON report, accepted vulnerabilities included (`--show-suppressed`) |
| `SECURITY.CODE` | `codeql.yml` | the open and dismissed CodeQL alerts, in the neutral `findings` format, dismissals as acceptances |

The other three security stamps never run on `v6`. The findings land in the `yontrack-ci` project
of v6.dev, which the seed's reset spares (`DemoSeed.CI_MIRROR_PROJECT`), with the thresholds of
`.yontrack/ci.yaml`. The mirror never fails a build: self.dev stays the instance of record.
Everything is in `scripts/security-findings-mirror.sh`, with `V6_URL` and `V6_TOKEN` as the seed
uses them.

## The environment

`v6.dev.yontrack.com`, defined in `.yontrack/ci.yaml` alongside the demo and self environments, with
a slot on the `yontrack` project admitting **the `v6` branch at BRONZE**. Deployment is immediate:
a `CANDIDATE` workflow moves the pipeline to running, and the `RUNNING` workflow auto-versions
`yontrack-helmfile.d/values/yontrack-v6/yontrack.yaml` in `yontrack/yontrack-infra-gitops` with
auto-approval. ArgoCD does the rest.

The values file and everything below it — namespace, chart values, DNS, certificate, Keycloak
client — live in `yontrack/yontrack-infra-gitops`, not here. **Until that side exists, the slot
deploys into a PR that nothing consumes.**

### Seeding it

`v6` has no data of its own, and the seed is what gives it some. The slot's third node dispatches
[demo-smoke.yml](../../.github/workflows/demo-smoke.yml) with `target: v6`, exactly as the demo slot
dispatches it for itself: it polls until the deployed version is the version answering, resets and
re-seeds the instance, asserts the seeded dataset over GraphQL, signs in through Keycloak, and
reports `DEMO.SMOKE` on the build. **The environment's data is therefore a function of the build
deployed, not an accumulation** — the same contract as the demo's.

One workflow serves both instances, parameterised by `target`. It resolves that into the URL
(`vars.V6_URL`, default `https://v6.dev.yontrack.com`), the credentials, and the concurrency group
(`v6-instance`, so a v6 run never queues behind a demo run). It is dispatched with `reference: v6`
so the seed program and the Playwright spec that run are the ones belonging to the code under test.

**`v6` needs three repository secrets of its own**, and the workflow fails at the door with a named
error until they exist:

| Secret | What it is |
|---|---|
| `V6_TOKEN` | API token on the v6 instance — the seed deletes and recreates every project with it, `yontrack-ci` apart, where the findings mirror writes with it too |
| `V6_USERNAME` | Keycloak user the Playwright sign-in check uses |
| `V6_PASSWORD` | its password |

Nothing is shared with the demo: two instances, two databases, two Keycloak realms.

## Keeping `v6` in step

`main` keeps moving while `v6` is open, so `v6` takes regular merges from `main`. Two things make
that cheap, and both are the reason pipeline changes land on `main`:

* **Few conflicting spots.** `VERSION` differs by design — take `v6`'s side, every time. The only
  other ones are those of the findings mirror, the one pipeline change landed on `v6` alone
  (#1869): `security-findings-mirror.sh`, its steps at the end of `security-images` in `ci.yml`
  and of `report` in `codeql.yml`, the CLI version of both, `--show-suppressed` in
  `security-image-scan.sh` and the seed sparing `yontrack-ci`. A merge from `main` touching those
  conflicts there — keep both sides, and `v6`'s CLI version when `main`'s is older than 5.8.0.
* **The slots are synchronised, not merged.** `EnvironmentsInjection.defineSlots` uses
  `syncForward` over the slots of the project: a slot the incoming configuration does not mention
  is **deleted**. A `v6` slot declared only on `v6` would therefore be torn down by the next `main`
  build and rebuilt by the next `v6` build, endlessly. Environments are safe (`keepEnvironments =
  true`), slots are not. Whatever the environments block says has to say the same thing on every
  branch that builds.

## The cutover

When 6.0 is ready, `v6` becomes `main`. That is the reverse of
[Minor cutover](minor-cutover.md) and has not been written down yet — it is one merge, one `VERSION`
bump, and the retirement of the `v6` environment.

And one thing the merge does not do by itself: **move the security stamps back to self.dev**
(#1875). The mirror stops with the `v6` branch, but self.dev only runs 6.0 once 6.0.0 is released,
so the switch cannot happen at the merge. The last step of `security-images` in `ci.yml`, and of
the `report` job in `codeql.yml`, holds the line: on `main` and `release/*`, a 6.x build warns
while self.dev still runs 5.x, and fails once it runs 6.x, until #1875 has landed.

And one thing the merge does not do by itself either: **point the nightly search performance at
`main`**. `search-perf.yml` checks out `v6` (#1887); at the merge, switch `SEARCH_PERF_REF` and the
`ref` input's default to `main`, or the nightly measures a branch that no longer moves.

## See also

* [Minor cutover](minor-cutover.md) — moving `main` from one minor to the next
* [Patch releases](patch-release.md) — fixing the previous minor from a `release/X.Y` branch
* [Releasing](release.md) — GOLD publishes, RELEASE records that it did
