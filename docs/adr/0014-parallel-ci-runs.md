# CI runs in parallel across commits, and registers builds in order

`.github/workflows/ci.yml` inherited `concurrency: { group: ci-${{ github.ref }} }` from the
`disableConcurrentBuilds()` of the Jenkins multibranch pipeline it replaced. On a repository
where work reaches `main` through pull requests that would have serialised one branch at a
time; here it serialised everything. The agent workflow in `CLAUDE.md` merges into `main` and
never pushes the `claude/*` branch, so `ci-refs/heads/main` was the group of every CI run in
the repository, and two sessions finishing near each other queued: measured over 25 runs on
`main`, execution took 18-24 minutes and eight of those runs waited a further 9-14 before
starting.

The group is now keyed on `github.sha`. Different commits run at the same time; a re-run of the
same commit still cannot overlap itself, which matters because both runs would write their
validations onto the one Yontrack build that commit owns. `cancel-in-progress` stays false:
`disableConcurrentBuilds()` queued rather than cancelled, and a superseded commit that never
gets its own validated build is a hole in the promotion chain.

Most of the pipeline was already indifferent to running twice at once. The GHCR tags are
`:run-<run_id>` and `:<base>-rc-<run_number>`, both unique per run, and the retag to
`nemerosa/ontrack:$BASE_VERSION` happens inside a job's own runner. The integration, KDSL and
Playwright stacks are Compose projects on that same throwaway runner -- the mechanism of
[ADR 0012](0012-parallel-integration-test-stacks.md) and
[ADR 0013](0013-parallel-kdsl-acceptance-stacks.md) is what a CI runner gets for free by being
a fresh clone. The demo is deployed by `demo-deploy.yml` on a schedule, under a concurrency
group of its own, and deliberately not by the BRONZE promotion. The documentation job uploads
an artefact and publishes nothing. Nothing costs more, either: the repository is public, so
standard runners are free, and `cancel-in-progress: false` already meant every run executed.

## The exception: registering the Yontrack build

One step is not indifferent. Yontrack orders builds by insertion id --
`StructureJdbcRepository.getPreviousBuild` is `ORDER BY ID DESC`, and the comment above it says
that a higher id is a later build and that the whole build model rests on it. The `Yontrack`
job registers the run's build about forty seconds in. Serialised, registration order could not
diverge from commit order. In parallel, two merges landing inside that window can record
main's commits the wrong way round, and a wrong `getPreviousBuild` poisons the change log the
SILVER and GOLD notifications render, the "latest BRONZE on main" default of `demo-deploy.yml`,
and every auto-versioning boundary. Two agents merging seconds apart is precisely the case the
change is for, so this is not a theoretical window.

So the guard moved rather than going away. The `yontrack` job carries a *job-level*
concurrency group, `ci-yontrack-${{ github.ref }}`, which serialises registration -- a twelve
second job -- while everything around it runs in parallel. Queue time does not count against a
job's `timeout-minutes`, and a job pending on a group holds no runner.

That is worth stating precisely, because it is less than it looks. The group makes registration
**mutually exclusive**; it does not **order** it by commit. Which run enters the group first is
whichever reaches the job first, and a run reaches it 65-95 s after starting -- `setup`
resolving the version, then the checkout here. Two pushes further apart than that spread
register in commit order. Two pushes inside it can still invert, and the guard has only
narrowed the window from "two registrations in the same instant" to "the spread in
time-to-register".

This is not hypothetical, and the transition to this scheme demonstrated it within the hour.
Run 253 was created under the old ref-keyed group and queued 18 minutes behind an earlier run,
while runs 254 and 255 -- created once the commit-keyed group was in place -- started within
three seconds of their pushes and registered first. Yontrack recorded the three builds in the
order 254, 255, 253, so the oldest of the three commits owns the newest build id. That
particular inversion was an artefact of old and new configuration running side by side and
cannot recur once every run uses this file; the 30-second steady-state window is what remains.

Closing that window entirely would mean a barrier rather than a mutex: the job would have to
wait until every run on the same branch with a lower `run_number` had passed its own
registration, polling the runs API to find out. That is real complexity for a race that needs
two merges within half a minute of each other, so it is deliberately not built. If build order
on `main` is ever observed to invert in steady state, this is the shape of the fix.

`.github/workflows/codeql.yml` had the same per-ref queue and is keyed the same way now, except
that a pull request still groups on its ref so that `cancel-in-progress` can supersede an
analysis in flight. Its `SECURITY.CODE` stamp is in no promotion today, but leaving it
serialised would have moved the bottleneck rather than removing it.

## Consequences

- Two or three CI runs overlap comfortably. Each fans out to about thirteen concurrent jobs
  against the organisation's allowance of sixty, and past that the degradation is fair-share
  queueing at the job level rather than a run waiting whole minutes to start.
- Runs on `main` can now complete out of order, so "the latest completed run" is no longer
  "the newest commit". Anything asking whether `main` is green must ask about a commit.
  `CLAUDE.md` already does, keying `status:ready` on `headSha`.
- Yontrack's build order follows `main`'s commit order for any two pushes more than ~30 s
  apart, which is what the change log, the promotion chain and auto-versioning read. Closer
  than that it can invert, and the section above says why and what the fix would be. An
  inverted pair gives the older build the newer id, so `getPreviousBuild` walks the wrong way
  for one build and `demo-deploy.yml` would offer the wrong "latest BRONZE"; the next build
  registered puts the sequence right again.
- `gradle/actions/setup-gradle` writes its cache from the default branch, so two overlapping
  runs on `main` race for one cache key. The loser logs that the entry already exists. It costs
  a little cache freshness and nothing else.
