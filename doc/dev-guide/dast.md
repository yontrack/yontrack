# Passive DAST scan of the demo

*Introduced by [#1765](https://github.com/yontrack/yontrack/issues/1765), part of
`initiative: security-scans`. The design is [`docs/grilling/2026-09-dast.md`](../../docs/grilling/2026-09-dast.md);
what the scanners read is [`security/dast/`](../../security/dast/README.md).*

Dynamic application security testing: scanning a **running** Yontrack over HTTP, as opposed to the
supply-chain, SAST and secret scans that read the code and the images. The passive scan runs OWASP
ZAP against the public demo instance and records its findings on the deployed build as the CHML
stamp `SECURITY.DAST`.

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

1. **Asks the demo which version it is running**, and resolves that version back to a Yontrack
   build — `scripts/demo-smoke.sh version` then `resolve`, the same two functions `demo-smoke.yml`
   uses, so `SECURITY.DAST` and `DEMO.SMOKE` land on the same build by construction. The CLI is
   installed directly rather than through `ontrack-github-actions-cli-config`, which would
   register a build as a side effect.
2. **Checks that the management port is not reachable.** If it answers, the run stops: an exposed
   actuator is an incident, not a MEDIUM to add to a tally.
3. **Derives a query-only GraphQL schema** and renders the ZAP plan with the API token.
4. **Runs ZAP** from a digest-pinned container against `security/dast/zap/passive.yaml`.
5. **Proves no mutation was sent**, from ZAP's own record of every request.
6. **Counts and reports** — `scripts/security-dast.sh`.
7. **Publishes the markdown report** to the private `yontrack/security-reports`.
8. **Validates `SECURITY.DAST`** on the build, with the counts. A scanner error reports no stamp
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

## Why the scan sends an `Origin`

Spring only emits `Access-Control-Allow-Origin` in answer to a request that carries an `Origin`,
and a scanner sends none by default - so the first run reported every missing header on the UI and
not the `allowed-origins: "*"` in `application.yml`. The plan now adds `Origin:
https://dast.invalid` to requests on `/graphql` and `/hook`: a header on a read, which changes
nothing on the instance and makes the CORS policy observable.

## How mutations are kept out

The demo gates SILVER and carries the dataset the smoke test asserts. The scan's hardest
constraint is not "do not attack" but **do not write**, and ZAP's GraphQL add-on generates one
request per root field of every root type it is given — mutations included, with no option to
leave them out.

So the option is the schema: `scripts/security-dast.sh query-schema` derives a **query-only** SDL
from `ontrack-web-core/ontrack.graphql` and that is what ZAP is fed. A mutation is not filtered,
it is not expressible. Three independent things would each have to fail for one to get through,
and [`security/dast/graphql/README.md`](../../security/dast/graphql/README.md) sets them out.

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

A step you add either prints a number or prints nothing.

## Changing what a finding counts for

Not in the ZAP plan. Nothing is disabled there, so the private report always keeps what the
scanner actually saw; the counting layer decides the rest:

| To | Edit |
|---|---|
| accept a finding, with a reason and an expiry | `security/dast/suppressions.yaml` |
| take a rule more or less seriously than ZAP does, everywhere | `security/dast/zap/rules.tsv` |
| say what to change in Yontrack for a rule | `security/dast/mitigations.yaml` |

All three are reviewed in git, in the public repository, and none of them carries exploit detail.

## Running the pieces locally

The scan itself needs the demo and its token, so it is not reproducible on a laptop; everything
around it is.

```bash
# The whole counting and reporting layer, against stubs and fixtures
scripts/security-dast-test.sh

# The derivation that keeps mutations out, against the real schema
scripts/security-dast.sh query-schema ontrack-web-core/ontrack.graphql /tmp/query-only.graphql

# A report out of a ZAP JSON report you already have
scripts/security-dast.sh normalize-zap zap.json > findings.json
DAST_VERSION=x DAST_BUILD=y DAST_RUN_URL=z scripts/security-dast.sh report report.md findings.json
```

`scripts/security-dast.sh` needs `jq` and `yq` (mikefarah's v4), both of which `ubuntu-latest`
carries.

## What comes next

- [#1766](https://github.com/yontrack/yontrack/issues/1766) — graphql-cop and Nuclei, as two more
  `normalize-*` converters feeding the same `report` step.
- [#1767](https://github.com/yontrack/yontrack/issues/1767) — the weekly **active** scan on a
  throwaway stack, reporting `SECURITY.DAST.ACTIVE`, reusing this script with `DAST_KIND=active`.
- [#1769](https://github.com/yontrack/yontrack/issues/1769) — the three scanner roles of
  `security/dast/casc.yaml`, replacing the single admin token used here.
