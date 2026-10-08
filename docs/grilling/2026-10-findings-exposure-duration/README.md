# Findings: exposure duration across findings, and remediation time by episode

Outcome of the grilling session of 2026-10-08 on
[#2041](https://github.com/yontrack/yontrack/issues/2041), the follow-up of #2040 split off in
[`../2026-10-findings-exposure/README.md`](../2026-10-findings-exposure/README.md). This file records
the decisions and why they were taken. The implementable spec is the body of #2041, and
[`mockups.html`](mockups.html) shows the variants that were put to the vote, with the chosen ones
marked.

## Where we start from

- #2040 stores **exposure periods** (`FINDING_EXPOSURE_PERIODS`, one row per stretch of a finding ×
  branch × stamp) and exposes them as `FindingExposure.periods`, each with a server-side
  `durationSeconds`. There is no finding-level duration.
- `getExposurePeriods` reloads the finding, its periods and its sightings **per exposure**: asking for
  periods from a list would be N+1.
- `ProjectFindingsTable.js` pages on the server and has **no sort at all**. `getProjectFindings`
  loads every finding of the project, filters and orders in Kotlin (`findingOrder`), then pages.
- `security.remediationTime` takes `Finding.firstSeen` → `Finding.resolvedAt` for CRITICAL/HIGH
  findings resolved in the window, median as the value. `firstSeen` never resets and `resolvedAt` is
  the latest resolution, so a reopened finding counts the time it was resolved.
- `security.overdue` ages open findings from `firstSeen` too — the same flaw.
- The scorecard page has no per-reading anchor, and the findings UI never links to it.
- Neither findings (#1864) nor the scorecard (#1904) exist on mobile.
- The demo's remediation and overdue findings sit on one branch each and are never reopened; the
  only reopened demo finding is a MEDIUM, invisible to both readings.

## Decisions

1. **The column shows the longest ongoing period on one branch × stamp**, over the counting branches
   (only the filtered branch when there is a branch filter). It is the figure the finding page shows
   per row, so the two pages agree. The union across branches only differs when one branch was fixed
   while another stayed exposed. `now − firstSeen` was rejected: it is the flaw the issue names.
2. **Duration with an age bar, and "fixed after X" on resolved rows** (variant C). The bar makes the
   oldest findings visible at a glance. It is decorative: `aria-hidden`, the text carries the value.
3. **The bar is on a log scale, 1 h to 1 year**, neutral, amber when the exposure is accepted. A scale
   relative to the page redraws on every page or filter, so bars could not be compared. A scale
   against the remediation target was rejected because a project can be in several estates with
   different per-severity targets, or in none.
4. **"Fixed after X" is the length of the last episode**, so a resolved row shows what that finding
   contributed to *Remediation time*.
5. **Sorting is on the server**: a `sort` argument (`DEFAULT | EXPOSED_FOR`) on `Project.findings`,
   applied in Kotlin before `page()`, descending only, kept in the URL query. A client-side sorter
   would sort the current page only. Periods are loaded in one batch query per project.
6. **Remediation time measures exposure episodes.** An episode is the union of a finding's periods on
   the scope's branches; one sample per episode ending in the window. A reopening is a new incident
   to fix and the resolved gap no longer counts; a fix on three branches is one sample, not three
   (which a per-period sample would give). One total per finding was rejected: it hides regressions.
7. **Overdue ages open findings from the start of their current episode**, in the same issue, so the
   two readings never disagree on a finding.
8. **Periods that overlap or touch merge; no gap tolerance.** A scan that stops reporting a finding
   resolves it, and #2040 already shows it so. One pure function computes episodes for both the table
   and the scorecard.
9. **The term is *exposure episode*** ("episode" for short), with *Remediation time* defined next to
   it in `CONTEXT.md`. Avoid "incident" and "reopening".
10. **The finding page links to the reading from the exposure section header**, to
    `?set=project#reading-project-security.remediationTime`; the tile gets a DOM id and the page
    scrolls to the hash. Only when the scorecard extension is present. An inline project median was
    rejected: a scorecard query on every finding page, for a misleading comparison (one finding vs a
    CRITICAL/HIGH median).
11. **No new demo content.** The column appears on the existing data, and episodes leave the pinned
    demo readings (3 and 11 days) unchanged. A reopened HIGH would disturb the gating and overdue
    demos for little gain.
12. **Docs**: the reading definitions in `scorecard/scorecard.md`, the column and sort in
    `integrations/findings/findings.md`, and a line in `migration-to-v6.md` saying both readings now
    measure episodes, so values can drop for reopened findings.
13. **No mobile impact**: no findings and no scorecard on mobile.
