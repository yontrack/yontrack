# The next-major branch

The next major is developed on a long-lived branch of its own — `v6` while `main` is still
building 5.x — and lands on `main` only at the cutover. This page says what that branch is, what
the pipeline does differently on it, and what it costs.

## What `v6` is

**A second `main`, not a feature branch.** It carries its own `VERSION` file (`6.0`), it is built
by the same `ci.yml`, it goes through the same promotion chain, and it has a deployment environment
of its own. The only thing it does not do is release: `GOLD` is a human gate, and nobody grants it
on `v6`.

The only file that differs between `main` and `v6` is `VERSION`. Everything else — the pipeline,
the environment definition, the versioning rules below — lives on `main` and is inherited by `v6`
through the regular merges from `main`. That is deliberate, and section
[Keeping `v6` in step](#keeping-v6-in-step) says why.

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
shape. Feature branches for `v6` work are cut from `v6` and merged back into it, never into `main`.

## The pipeline on `v6`

Same `ci.yml`, same `.yontrack/ci.yaml`, and the branch conditions do the rest.

| | `main` | `v6` |
|---|---|---|
| Builds, tests, promotions to BRONZE | yes | yes |
| Test coverage (`COVERAGE.*`) | yes | yes |
| Deployed on BRONZE | demo.dev.yontrack.com | v6.dev.yontrack.com |
| Demo smoke test (`DEMO.SMOKE`) | yes | **no** |
| What SILVER means | deployed to the demo and verified there | the build is green and deployed |
| Real GitLab / Bitbucket pipeline tests | yes | **no** |
| Released by `release.yml` | yes | no — nobody grants GOLD on `v6` |

Two of those are worth spelling out.

**No smoke test.** `demo-smoke.yml` resets and re-seeds the demo's data as part of verifying it.
`v6` is a development environment whose data is left as it is, so the slot's `RUNNING` workflow
stops at the deployment: `auto-versioning`, then `slot-pipeline-deployed`, and no `smoke` node.

Nothing then reports `DEMO.SMOKE` on a `v6` build, and nothing asks for it: only the `^main$`
block in `.yontrack/ci.yaml` adds `DEMO.SMOKE` to SILVER, so on `v6` SILVER keeps the defaults'
weaker meaning — BRONZE alone, "the build is green". This is the same inversion the release
branches rely on, and for the same reason: `PromotionLevelConfiguration.merge` is additive only, so
the defaults declare the weakest form and each branch kind adds what it can satisfy (#1702).

**No third-party pipeline tests.** The real GitLab and Bitbucket Cloud runs are scoped `^main$` and
metered against free minutes in the fixture namespaces. Extending them to `v6` would roughly double
that spend for no extra signal — they exercise integrations, not the code under change.

## The environment

`v6.dev.yontrack.com`, defined in `.yontrack/ci.yaml` alongside the demo and self environments, with
a slot on the `yontrack` project admitting **the `v6` branch at BRONZE**. Deployment is immediate:
a `CANDIDATE` workflow moves the pipeline to running, and the `RUNNING` workflow auto-versions
`yontrack-helmfile.d/values/yontrack-v6/yontrack.yaml` in `yontrack/yontrack-infra-gitops` with
auto-approval. ArgoCD does the rest.

The values file and everything below it — namespace, chart values, DNS, certificate, Keycloak
client — live in `yontrack/yontrack-infra-gitops`, not here. **Until that side exists, the slot
deploys into a PR that nothing consumes.**

## Keeping `v6` in step

`main` keeps moving while `v6` is open, so `v6` takes regular merges from `main`. Two things make
that cheap, and both are the reason the pipeline changes live on `main`:

* **One conflicting file.** `VERSION` differs by design — take `v6`'s side, every time. Nothing
  else should conflict.
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

## See also

* [Minor cutover](minor-cutover.md) — moving `main` from one minor to the next
* [Patch releases](patch-release.md) — fixing the previous minor from a `release/X.Y` branch
* [Releasing](release.md) — GOLD publishes, RELEASE records that it did
