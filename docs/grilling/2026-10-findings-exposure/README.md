# Findings: exposure duration and the fix in the timeline

Outcome of the grilling session of 2026-10-08 on
[#2040](https://github.com/yontrack/yontrack/issues/2040). This file records the decisions and why
they were taken. The implementable spec is the body of #2040, and
[`finding-page.html`](finding-page.html) is the mockup that was chosen. Proposal D of the original
issue was split off as [#2041](https://github.com/yontrack/yontrack/issues/2041), which is blocked
by #2040.

## Where we start from

- An exposure is **one row per finding × branch × stamp**, and it is **overwritten when the finding
  is reopened**: `FindingsExposureComputation.compute` builds a fresh `FindingExposure`, which resets
  `since` and clears `resolvedAt`, so the previous period is lost
  (`FindingJdbcRepository.saveExposures`, `INSERT … ON CONFLICT DO UPDATE`).
- **The resolving run is not stored.** Resolution keeps only `resolvedAt` (the run's time) and
  `resolutionReason = ABSENT`.
- **Acceptance comes from the scanner report.** Every observation carries a full copy of it
  (statement, expiry, source), and the exposure carries only the flag and the expiry. Expiry is
  evaluated when read.
- Observations cascade with their validation run, so a build purge deletes them.
- The finding page has no duration. Its observations timeline stops at the last sighting, and *First
  seen* / *Resolved* are project-level dates with no build.
- There is no findings UI on mobile in 6.0: both routes go to the desktop-only interstitial
  (`mobileRoutes.js`).
- The demo seed has resolved and accepted findings, but nothing reopened.

## Decisions

1. **The build matters as much as the date.** The page says *where* a finding was discovered and
   fixed (`main`, build `3.4.2`), not only when. This was the main correction to the issue as
   written.
2. **Scope: A, B and C in #2040.** D (the project findings table and *Remediation time*) is #2041,
   because it is a different surface and reads the periods that #2040 introduces.
3. **Acceptance counts as exposure.** The vulnerable code still ships, and an acceptance is a
   decision about risk, not a fix. It is shown differently: amber in the timeline, "accepted" in the
   table.
4. **Durations adapt to their length**: minutes under 1 h, hours under 48 h, days above, with the
   exact timestamps in a tooltip. Scans run several times a day, so an exposure is often shorter
   than a day.
5. **Mockup 4 + 1.** The table "Exposure per branch" gains *Discovered in*, *Fixed in* and *Exposed
   for*, and a Gantt-like *Exposure timeline* above it gives the picture across branches. Two
   options were rejected:
   - the build strip (option 3), because it depends on builds that get purged;
   - the per-branch story line (option 2), because it repeats the table.
6. **Exposure periods are kept in a new table**, `FINDING_EXPOSURE_PERIODS`, while
   `FINDING_EXPOSURES` stays the current state. The reopened lane, the "Reopened" entry and an honest
   duration all need the earlier periods.
7. **Each end of a period records its run and a snapshot of its build.** The run is an FK with
   `ON DELETE SET NULL`. The snapshot is the build's display name, so "fixed in 3.4.2" stays
   readable after a purge: the link goes, the text stays.
8. **No backfill.** 6.0 is unreleased, so only `self.dev` and the regenerated demo have data. The
   migration seeds one period per existing exposure, with no runs or builds ("build unknown").
9. **The observation timeline becomes a server-side history** (`Finding.history`). The fix, the
   reopenings and the acceptance changes are entries in it, and consecutive observations of one
   exposure period collapse into a group that can be expanded. 200 scans would otherwise push
   "Discovered" to page 10. The history is built on the server because synthetic entries, groups
   and paging do not mix reliably in the client.
10. **Acceptance entries are derived, not stored.** "Accepted", "Acceptance withdrawn" and
    "Acceptance expired" are computed from the observations, which already carry the statement and
    the expiry. Acceptance never starts inside Yontrack.
11. **The finding summary names branch and build**, both for *First seen* and for *Resolved* (the
    build that resolved the last counting branch).
12. **Gantt layout.** There is one lane per branch × stamp, the same rows as the table. The stamp
    name shows only when the branch has several. The axis runs from the earliest period start to
    now, and a bar is at least 4 px wide. The section is hidden when there is no exposure.
13. **Demo seed**: `CVE_CRITICAL_FIXED` comes back on `main` two builds after its fix.
    `CVE_FIXED_ON_MAIN` is left as it is, because the security scorecard demo relies on it.
14. **Mobile: no impact**, because there is no findings UI on mobile in 6.0. The branch command
    tooltip and the widgets are out of scope. *Remediation time* goes to #2041.

## Specs as body, not comment

`/fix-issue` and `/run-initiative` read only the issue's `number,title,body,labels`, never its
comments. The settled spec is therefore the rewritten body of #2040. The comment on the issue
records the session and points here.
