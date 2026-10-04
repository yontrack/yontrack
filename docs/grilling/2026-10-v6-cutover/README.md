# v6 cutover, 5.x maintenance, 6.0 alphas

Grilling session of 2026-10-04. It settles how `v6` becomes `main`, how 5.x stays maintained, and how 6.0
pre-releases ship. The issue breakdown is in [issues.md](issues.md).

## Starting point

- `origin/v6` is a pure fast-forward of `origin/main`: 0 behind, 173 ahead. The merge-base is main's HEAD,
  which is tag `5.5.7`.
- main has `VERSION=5.5` and migrations up to `V82`. v6 has `VERSION=6.0` and migrations up to `V98`.
  No migration numbers collide.
- `release/5.4` still exists on origin. There is no `release/5.5`: the 5.5.x patches shipped from main.
- Editing `VERSION` to `6.0-alpha` is **not enough** to release alphas:
  - `VersionCalculator` builds `6.0-alpha.N` correctly. CI turns that into `6.0-alpha.N-rc-<run>`.
  - `scripts/release.sh` only accepts `X.Y.Z`, so GOLD fails before anything is published.
  - Nothing marks the GitHub release as a pre-release.
  - The RELEASE promotion would deploy the alpha to self.dev, post to `#internal-releases` and dispatch
    `doc.yontrack.com`.

## Decisions

### The 5.x maintenance branch

1. **The branch is `release/5.5`.** It is cut from main's HEAD at the freeze, which should be tag `5.5.7`.
   - `v5` is not workable. `origin/v5/1039-redis-caching` blocks the ref, and `v\d+` would get the
     versioning that main gets.
   - The `release/5.4` git branch is deleted. Its Yontrack branch is kept.
2. **5.5 is maintained for about 6 months after 6.0.0.** This is not documented anywhere. Retiring the
   branch is Damien's call.
3. **A 5.x fix lands on main first and is cherry-picked to `release/5.5`.** A bug that exists only in 5.x,
   in code that 6.0 removed, lands on `release/5.5` directly.
4. **`patch-release.md` rules 1, 4 and 6 get an exception** for the last minor of the previous major.
   The exception carries no date.
5. **A 5.x fix carries milestone `5.5`.** Its body says
   `**Base branch:** main, cherry-pick to release/5.5`, or `release/5.5` for a 5.x-only bug.
6. **The self.dev slot narrows to `^(main|release-6\..*)$`** in the change that moves self.dev to 6.x
   (#1875). Otherwise a 5.5.x release would downgrade a V98 database.

### The merge

7. **The ADR 0018 cutover gate moves from the merge to the 6.0.0 GA release.** The gate itself does not
   change: no `Removed in V6` marker left and an empty `markers-baseline.txt`. Its grep exclusions are
   widened so that the 17 test-fixture lines no longer show up.
8. **The cutover runs in this order:**
   1. Freeze.
   2. Cut `release/5.5`.
   3. Fast-forward main to v6, and then to the cutover commit. Both go in one push, so CI runs once.
   4. Delete `v6` once nothing open depends on it.
9. **The cutover commit:**
   - rewrites every use of `v6` as a base branch;
   - removes v6.dev from the pipeline;
   - sets `VERSION=6.0-alpha`;
   - moves the gate (decision 7);
   - amends the patch rules (decision 4).
10. **v6.dev goes away at the merge.** The findings mirror stops by itself, because it is gated on the ref
    being `v6`, and comes back on self.dev with #1875. demo.dev follows main, so it runs 6.x.
11. **No `6.1` issue is picked up until 6.0.0 ships.**

### 6.0 pre-releases

12. **Versions go `6.0-alpha.N`, then `6.0-beta.N`, then `6.0.0`.** This is the existing scheme, used by
    `4.11-alpha.*` and `5.0-beta.*`.
    - Alpha → beta: the `6.0` milestone has no open feature left. Bugs may remain.
    - Beta → GA:
      - the ADR 0018 gate passes;
      - the `6.0` milestone is empty;
      - an upgrade from a real 5.5.x database (floor `V68`) has been run.
13. **What a pre-release publishes:**
    - the Docker Hub version tag (there is no `latest` tag to protect);
    - a GitHub **pre-release**, never marked Latest;
    - the S3 docs under `release/<version>/`;
    - release notes starting at the previous pre-release tag, or at `5.5.7` for the first one. The
      6.0.0 notes start at `5.5.7`.

    It does **not** deploy to self.dev, post on Slack, dispatch `doc.yontrack.com`, or require a wiki
    page.
14. **A pre-release releases its milestone issues.** `/release-milestone 6.0 6.0-alpha.N` moves them to
    `status:released` with "Available in 6.0-alpha.N". The skill needs no change.
