# DAST — dynamic application security testing

Everything the automated DAST scans read: the ZAP plans, the graphql-cop configuration, the Nuclei
template selection, the per-rule levels, the accepted findings, the rule-to-mitigation map and the
scanner role declaration. Nothing here is a report:
reports go to the **private** repository [`yontrack/security-reports`](https://github.com/yontrack/security-reports),
because `yontrack/yontrack` is public and a finding's URL, parameter and evidence are an
attacker's checklist against every deployment that has not upgraded yet.

The design behind all of it is [`docs/grilling/2026-09-dast.md`](../../docs/grilling/2026-09-dast.md).

## Layout

```
security/dast/
  casc.yaml           Yontrack roles of the scanner accounts - canonical declaration (#1768)
  suppressions.yaml   accepted findings, subtracted from the counts, with a statement and an expiry
  mitigations.yaml    rule -> what to change in Yontrack and where
  compose/
    docker-compose-dast.yml  the throwaway stack the ACTIVE scan attacks (#1767): the OIDC KDSL
                             stack with the hook signature check ON, the management port NOT
                             published, and the CasC mounted
    casc/
      security-settings.yaml throwaway-only CasC turning grantProjectViewToAll OFF, so the active
                             scan's cross-project checks mean something (never in casc.yaml itself)
  graphql/            how the GraphQL endpoint is scanned, and why no mutation is ever sent (passive)
  graphql-cop/
    config.py         graphql-cop's configuration, reading the role's bearer token from a run-time file
    requirements.txt  its Python dependencies, pinned by hash
  nuclei/
    passive.yaml      Nuclei configuration: selected and excluded tags, HTTP only, rate limit
  zap/
    passive.yaml      ZAP Automation Framework plan for the passive scan of the demo: the UI,
                      unauthenticated
    passive-api.yaml  the same for the API, run once per scanner role of casc.yaml (#1769)
    active.yaml       ZAP plan for the ACTIVE scan of the throwaway stack (#1767): attacks, and
                      exercises mutations, once per scanner role
    rules.tsv         per-rule level overrides, in the zap-baseline IGNORE/WARN/FAIL vocabulary -
                      for the findings of every scanner, despite the directory
```

## Two scans

| | Passive (#1765/#1766/#1769) | Active (#1767) |
|---|---|---|
| Workflow | `.github/workflows/dast-passive.yml` | `.github/workflows/dast-active.yml` |
| Target | the demo — real ingress, TLS, Keycloak | a throwaway stack on the runner, torn down after |
| Writes? | never — mutations are not even expressible | yes — it creates, changes and deletes data |
| Scanners | ZAP baseline, graphql-cop, Nuclei | ZAP active scan, plus the authorization probes |
| Mutations | stripped from the schema (`query-schema`) | KEPT, minus the dangerous ones (`active-schema`) |
| Stamp | `SECURITY.DAST` on the deployed build | `SECURITY.DAST.ACTIVE` on the scanned BRONZE build |

Both run the same counting/reporting/disclosure layer — `scripts/security-dast.sh` — the active
scan only setting `DAST_KIND=active`. What differs is what may be sent: the passive scan is fed a
query-only schema so a mutation cannot be generated at all; the active scan is fed a schema that
keeps the `Mutation` root but strips token revocation and every account/group/role/CasC/settings
mutation (`active-schema`), so ZAP sends mutations on purpose but never one that could revoke its
own token, delete a `scan-*` account, or turn `grantProjectViewToAll` back on mid-run. Logout and
the management port are excluded from attack in the plan.

### The active scan's authorization checks

The active scan adds the checks the passive one cannot make, because they require *writing*: a role
attempting what its authorization must refuse. `scripts/security-dast.sh access-control` sends a
curated, deterministic set of probes — `scan-readonly` creating a project, `scan-project` reading
and changing a project it does not hold — and records an *escalation* when one succeeds. These are
the checks that would have caught #1739, #1741 and #1742. They run before ZAP, while the seeded data
is intact, and are a scanner in their own right in the report (`access-control`), counted as HIGH.
ZAP has no Access Control Testing job in the Automation Framework, which is why the checks live in
the script rather than the plan.

They only mean something with `grantProjectViewToAll` off — Yontrack grants project view to every
authenticated user by default, which is why `scan-project` sees four projects on the demo though its
CasC grants it one (#1769). The throwaway stack turns that default off through
`compose/casc/security-settings.yaml`, and the workflow proves with `whoami` that `scan-project`
sees exactly one project before ZAP starts, failing before scanning if it sees more.

## Who reads what

| File | Read by |
|---|---|
| `zap/passive.yaml`, `zap/passive-api.yaml` | the ZAP container, in `.github/workflows/dast-passive.yml` |
| `zap/active.yaml` | the ZAP container, in `.github/workflows/dast-active.yml`, rendered per role by `render-plan` |
| `compose/docker-compose-dast.yml`, `compose/casc/*` | Docker Compose, in `.github/workflows/dast-active.yml` |
| `graphql-cop/*` | the graphql-cop container, in the passive workflow |
| `nuclei/passive.yaml` | Nuclei, in the passive workflow, and `scripts/security-dast.sh nuclei-preflight` |
| `zap/rules.tsv`, `suppressions.yaml`, `mitigations.yaml` | `scripts/security-dast.sh report` (both scans) |
| `casc.yaml` | a human, and its copy in the gitops repository; re-applied by `scripts/demo-smoke.sh casc`; read by `scripts/security-dast.sh whoami`, which checks each scanner account arrives in its group; on the active stack its `casc:` wrapper is stripped and it is mounted as CasC |

## The counting layer is not the scanner

A deliberate split, and the reason a second scanner costs so little to add:

**The scanner reports everything it finds.** No rule is disabled in `zap/passive.yaml`, no finding
is filtered out of the ZAP report, and Nuclei is not filtered by severity. What *is* restricted is
what may be sent to the demo at all: graphql-cop's one mutation-sending test, and Nuclei's
`intrusive`, `dos`, `fuzz` and `bruteforce` templates. The private report therefore keeps full fidelity —
what the scanner saw is what the report shows.

**`scripts/security-dast.sh` decides what it means.** It normalises a scanner's own report into
one shape, then applies `zap/rules.tsv` and `suppressions.yaml` to it, counts what is left and
writes the markdown report. A new scanner needs only a `normalize-<tool>` subcommand emitting that
shape - which is all graphql-cop and Nuclei took (#1766); the levels, the suppressions, the
mitigations, the counting, the report and the disclosure rules are then already done for it. All
three scanners' counts add up into the one `SECURITY.DAST` validation run, and the report groups
the findings by tool, with each tool's own counts. A second workflow
(#1767 adds the active scan) needs only its own plan and `DAST_KIND=active`.

The normalised shape is documented in the header of `scripts/security-dast.sh`.

## Counting

The API passes run once per scanner role (#1769), so the same rule is usually reported by several
passes. Before anything is counted, the findings of every pass are **de-duplicated by rule and URL**,
and each finding and each affected URL records the roles that saw it - `unauthenticated` for Nuclei
and the UI part of the ZAP scan. Three roles tripping one rule are one finding; the report says which
roles saw it, and splits the counts by role as well as by tool.

After suppressions, and **per rule**, not per affected URL: one misconfiguration typically shows
up on dozens of URLs, and counting the URLs would make "how bad is it" a function of how many
pages the spider happened to reach.

| Scanner risk | Counted as |
|---|---|
| Critical (Nuclei only) | CRITICAL |
| High | HIGH |
| Medium | MEDIUM |
| Low | LOW |
| Informational, and Nuclei's `unknown` | dropped |

ZAP and graphql-cop have no Critical; a Nuclei critical fails the stamp like a HIGH. The counts go to the
`SECURITY.DAST` CHML stamp declared in `.yontrack/ci.yaml` — WARNING from one MEDIUM, FAILED from
one HIGH — which is in no promotion: this is reporting, not gating.

## Suppressions and mitigations are reviewed in git

Both files live in the public repository on purpose. A suppression says *this rule, on this kind
of URL, is accepted, because …* — a statement about a decision, carrying no exploit detail — and
an expiry date, so that an acceptance is revisited rather than inherited. A mitigation says what
to change and where. Neither is worth hiding, and both are worth reviewing.

An **expired** suppression stops applying: the finding comes back into the counts, which is the
whole point of the date.

## Disclosure

The rules the workflows follow, in one place:

- workflow logs and step summaries carry **counts only** — never a URL, a parameter or a piece of
  evidence;
- there are **no workflow artifacts** for a DAST run;
- the full detail goes to `yontrack/security-reports` only, one file per run, never rewritten;
- the validation run points at the workflow run, never at the report.

`scripts/security-dast.sh` prints counts and nothing else, and everything a scanner writes to its
own stdout goes through `sd_scrub` before it can reach a log.
