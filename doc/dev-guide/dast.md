# Passive DAST scan of the demo

*Introduced by [#1765](https://github.com/yontrack/yontrack/issues/1765), part of
`initiative: security-scans`. The design is [`docs/grilling/2026-09-dast.md`](../../docs/grilling/2026-09-dast.md);
what the scanners read is [`security/dast/`](../../security/dast/README.md).*

Dynamic application security testing: scanning a **running** Yontrack over HTTP, as opposed to the
supply-chain, SAST and secret scans that read the code and the images. The passive scan runs OWASP
ZAP, graphql-cop and Nuclei against the public demo instance and records their findings, added up,
on the deployed build as the CHML stamp `SECURITY.DAST`.

Report only. `SECURITY.DAST` is in no promotion and nothing in `ci.yml` depends on this workflow.

## What runs, and when

`.github/workflows/dast-passive.yml`:

| Trigger | Why |
|---|---|
| `workflow_run` on **Demo smoke**, success | a new BRONZE build is live and the demo has just been reset and seeded — the one moment its state is known |
| `schedule`, Mondays 03:17 UTC | new ZAP rules appear in weeks when Yontrack does not change |
| `workflow_dispatch` | on demand |

It shares the concurrency group **`demo-instance`** with `demo-smoke.yml`. That is not
housekeeping: the seed deletes and recreates every project, and a reset starting under a running
crawl would have the scanner walking an instance being deleted around it. Anything else that
touches the demo joins that group.

## What it does

1. **Derives a query-only GraphQL schema**, the one ZAP is fed.
2. **Logs in as the three scanner roles** (#1769) — see below — and, with the same authenticated
   query, asks the demo which version it is running.
3. **Resolves that version back to a Yontrack build** — `scripts/demo-smoke.sh resolve`, the
   function `demo-smoke.yml` uses, so `SECURITY.DAST` and `DEMO.SMOKE` land on the same build by
   construction. The CLI is installed directly rather than through
   `ontrack-github-actions-cli-config`, which would register a build as a side effect.
4. **Checks that the management port is not reachable.** If it answers, the run stops: an exposed
   actuator is an incident, not a MEDIUM to add to a tally.
5. **Runs ZAP** from a digest-pinned container: once over the UI, unauthenticated
   (`security/dast/zap/passive.yaml`), then once over `/graphql` per scanner role
   (`security/dast/zap/passive-api.yaml`, rendered with that role's token). After every pass it
   **proves no mutation was sent**, from ZAP's own record of every request, and after every API
   pass that **no request was answered 401**.
6. **Runs graphql-cop** (#1766) against `/graphql`, once per scanner role, every test but the one
   that sends a mutation — checked in its source first, and proved from each pass's output
   afterwards.
7. **Runs Nuclei** (#1766) against the demo, unauthenticated, with the templates tagged `exposure`,
   `misconfig`, `springboot`, `spring`, `keycloak`, `nextjs` and `default-login`, none tagged
   `intrusive`, `dos`, `fuzz` or `bruteforce`, HTTP only, ten requests a second —
   `security/dast/nuclei/passive.yaml`, checked against the templates actually selected.
8. **Counts and reports** — `scripts/security-dast.sh`, one converter per scanner into one report
   grouped by tool. The passes are de-duplicated by rule and URL first, and the report says which
   role saw each finding.
9. **Publishes the markdown report** to the private `yontrack/security-reports`.
10. **Validates `SECURITY.DAST`** on the build, with the counts of all three. A scanner error reports no stamp
   at all: nothing depends on this one, so an absent stamp stalls nothing and says the true
   thing - not scanned.

## Why there is no SARIF upload

The design allowed one, "only if GitHub accepts and usefully renders URL-located findings". It was
tried on the first real run: GitHub accepted the `dast-zap` upload without a warning and rendered
**zero alerts** out of 40 results. Code scanning places a result on a line of a file in the
repository, and a DAST finding is located by a URL, so there is nothing to place it on. Inventing a
file per rule would be worse than no alert. The upload was dropped, and the private report is the
only source of truth.

It had a second problem worth knowing before anyone tries again: ZAP's `sarif-json` template
embeds the whole exchange of every result, request headers included, so a result on `/graphql`
carries the live API token. Any future upload has to strip `webRequest` and `webResponse` first.

`dast-graphql` and `dast-nuclei` were not uploaded either, and not tried (#1766): graphql-cop's
findings are located by the endpoint and Nuclei's by a URL, so code scanning has the same nothing to
place them on, and the detail that would make them useful is graphql-cop's request, token included,
and the exact path of an exposure.

## How the scanners are pinned

| Scanner | Pinned by |
|---|---|
| ZAP | image digest |
| graphql-cop | the commit of its `1.16` tag and the SHA-256 of its archive, run in a digest-pinned `python:3.10-slim`, dependencies installed with `--require-hashes` from `security/dast/graphql-cop/requirements.txt` |
| Nuclei | release `3.11.1` and the SHA-256 its release publishes |
| nuclei-templates | the commit of the `v10.4.8` tag and the SHA-256 of its archive — Nuclei would otherwise download the newest templates when it starts |

`scripts/security-dast.sh fetch` refuses a download that does not match. All the pins are `env` in
`.github/workflows/dast-passive.yml`; Dependabot does not move them.

## The scanner roles

The API is scanned as the three accounts of [`security/dast/casc.yaml`](../../security/dast/casc.yaml)
(#1769), not as the demo admin, so that what a read-only or a project-scoped user reaches is scanned
too:

| Account | Yontrack group | Role |
|---|---|---|
| `scan-admin` | `DAST Admin` | global `ADMINISTRATOR` |
| `scan-readonly` | `DAST Read-only` | global `READ_ONLY` |
| `scan-project` | `DAST Project` | `PARTICIPANT` on `petclinic` |

They are Keycloak users of the demo realm (yontrack/yontrack-infra-gitops#72), and the workflow does
not use `DEMO_TOKEN` any more. Their passwords are the Actions secrets `DAST_SCAN_ADMIN_PASSWORD`,
`DAST_SCAN_READONLY_PASSWORD` and `DAST_SCAN_PROJECT_PASSWORD`, and the realm's client secret is
`DEMO_KEYCLOAK_CLIENT_SECRET`.

**Tokens.** `scripts/security-dast.sh login` gets each role an access token by a Keycloak password
grant on the demo realm, client `yontrack-client`; the password and the client secret go to curl on
its stdin, never on a command line. The token goes to a file in `$RUNNER_TEMP/dast/tokens`, readable
by the runner's user only, and is registered as a log mask. It is sent as `Authorization: Bearer`:
the API is a Spring OAuth2 resource server (`WebSecurityConfig`), and `X-Ontrack-Token` is for
Yontrack's own API tokens, which these accounts do not hold. ZAP gets it through a run-time rendered
copy of its plan, graphql-cop through a headers file mounted read-only; both are deleted after their
pass.

**Before scanning**, `whoami` makes one authenticated query as each role and stops the run unless the
API accepts the token and maps the account to the group `casc.yaml` gives it — and, for
`scan-project`, unless it sees at least one project, which it would not if the CasC had not been
re-applied after the seed. A project-scoped role seeing more projects than `casc.yaml` grants it is
printed as a WARNING, with the counts: the instance may grant project view to every user.

**Failure.** An account that cannot log in stops the run with the account, the status and Keycloak's
error class - `invalid_grant`, `unauthorized_client` - and reports no stamp. A scan that went on
unauthenticated would report a cleaner API than there is.

**Expiry.** A pass must not outlive its token. Every pass logs in again right before it runs, and
`login` refuses a token valid for less than the pass's budget (`DAST_ZAP_PASS_BUDGET`,
`DAST_GRAPHQL_COP_PASS_BUDGET`); the demo's realm issues tokens for an hour. After each ZAP API pass,
`assert-authenticated` proves from its HAR that no request to `/graphql` was answered 401.

**Redaction.** `scrub` and `report` redact every file in the token directory, wherever its contents
appear, and anything shaped like a JWT or following an `Authorization` or `X-Ontrack-Token` header
name.

Passive only: no role attempts a write, least of all a cross-project one. That is #1767's job, on a
throwaway stack.

## Why the API is authenticated and Nuclei and the UI are not

graphql-cop and ZAP's API pass test what the GraphQL endpoint accepts, and an unauthenticated request
stops at the authentication. The UI goes through that same API, so an authenticated crawl of it would
re-test the API through a browser. Nuclei looks for what an attacker **without** a token reaches — an
exposed actuator, a served `.git`, a default password — and an administrator's token on a login
template is not something to find out about.

## Why the scan sends an `Origin`

Spring only emits `Access-Control-Allow-Origin` in answer to a request that carries an `Origin`,
and a scanner sends none by default - so the first run reported every missing header on the UI and
not the `allowed-origins: "*"` in `application.yml`. The plan now adds `Origin:
https://dast.invalid` to requests on `/graphql` and `/hook`: a header on a read, which changes
nothing on the instance and makes the CORS policy observable.

The header stays now that #1771 has fixed the policy. The API is same-origin by default, so the
scan's responses carry no `Access-Control-Allow-Origin`, and it is the header that would show a
regression. It costs the scan nothing: with no origin configured the API serves a request carrying
an `Origin` like any other and leaves the refusal to the browser, so the GraphQL requests still
answer `200`. `CorsDefaultIT` holds that.

## How mutations are kept out

The demo gates SILVER and carries the dataset the smoke test asserts. The scan's hardest
constraint is not "do not attack" but **do not write**, and ZAP's GraphQL add-on generates one
request per root field of every root type it is given — mutations included, with no option to
leave them out.

So the option is the schema: `scripts/security-dast.sh query-schema` derives a **query-only** SDL
from `ontrack-web-core/ontrack.graphql` and that is what ZAP is fed. A mutation is not filtered,
it is not expressible. Three independent things would each have to fail for one to get through,
and [`security/dast/graphql/README.md`](../../security/dast/graphql/README.md) sets them out -
with the three that do the same for graphql-cop, whose one mutation test is excluded.

## Disclosure — read this before adding a step

`yontrack/yontrack` is public. Its workflow logs and artifacts are readable by any signed-in
GitHub user, and a DAST report is a working checklist against every deployment that has not
upgraded yet.

- The workflow prints **counts only**. No URL, no parameter, no evidence.
- It uploads **no artifact**. Deliberately. Do not add one.
- The detail goes to `yontrack/security-reports` — private, one file per run, never rewritten.
- The validation run points at the workflow run, never at the report.
- Anything a scanner writes to its own stdout goes through `scripts/security-dast.sh scrub` first.
  The ZAP log carries both crawled URLs and, when it echoes the plan back, the API token.
- The scanner roles' tokens are registered as masks, and `scrub` and `report` redact every one of
  them. The login and `whoami` steps print the account, its group and counts.
- graphql-cop's output carries every request it sent, token included, and Nuclei's log carries
  every match as it is found: neither is printed. On a failure, graphql-cop's stderr is printed
  scrubbed, and of Nuclei's log only its fatal lines. On success, Nuclei's final statistics — counts
  of templates, requests and errors — are all that is printed.
- The per-tool and per-role splits of the counts are in the report only; the log and the summary
  carry totals.

A step you add either prints a number or prints nothing.

## Changing what a finding counts for

Not in the ZAP plan. Nothing is disabled there, so the private report always keeps what the
scanner actually saw; the counting layer decides the rest:

| To | Edit |
|---|---|
| accept a finding, with a reason and an expiry | `security/dast/suppressions.yaml` |
| take a rule more or less seriously than its scanner does, everywhere | `security/dast/zap/rules.tsv` (all three scanners) |
| change which Nuclei templates run | `security/dast/nuclei/passive.yaml` |
| say what to change in Yontrack for a rule | `security/dast/mitigations.yaml` |

All three are reviewed in git, in the public repository, and none of them carries exploit detail.

## Running the pieces locally

The scan itself needs the demo and the scanner accounts' passwords, so it is not reproducible on a
laptop; everything around it is.

```bash
# The whole counting and reporting layer, against stubs and fixtures
scripts/security-dast-test.sh

# The derivation that keeps mutations out, against the real schema
scripts/security-dast.sh query-schema ontrack-web-core/ontrack.graphql /tmp/query-only.graphql

# A report out of scanner outputs you already have
scripts/security-dast.sh normalize-zap zap-ui.json > findings-zap-ui.json
scripts/security-dast.sh normalize-zap zap-readonly.json scan-readonly > findings-zap-readonly.json
scripts/security-dast.sh normalize-graphql-cop graphql-cop.out 1.16 scan-readonly > findings-graphql-cop.json
scripts/security-dast.sh normalize-nuclei nuclei.jsonl 3.11.1 > findings-nuclei.json
DAST_VERSION=x DAST_BUILD=y DAST_RUN_URL=z scripts/security-dast.sh report report.md findings-*.json
```

`scripts/security-dast.sh` needs `jq` and `yq` (mikefarah's v4), both of which `ubuntu-latest`
carries.

## The active scan

*Introduced by [#1767](https://github.com/yontrack/yontrack/issues/1767).*
[`.github/workflows/dast-active.yml`](../../.github/workflows/dast-active.yml) runs a second scan
that **attacks**: ZAP sends injection, XSS, SSRF and traversal payloads and exercises GraphQL
**mutations**, so it never touches the demo — only a **throwaway stack** brought up on the runner
from the latest BRONZE image, seeded with `ontrack-demo-seed`, and torn down after. Its counts land
on that BRONZE build as `SECURITY.DAST.ACTIVE`. Weekly (Sunday 01:00 UTC) and on demand; report
only, like the passive scan.

It reuses `scripts/security-dast.sh` unchanged, with `DAST_KIND=active`. What differs:

| | Passive | Active |
|---|---|---|
| Target | the demo | a throwaway stack ([`security/dast/compose/docker-compose-dast.yml`](../../security/dast/compose/docker-compose-dast.yml)) |
| Schema fed to ZAP | query-only (`query-schema`) — no mutation is expressible | mutations kept, the dangerous ones stripped (`active-schema`) |
| Plan | `zap/passive*.yaml` | [`zap/active.yaml`](../../security/dast/zap/active.yaml) — spider + `activeScan`, per role |
| Extra checks | — | authorization probes (`access-control`) |

**The throwaway stack** is the OIDC KDSL compose with three deliberate differences, each a
requirement of the issue: the GitHub ingestion hook **signature check is on** (so `/hook/secured/**`
is scanned as customers run it), the **management port 8800 is not published** (and
`MANAGEMENT_ENDPOINT_ACCOUNT_ACCESS` is left at its default), and the scanner-role CasC plus a
throwaway-only fragment turning `grantProjectViewToAll` **off** are mounted. It is a self-contained
compose file rather than an overlay because Docker Compose concatenates `ports`, so an override
cannot un-publish 8800. The scanner accounts (`scan-admin`, `scan-readonly`, `scan-project`) and
their `/dast-*` groups live in `compose/keycloak/import/oidc/ontrack.json`, shared with the OIDC
KDSL and Playwright shards; their passwords on this stack are fixed, clearly non-secret test values,
never the demo's `DAST_SCAN_*_PASSWORD` secrets.

**Mutations are kept, but not the dangerous ones.** `active-schema` keeps the `Mutation` root — the
whole point of an active scan — but strips token revocation and every account/group/role/CasC/
settings mutation, so ZAP cannot revoke its own token, delete a `scan-*` account, or turn
`grantProjectViewToAll` back on mid-run. Logout and the management port are excluded from attack in
the plan.

**The authorization checks** (`access-control`) are what the active scan exists for — the class of
bug [#1739], [#1741] and [#1742] were. `scan-readonly` must not create a project; `scan-project`
must not read or change a project it does not hold. They run before ZAP, while the seeded data is
intact, and count as HIGH. They only mean something with `grantProjectViewToAll` off, so the
workflow **fails before scanning** unless `whoami` shows `scan-project` seeing exactly its one
project. ZAP's own Access Control Testing has no Automation Framework job, which is why these live in
the script.

**Tokens over a multi-hour run** are refreshed the same way as the passive scan: a fresh Keycloak
password grant right before each role's pass, and each pass (spider + `activeScan` ≈ 45 min) is
bounded well under the realm's one-hour token, so no pass outlives its token — `assert-authenticated`
proves it afterwards.

[#1739]: https://github.com/yontrack/yontrack/issues/1739
[#1741]: https://github.com/yontrack/yontrack/issues/1741
[#1742]: https://github.com/yontrack/yontrack/issues/1742

## What came next

- [#1767](https://github.com/yontrack/yontrack/issues/1767) — done: the active scan, above.
- [#1769](https://github.com/yontrack/yontrack/issues/1769) — done: the three scanner roles.
