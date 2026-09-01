# Demo smoke test

The demo slot marks itself deployed when the gitops PR merges. That is well before ArgoCD has
synced and the pods are serving, so "deployed" on its own says nothing about whether the demo
works. `.github/workflows/demo-smoke.yml` closes that gap and reports `DEMO.SMOKE` on the
build. No promotion requires that stamp yet - `SILVER`, which will, is issue #1667.

## What it does

| # | Step | Why |
|---|------|-----|
| 1 | Resolve the build the version names | `yontrack validate --build` takes the build *name*, and the slot can only pass the version |
| 2 | Poll until the demo reports that version | The whole deployment contract: the version asked for is the version answering |
| 3 | Reset and seed | The demo's state is a function of the build - see [Demo seed and reset](demo-seed.md) |
| 4 | Check the seeded dataset over GraphQL | The seed ran and left something behind |
| 5 | Check the UI signs in and renders | Keycloak's realm, and the UI pod reaching the backend pod |
| 6 | Report `DEMO.SMOKE` | Whatever happened, including "the demo never came up" |

Step 2 is the valuable one. Everything that can go wrong between the merged PR and a serving
pod - ArgoCD not synced, image not pullable, old pod still up, new pod crash-looping - looks
the same from outside: the demo answers with the previous version, or with nothing. One poll
with one deadline covers them all.

## Deliberately thin

The heavy suites already ran for `BRONZE`. Re-running them here would test the deployment
rather than the code, and a fat smoke suite becomes the flaky thing that blocks releases. What
is left is what only a real deployment can break, and the Playwright leg runs with `retries: 0`
for the same reason: a demo that only works on the second try is a demo that is broken.

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

Dispatched by the demo slot's `RUNNING` workflow through the `github-workflow` notification
channel (`.yontrack/ci.yaml`), which passes only the version. `project` and `branch` come from
the workflow's own defaults, `yontrack` and `main`, which is what the slot is scoped to anyway.

It can also be dispatched by hand from the Actions tab with a version.

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

## The pieces

| Piece | Role |
|---|---|
| `.github/workflows/demo-smoke.yml` | The steps, and the `DEMO.SMOKE` report |
| `scripts/demo-smoke.sh` | Build resolution, the poll, the GraphQL assertion |
| `scripts/demo-smoke-test.sh` | Its tests, against a stubbed `curl` and a stubbed CLI |
| `scripts/yontrack-build.sh` | Build lookup by version, shared with `scripts/demo-deploy.sh` |
| `ontrack-web-tests/demo/demo.spec.js` | The browser leg |
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
