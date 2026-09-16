# DAST — dynamic application security testing

Everything the automated DAST scans read: the ZAP plans, the per-rule levels, the accepted
findings, the rule-to-mitigation map and the scanner role declaration. Nothing here is a report:
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
  graphql/            how the GraphQL endpoint is scanned, and why no mutation is ever sent
  zap/
    passive.yaml      ZAP Automation Framework plan for the passive scan of the demo
    rules.tsv         per-rule level overrides, in the zap-baseline IGNORE/WARN/FAIL vocabulary
```

## Who reads what

| File | Read by |
|---|---|
| `zap/passive.yaml` | the ZAP container, in `.github/workflows/dast-passive.yml` |
| `zap/rules.tsv`, `suppressions.yaml`, `mitigations.yaml` | `scripts/security-dast.sh report` |
| `casc.yaml` | a human, and its copy in the gitops repository; re-applied by `scripts/demo-smoke.sh casc` |

## The counting layer is not the scanner

A deliberate split, and the reason a second scanner costs so little to add:

**The scanner reports everything it finds.** No rule is disabled in `zap/passive.yaml`, and no
finding is filtered out of the ZAP report. The private report therefore keeps full fidelity —
what the scanner saw is what the report shows.

**`scripts/security-dast.sh` decides what it means.** It normalises a scanner's own report into
one shape, then applies `zap/rules.tsv` and `suppressions.yaml` to it, counts what is left and
writes the markdown report. A new scanner (#1766 adds graphql-cop and Nuclei) needs only a
`normalize-<tool>` subcommand emitting that shape; the levels, the suppressions, the mitigations,
the counting, the report and the disclosure rules are then already done for it. A second workflow
(#1767 adds the active scan) needs only its own plan and `DAST_KIND=active`.

The normalised shape is documented in the header of `scripts/security-dast.sh`.

## Counting

After suppressions, and **per rule**, not per affected URL: one misconfiguration typically shows
up on dozens of URLs, and counting the URLs would make "how bad is it" a function of how many
pages the spider happened to reach.

| Scanner risk | Counted as |
|---|---|
| Critical | CRITICAL |
| High | HIGH |
| Medium | MEDIUM |
| Low | LOW |
| Informational | dropped |

ZAP has no Critical, so a passive ZAP-only run never produces one. The counts go to the
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
