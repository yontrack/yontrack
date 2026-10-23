# v5.dev.yontrack.com — the 5.5 maintenance environment

Outcome of the grilling session of 2026-10-23, held once for the whole `initiative: v5-dev`.

Since the [6.0 cutover](../2026-10-v6-cutover/README.md), no environment runs 5.5: a 5.5 patch could
only be checked by reading its diff, and a 5.x issue could not be reproduced anywhere.
v5.dev.yontrack.com is that environment.

In scope: yontrack/yontrack#2119 (`release/5.5`), #2120 (`main`), #2121 (the human step) and
yontrack/yontrack-infra-gitops#281 (the `yontrack-v5` environment).

## Decisions

| # | Decision | Choice |
|---|---|---|
| 1 | Purpose | An **internal** 5.5 environment: it checks 5.5 patch candidates before `GOLD` and lets 5.x issues be reproduced. Not a public demo, so no customer accounts and no DAST. |
| 2 | Eligibility, not deployment | A Yontrack environment `v5.dev.yontrack.com`, with a slot on project `yontrack` admitting `^release-5\.5$` (the Yontrack branch name) at `BRONZE`. **No `CANDIDATE` workflow**, as on self.dev (#2074): a build only becomes eligible, and someone starts the deployment by hand. |
| 3 | Where environments and slots are declared | **From `main` only.** `project.environments` moves out of `configuration.defaults` into the `^main$` custom block. `release/5.5` and feature branches stop declaring it, so their builds never resync the slots. |
| 4 | Image tag | The **`rc-version` meta-info item** (`${build.meta?name=rc-version&error=true}`), as on the demo (#1999), backported to `release/5.5`'s `ci.yml`. Builds registered before the backport cannot be deployed. |
| 5 | Chart | The latest 5.x yontrack-chart. One backend replica, no `zeroDowntimeUpgrades`, no audit trail, no MCP. |
| 6 | Seed and smoke test | On **every deployment**. The slot dispatches `demo-smoke.yml` on `release/5.5` with `target: v5`, so the 5.5 seed runs against 5.5. Data is reset on every deployment, as on the demo. |
| 7 | `SILVER` on `release/5.5` | **Requires `DEMO.SMOKE`.** A 5.5 patch cannot reach `GOLD` until it has been deployed to v5 and smoke-tested there. |
| 8 | Auth | The chart's bundled Keycloak, with the Yontrack admin only. Secrets under Vault `yontrack/dev/v5`. The host is public, behind the Keycloak login. |
| 9 | Licence | One of its own. |
| 10 | Images | GHCR (private), through the same `ghcr-pull-secret` mechanism as the demo (Vault `apps/ghcr`). |
| 11 | Postgres | No backup, and excluded from the CNPG backup alert. |
| 12 | Notification | Slack `#notifications` when a deployment finishes. |
| 13 | Lifecycle | Retired at 5.5 end of life. A v6.dev for 6.x maintenance, once v7 reaches `main`, follows the same pattern and is **out of scope**. |

## Why decision 3 matters beyond v5

`EnvironmentsInjection.defineSlots` resyncs the slots of every project it lists with `syncForward`,
which deletes any slot of those projects the incoming configuration does not mention. Declared in
the defaults, the environments block was applied by every branch that builds, each from its own
copy of `.yontrack/ci.yaml`: `release/5.5`
brought the v6 slot back and widened self.dev's admission again, and would have deleted the v5 slot
on its next build. In the `^main$` block, an extension that no other branch's configuration mentions
never reaches `EnvironmentsCIConfigExtension.configure`, and those branches leave the slots as `main`
last set them. `RepositoryCIConfigurationIT` reads the file and pins it.

## Where it is written down

- [Patch releases](../../../doc/dev-guide/patch-release.md) — deploying a 5.5 patch to v5.dev before `GOLD`
- [The next-major branch](../../../doc/dev-guide/major-branch.md) — environments declared from `main`
  only, and the previous major's maintenance environment
