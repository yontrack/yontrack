# Demo smoke test

A slot marks itself deployed when the gitops PR merges. That is well before ArgoCD has
synced and the pods are serving, so "deployed" on its own says nothing about whether the instance
works. `.github/workflows/demo-smoke.yml` closes that gap and reports `DEMO.SMOKE` on the
build.

`SILVER` requires that stamp, on top of `BRONZE`, so a green run of this workflow is what
promotes the build to `SILVER` - see [`SILVER`](#silver) below.

## Two instances, one workflow

The `target` input picks the instance: `demo` (the default) or `v6`, the next-major environment -
see [the next-major branch](major-branch.md). Everything the workflow does is the same for both;
only three things are resolved from `target`:

| | `demo` | `v6` |
|---|---|---|
| URL | `vars.DEMO_URL`, default `https://demo.dev.yontrack.com` | `vars.V6_URL`, default `https://v6.dev.yontrack.com` |
| Credentials | `secrets.DEMO_TOKEN` / `DEMO_USERNAME` / `DEMO_PASSWORD` | `secrets.V6_TOKEN` / `V6_USERNAME` / `V6_PASSWORD` |
| Concurrency group | `demo-instance`, shared with the passive DAST scan | `v6-instance`, this workflow alone |

Two instances, two databases, two Keycloak realms: nothing is shared. A missing secret is an
empty string, so an unconfigured target would otherwise fall through to the demo's credentials
and fail deep inside the poll; `Check the target's credentials` stops that at the door, right
after the correlation artifact is published and before anything talks to the instance.

On `v6` the seed is the point rather than the verification: the environment has no data of its
own, and this workflow is what provisions it on every deployment.

## What it does

| # | Step | Why |
|---|------|-----|
| 1 | Resolve the build the version names | `yontrack validate --build` takes the build *name*, and the slot can only pass the version |
| 2 | Poll until the instance reports that version | The whole deployment contract: the version asked for is the version answering |
| 3 | Reset and seed | The instance's state is a function of the build - see [Demo seed and reset](demo-seed.md) |
| 4 | Re-apply the CasC | The seed deleted the projects, and their permissions with them - see [Re-applying the CasC](#re-applying-the-casc) below |
| 5 | Check the seeded dataset over GraphQL | The seed ran and left something behind, security findings included |
| 6 | Check the UI signs in and renders, and shows the security findings | Keycloak's realm, the UI pod reaching the backend pod, and the four views of the findings |
| 7 | Report `DEMO.SMOKE` | Whatever happened, including "the demo never came up" |

Step 2 is the valuable one. Everything that can go wrong between the merged PR and a serving
pod - ArgoCD not synced, image not pullable, old pod still up, new pod crash-looping - looks
the same from outside: the demo answers with the previous version, or with nothing. One poll
with one deadline covers them all.

## Re-applying the CasC

The demo's configuration as code gives the DAST scanner group `DAST Project` the `PARTICIPANT`
role on the seeded project, through CasC `project-permissions`. The seed deletes and recreates
every project, and a project's permissions go with the project - so from the first reset onwards
`scan-project` would hold no project at all and the cross-project authorization scan of the DAST
track would test nothing (issue #1768).

`scripts/demo-smoke.sh casc` runs right after the seed, and does two things:

1. `mutation { reloadCasc { errors { message } } }`, which re-applies the demo's CasC config map;
2. reads `accountGroups(name: "DAST Project") { authorizedProjects { project { name } role { id } } }`
   back, and fails unless the group holds `PARTICIPANT` on the seeded project.

The second half is not ceremony: a reload that ran but left `project-permissions` unapplied is
indistinguishable from a good one on the mutation's side, and the permission is the thing this
step exists for.

Why `reloadCasc` and not the REST endpoints: `PUT /extension/casc/reload` and
`POST /extension/casc/upload` exist, but the chart's ingress routes only `/graphql` and `/hook`
to the backend, so neither is reachable from outside the cluster. `reloadCasc` needs no enabling
flag - `ontrack.casc.reloading` only creates a *scheduled* job and `ontrack.casc.upload` only
opens the upload endpoint - and is gated on the global `GlobalSettings` function, which
`DEMO_TOKEN` already carries.

CasC is declarative, so the reload is idempotent by construction. It has to be: this workflow
runs on every `main` `BRONZE` deployment.

The canonical declaration of the scanner roles is
[`security/dast/casc.yaml`](../../security/dast/casc.yaml); the demo runs a copy of it, in
`yontrack-helmfile.d/values/yontrack-demo/casc/casc.yaml` in `yontrack/yontrack-infra-gitops`.
An instance that declares no such group - anything but the demo - is not a failure: there is no
DAST CasC there and nothing to restore, and the step says so and passes.

`DEMO_CASC_GROUP` and `DEMO_CASC_ROLE` override the group and role that are checked.

## `SILVER`

`.yontrack/ci.yaml` declares `SILVER` as `promotions: [BRONZE]` in its defaults, and adds
`validations: [DEMO.SMOKE]` in the `^main$` and `^v6$` blocks - the branches that have an
instance to be verified on. It needs no workflow node of its own, and nothing dispatches it:
auto-promotion grants it as soon as both prerequisites hold, and `DEMO.SMOKE` is always the
later of the two.

```
BRONZE -> the slot admits the build -> deploy -> demo-smoke.yml -> DEMO.SMOKE -> SILVER
```

So `BRONZE` means "the build is green" and `SILVER` means "it is running on its environment and
was verified there" - which is what someone needs before deciding on `GOLD`. A Slack notification
on `SILVER` to `#notifications` carries the instance's URL and the version and says exactly that.
The `BRONZE` notification stays: knowing a build is green is useful on its own.

A release branch is the exception: it is never deployed, so nothing adds `DEMO.SMOKE` there and
`SILVER` keeps the defaults' weaker meaning, "the build is green" (#1702). The merge is additive
only, which is why the defaults carry the weakest form and each branch kind adds what it can
satisfy.

Two things this does *not* do:

- `GOLD` is not configured yet. It arrives with the new publication path (issue #1673).
- `RELEASE` still requires `BRONZE`, not `SILVER`. Making the release train depend on the demo
  pipeline is a deliberate switch that lands with #1673 too - until then a broken demo must
  not be able to block a release.

A failed `DEMO.SMOKE` needs no handling of its own either. Auto-promotion looks at the *last*
run of a stamp, so a `FAILED` one simply does not grant `SILVER`, and the existing
"On validation error" subscription already puts it in `#notifications`.

## Deliberately thin

The heavy suites already ran for `BRONZE`. Re-running them here would test the deployment
rather than the code, and a fat smoke suite becomes the flaky thing that blocks releases. What
is left is what only a real deployment can break, and the Playwright leg runs with `retries: 0`
for the same reason: a demo that only works on the second try is a demo that is broken.

## The security findings

The seed gives `petclinic-billing` its security findings (see
[Demo seed and reset](demo-seed.md#security-findings-are-scans-not-statuses)), and both legs
check them, each the way it reads the demo:

- `scripts/demo-smoke.sh assert` reads, in one query, the project's findings summary - some open,
  some accepted, as the Security section shows them - and the search result of
  `CVE-2024-38816`, which must be on `petclinic-billing` and exposed on a branch.
  `DEMO_FINDINGS_PROJECT` and `DEMO_FINDINGS_CVE` override the two.
- The browser leg's second test walks what a visitor would: the Security section of the project
  page, its *All findings* link to the findings page, the CVE's link to the finding page - exposed
  on `release-2.3`, resolved on `main` - and the search of the CVE, back to the finding page.

A seed which posted its scans but lost their findings would pass the first check and leave an
empty Security section, so the counts are asserted non-zero rather than merely present.

## Everything goes through `/graphql`

The chart's ingress routes only `/graphql` and `/hook` to the backend; `/` goes to the Next UI.
`/rest/*` is therefore **not** reachable from outside the cluster - it lands on the UI and
answers 404 - so the version is read with `query { info { version { full } } }` rather than
from `/rest/info`. That is not a workaround: the same call exercises the ingress, the backend
and authentication, which is more than the REST endpoint would have proved.

## Reporting on failure

The validation step runs under `!cancelled()`, not `success()`, and treats a *skipped* step as
a failure. A demo that never came up has to land a `FAILED` `DEMO.SMOKE` on the build: an
absent stamp is indistinguishable from "not deployed yet", which is exactly how a broken demo
times out silently.

The one thing that cannot be reported is a failure to resolve the build - there is then no
build to report against. It runs first, before any of the smoke steps, so that failure reads as
"Yontrack does not know this version" rather than as a broken demo.

## Running it

Dispatched by a slot's `RUNNING` workflow through the `github-workflow` notification channel
(`.yontrack/ci.yaml`).

The demo slot passes only the version: `project` and `branch` come from the workflow's own
defaults, `yontrack` and `main`, which is what that slot is scoped to anyway.

The v6 slot passes `version`, `target: v6` and `branch: v6` - the branch has to be spelled out
because the default is `main`, and a v6 version would not resolve there. It also dispatches with
`reference: v6` rather than `main`, so the seed program and the Playwright spec that run are the
ones belonging to the code under test; the workflow file itself is the same on both branches.

It can also be dispatched by hand from the Actions tab with a version and a target.

### The `id` input

The workflow declares an `id` input it never reads. That is the `github-workflow` channel's
correlation contract, and it is not optional: GitHub's API gives no way to learn the run ID of a
dispatch, so Yontrack always sends an `id` input and then finds the run by an artifact named
`inputs-<id>.properties`. Two consequences, both of which this workflow got wrong on its first
attempt:

- **A workflow that does not declare `id` cannot be dispatched at all.** GitHub rejects any
  undeclared input, and the notification fails with
  `422 Unexpected inputs provided: ["id"]` - which surfaces on the slot's workflow node, not
  here, because the run never starts.
- **The artifact has to be published quickly.** Yontrack polls the run's artifacts 10 times at
  10-second intervals. The correlation steps therefore run *before* the checkout, since
  `fetch-depth: 0` on this repository does not fit inside 100 seconds. They need no checkout:
  the file is empty and only its name carries the ID.

See [the channel's documentation](../../ontrack-docs/docs/content/integrations/notifications/github-workflow.md).

| Secret / variable | Meaning |
|---|---|
| `vars.YONTRACK_URL`, `secrets.YONTRACK_TOKEN` | The Yontrack instance the validation is reported to |
| `vars.DEMO_URL` | The demo, defaulting to `https://demo.dev.yontrack.com` |
| `secrets.DEMO_TOKEN` | API token on the demo - admin-level, since the seed deletes every project |
| `secrets.DEMO_USERNAME`, `secrets.DEMO_PASSWORD` | Keycloak credentials the browser signs in with |
| `vars.V6_URL` | The v6 instance, defaulting to `https://v6.dev.yontrack.com` |
| `secrets.V6_TOKEN` | API token on it - admin-level, for the same reason |
| `secrets.V6_USERNAME`, `secrets.V6_PASSWORD` | Its Keycloak credentials |

## The pieces

| Piece | Role |
|---|---|
| `.github/workflows/demo-smoke.yml` | The steps, and the `DEMO.SMOKE` report |
| `scripts/demo-smoke.sh` | Build resolution, the poll, the CasC reload, the GraphQL assertion |
| `scripts/demo-smoke-test.sh` | Its tests, against a stubbed `curl` and a stubbed CLI |
| `scripts/yontrack-build.sh` | Build lookup by version, shared with `scripts/demo-deploy.sh` |
| `ontrack-web-tests/demo/demo.spec.js` | The browser leg: the sign-in, and the security findings |
| `ontrack-web-tests/playwright.demo.config.js` | Its configuration - a separate `testDir`, so the regular `PLAYWRIGHT` suite does not pick the spec up |

`scripts/demo-smoke-test.sh` is run by hand, like `scripts/demo-deploy-test.sh`:

```bash
scripts/demo-smoke-test.sh
```

The browser leg can be pointed at any instance, which is how it is developed:

```bash
cd ontrack-web-tests
DEMO_URL=http://localhost:3000 DEMO_USERNAME=admin DEMO_PASSWORD=admin \
  DEMO_SEEDED_PROJECT=petclinic npm run test-demo
```

The spec defaults none of those. A default `DEMO_URL` would point a local run at the live demo,
which is the one instance a developer never means to be driving by accident.

Against the local dev stack, read the UI port out of `.yontrack-dev/instance.env` - see
[DEVELOPMENT.md](../../DEVELOPMENT.md) - and seed it first.
