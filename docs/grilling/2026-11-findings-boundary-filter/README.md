# Findings: a build filter on the end of an exposure period

Outcome of the grilling session of 2026-11-02 on a customer request. This file records the decisions
and why they were taken. The implementable spec is the body of
[#2137](https://github.com/yontrack/yontrack/issues/2137), and [`finding-page.html`](finding-page.html)
is the mockup that was chosen.

## Where we start from

- The finding page ([#2040](https://github.com/yontrack/yontrack/issues/2040)) names the build which
  ended each exposure period. That build may never have been promoted anywhere, so "fixed in X" can
  describe a build nobody ships.
- The customer asked to filter the boundary with the build filters of the Classic branch view. With
  no filter, all builds count, as today. Only the end of a period is filtered.
- Those filters cannot be reused as they are:
  - they select from the newest build backwards (`ORDER BY B.ID DESC`, `LIMIT count`, 10 by default),
    so nothing can ask for the first match after a given build;
  - `since*` and *Last per promotion level* are relative to the head of the branch, so their result
    moves with every new build;
  - saved filters are per user and per branch, while the timeline shows several branches.

## Decisions

1. **The boundary is the first matching build at or after the fixing build, in build order, and
   before the next period starts.** Build order rather than dates: an older build promoted after the
   fix still contains the finding. A matching build which was never scanned counts, as nothing says
   the finding is back.
2. **Only the criteria which test a single build are usable.** `count` is ignored, because a limit
   would silently lose the matching build. `since*` and *Last per promotion level* are not usable.
3. **One filter per page, applied to every lane by name.** The question is "did the fix reach
   production?", and it should have one answer across branches. Release branches normally share
   promotion names. One picker per lane (reusing `BuildFilterDropdown` as it is) was rejected because
   a mix of rules makes the lanes harder to compare, and a per-lane override because it is the most to
   build and explain.
4. **A personal view setting**, in local storage per project with a permalink. A project-level setting
   would be more consistent, but it needs an admin and effectively redefines "fixed" for the project.
   The active filter is always shown, so nobody mistakes the filtered view for the default one.
5. **A lens, not a state.** Only the *Exposure timeline* and *Exposure per branch* sections change. The
   state of the finding, *History*, the project findings table, *Remediation time* and the scorecard
   stay on raw scan facts: otherwise two users would get different scorecards.
6. **The bar ends when the build started to match**: the promotion run for a promotion, the validation
   run for a stamp, the creation of the build otherwise, and the latest when several apply. Using only
   the creation would say a fix reached GOLD days before it did.
7. **Option A: two steps on one bar**, red until the scan fix, then hatched until the matching build.
   Both facts are visible at once. Two options were rejected:
   - moving the end of the bar (B), because the real fix shrinks to a tick, and a filter makes a 4-day
     fix look like 12 days of exposure with no visible reason;
   - a second track below the exposure (C), because every lane gets taller and the gap is easier to
     overlook.
8. ***Exposed for* keeps the scan duration**, with the filtered one as a second line. One column
   heading must not carry two meanings depending on who is viewing, and the page must not contradict
   *Remediation time*.
9. **"None yet" and "Not reached" differ.** The first is still waiting on a build. The second was
   reopened before any matching build, so it needs a new fix.
10. **A filter which cannot apply falls back to the raw scan fix, and says so.** Showing a branch with
    no GOLD level as "None yet" would have it waiting for a build which can never exist.
11. **Saved filters are listed, and choosing one copies its criteria** into the page filter. This gives
    the customer the filters they know, while keeping one rule for the whole page. A filter using
    `since*` is listed but cannot be selected, and says why.
12. **Nothing is stored.** A boundary is computed when read, so it follows later promotions and
    filter changes, and no migration is needed.
13. **Mobile: no impact**, because there is no findings UI on mobile in 6.0. **Demo seed**: yes, as the
    feature is user-visible. `CVE_REOPENED` already has a fix in an unpromoted build.

## Specs as body, not comment

`/fix-issue` and `/run-initiative` read only the issue's `number,title,body,labels`, never its
comments. The settled spec is therefore the body of #2137.
