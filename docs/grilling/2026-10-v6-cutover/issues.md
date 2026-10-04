# v6 cutover — issue breakdown

Breakdown of [README.md](README.md) into issues, created on 2026-10-04.

All four issues are in `yontrack/yontrack`, milestone `6.0`, label `initiative: v6-cutover`.
Dependencies are recorded as native GitHub dependencies, and as a *Depends on* line at the top of each body.

| # | GitHub | Issue | Base | Labels | Depends on |
|---|--------|-------|------|--------|------------|
| A | [#1987](https://github.com/yontrack/yontrack/issues/1987) | Releasing 6.0 pre-releases: `X.Y-alpha.N` / `X.Y-beta.N` through GOLD | `main` | `type: enhancement`, `extension:ci-config`, `status:todo`, `ready-for-agent` | — |
| B | [#1988](https://github.com/yontrack/yontrack/issues/1988) | Cutover commit: `main` becomes 6.0 | branch off `v6`, **not merged** | `type: enhancement`, `status:todo` | A |
| C | [#1989](https://github.com/yontrack/yontrack/issues/1989) | Cutover: cut `release/5.5`, fast-forward `main` to 6.0, retire `v6`, first alpha | — (checklist) | `status:todo` | A, B |
| D | [#1875](https://github.com/yontrack/yontrack/issues/1875) | Add the self.dev slot narrowing | `main` after cutover | unchanged | — |

Outside this repository:

- [yontrack-chart#124](https://github.com/yontrack/yontrack-chart/issues/124) (*Prepare the main branch for
  version 6…*) needs a 6.0 alpha on Docker Hub. It has a native dependency on **C**, and C closes only once
  `6.0-alpha.0` is published.
- yontrack-infra-gitops:
  - [#176](https://github.com/yontrack/yontrack-infra-gitops/issues/176) removes the v6 environment. It
    was created on 2026-10-04 and has a native dependency on **C**.
  - [#170](https://github.com/yontrack/yontrack-infra-gitops/issues/170) was re-scoped on 2026-10-04 from
    v6.dev to demo.dev and self.dev, and depends on **C**.
    [#171](https://github.com/yontrack/yontrack-infra-gitops/issues/171) was closed as its duplicate.
  - [yontrack-infra-bootstrap#13](https://github.com/yontrack/yontrack-infra-bootstrap/issues/13) was
    re-scoped to demo and self.dev buckets.

---

## A ([#1987](https://github.com/yontrack/yontrack/issues/1987)) — Releasing 6.0 pre-releases: `X.Y-alpha.N` / `X.Y-beta.N` through GOLD

**Base branch:** `main`. This overrides the `6.0` milestone. A must be on main before the cutover, and it
reaches `v6` through the usual main merge.

### Why

`VERSION=6.0-alpha` already builds as `6.0-alpha.N` (`VersionCalculator`), but nothing downstream can release
it:

- `scripts/release.sh` `rel_valid_version` accepts `X.Y.Z` only, so GOLD fails in `resolve`.
- `rel_version_gt` compares fields numerically and errors out on `0-alpha`.
- `gh release create` has no `--prerelease`.
- `rel_is_latest` and `rel_version_tags` ignore pre-release tags, so a pre-release could take the GitHub
  *Latest* badge.
- RELEASE, granted once publication succeeds, triggers three things a pre-release must not do:
  - the self.dev slot, which takes RELEASE builds of `^(main|release-.*)$`;
  - the `#internal-releases` Slack message;
  - the `doc.yontrack.com` `doc.yml` dispatch (`.yontrack/ci.yaml`, `RELEASE:` workflow).

### What

A **pre-release** is a base version matching `^\d+\.\d+-(alpha|beta)\.\d+$`.

- **`release.sh`:**
  - `rel_valid_version` accepts X.Y.Z and pre-release versions.
  - `rel_version_gt` orders them: `6.0-alpha.N` < `6.0-beta.M` < `6.0.0`, and `5.5.7` < `6.0-alpha.0`.
  - `rel_is_latest` is always false for a pre-release.
  - **Release notes:** a pre-release's notes start at the previous pre-release tag of the same `X.Y`
    (alpha or beta), or at the highest X.Y.Z below it when there is none. For the first alpha that is
    `5.5.7`. A GA's notes ignore pre-release tags, as they do today, so 6.0.0 starts at `5.5.7`.
- **`release.yml`:**
  - `gh release create … --prerelease --latest=false` for a pre-release.
  - Docker Hub pushes the version tag, as for any release.
  - The S3 docs go under `release/<version>/`.
- **Wiki:** a pre-release needs no `Release-<v>.md` page. The WIKI validation still has to be reported, or
  RELEASE is never granted. It is reported PASSED with a description saying no page is required.
- **RELEASE side effects** — for a pre-release:
  - no self.dev deployment;
  - no `#internal-releases` Slack message;
  - no `doc.yontrack.com` dispatch.

  How is left to the implementation (see *Open point* below), but it must be visible in
  `.yontrack/ci.yaml` and covered by its comments. The `#releases` `environment-regex`
  (`\d+\.\d+\.\d+(-rc-\d+)?`) already fails to match `6.0-alpha.N-rc-<run>`. That is now intended, so say
  so in its comment.
- **Unchanged:** `security-rescan.sh` keeps rescanning X.Y.Z releases only.
- **`scripts/release-test.sh`:** add cases for validation, ordering, *latest*, the notes base for the first
  alpha, for a later alpha, for alpha → beta and for 6.0.0, and the wiki bypass.
- **`buildSrc` `VersionCalculatorTest`:** add a `6.0-alpha` case next to the existing `5.3-beta` one.
- **`doc/dev-guide/release.md`:** add a *Pre-releases* section covering what differs and how a pre-release
  is cut: set `VERSION` to `X.Y-alpha`, then grant GOLD.

### Open point for the implementer

Self.dev, Slack and doc dispatch can be kept out of a pre-release in two ways:

- Filter each of them on the version (the self.dev slot's admission rules, a condition on the RELEASE
  workflow nodes).
- Have a pre-release record its publication without granting RELEASE.

Prefer the first if the admission rules and workflow nodes can express it, so that RELEASE keeps meaning
"publication completed". Otherwise, record why in the PR description.

### Done when

- `release-test.sh` passes with the new cases.
- A dry run of `resolve` on a `6.0-alpha.0-rc-N` version gets through validation.
- Desktop/mobile: no UI impact, since this is CI only. Demo: not user-visible, nothing to seed.

---

## B ([#1988](https://github.com/yontrack/yontrack/issues/1988)) — Cutover commit: `main` becomes 6.0

**Depends on:** A
**Base branch:** a branch `claude/v6-cutover-pipeline` off `origin/v6`. **Do not merge it into `v6` or
`main`.** C merges it, at the cutover, by fast-forward. Rebase it on `v6`'s final HEAD just before C.

Not `ready-for-agent`: `run-initiative` would land it. Prepare it in a session with Damien and stop at the
pushed branch.

### What

**Base-branch references** — `v6` as a target becomes `main`. Releases of 5.x go to `release/5.5` with
cherry-picks:

- `CLAUDE.md` (branch from `origin/main`).
- `docs/agents/issue-lifecycle.md` (the close comment base).
- `.claude/skills/fix-issue/SKILL.md`: the base follows the milestone. `5.5` means
  `main, cherry-pick to release/5.5` or `release/5.5`, anything else means `main`.
- `.claude/skills/run-initiative/SKILL.md`: the description, the "Never land a `v6` issue on `main`" rule,
  the example `**Base branch:**` line, and the conditional base. It also gets the cherry-pick step for a
  `5.5` base.
- `doc/dev-guide/README.md`, `demo-smoke.md`, `demo-seed.md` and `search-perf-test.md`.
- `doc/dev-guide/major-branch.md` becomes a generic guide for the *next* major branch (`v7`). Its
  *Cutover* section records what this cutover did, with the gate moved (below).

**The v6 environment:**

- `.yontrack/ci.yaml`:
  - drop the v6.dev environment and its slot (`branchPattern ^v6$`, gitops
    `yontrack-v6/yontrack.yaml`, demo-smoke dispatch);
  - drop the `config: '^v6$'` custom config;
  - drop the SEARCH.PERFORMANCE "until v6 is merged" note.
- `.github/workflows/demo-smoke.yml`: drop the `v6` target, the `v6-instance` concurrency group, the
  `V6_*` inputs and secrets, and the v6.dev `DEMO_SEED_URL_PATTERN`.
- `.github/workflows/search-perf.yml` and `scripts/search-perf-validate.sh` (+ its test): the `ref`
  default and `SEARCH_PERF_REF` become `main`.
- `buildSrc` `VersionCalculator.kt`: `MAJOR_BRANCH` stays, because it serves `v7`. Only its comment,
  "`v6` while main is still building 5.x", is updated.
- **Leave as is:** the findings mirror steps, `scripts/security-findings-mirror.sh` and the `guard`, and
  the demo seed's `CI_MIRROR_PROJECT`. They go dormant once there is no `v6` ref, and #1875 removes them.

**Gate and rules:**

- ADR 0018 and `major-branch.md`: the cutover gate (no `Removed in V6` marker, empty
  `markers-baseline.txt`) blocks the **6.0.0 GA release**, not the merge.
- Widen the gate's `git grep` exclusions so that the deprecation test fixtures
  (`DeprecatedConfigurationPropertiesCheckTest`, `DeprecationServiceImplTest`,
  `DeprecationInstrumentationTest`) no longer print.
- `doc/dev-guide/patch-release.md`, as an exception for **the last minor of the previous major**, with no
  date and no user-facing mention:
  - rule 1: it may live next to the current major's release branch;
  - rule 4: a bug that exists only in that major lands on its branch directly;
  - rule 6: it is not retired at the next minor cutover, and retiring it is Damien's call.
- `doc/dev-guide/release.md`: the alpha → beta → GA criteria. Alpha → beta when the milestone has no open
  feature. Beta → GA when:
  - the gate passes;
  - the milestone is empty;
  - an upgrade from a real 5.5.x database has been run.

**Version:** `VERSION` becomes `6.0-alpha`.

**Leave alone:** `docs/grilling/**` is history, and `migration-to-v6.md` (and the links to it) is not a
branch reference.

### Done when

- `git grep -n -w v6` on the branch shows only history, `migration-to-v6` links, GitHub Action pins and
  the dormant mirror that #1875 removes.
- `./gradlew :buildSrc:test` and `scripts/search-perf-validate-test.sh` pass, and the docs build is green.
- The branch is pushed and reviewed, not merged.

---

## C ([#1989](https://github.com/yontrack/yontrack/issues/1989)) — Cutover: cut `release/5.5`, fast-forward `main` to 6.0, retire `v6`, first alpha

**Depends on:** A, B
A checklist run together with Damien. Not `ready-for-agent`.

### Before

- [ ] A is merged on main, its CI is green, and main is merged into `v6`.
- [ ] B is rebased on `v6`'s HEAD and reviewed.
- [ ] No agent session is running, and no `run-initiative` is in flight. Freeze declared.
- [ ] Every open `claude/*` branch based on `v6` is merged into `v6` or abandoned.
- [ ] `git fetch`, then `git merge-base --is-ancestor origin/main origin/v6`, which must succeed.
- [ ] Note main's HEAD against tag `5.5.7`. Any 5.x commit past it ships as `5.5.8` from `release/5.5`.

### Cut

- [ ] `git push origin origin/main:refs/heads/release/5.5`.
- [ ] `git push origin --delete release/5.4`. Keep the Yontrack branch `release-5.4`.
- [ ] `git checkout main && git merge --ff-only origin/v6 && git merge --ff-only claude/v6-cutover-pipeline`.
      Both are fast-forwards.
- [ ] `git push origin main`: one push, one CI run.
- [ ] CI on main is green. The build is `6.0-alpha.0-rc-<run>`, and the guard warns, which is expected.
- [ ] The first `release/5.5` build is green, at version `5.5.8-rc-<run>`.

### Retire `v6`

- [ ] `git push origin --delete v6` and delete the local branch.
- [ ] Remove the `V6_TOKEN`, `V6_USERNAME`, `V6_PASSWORD` secrets and the `V6_URL` variable.
- [ ] Tear down v6.dev: [yontrack-infra-gitops#176](https://github.com/yontrack/yontrack-infra-gitops/issues/176).
- [ ] Disable the Yontrack branch `v6` on self.dev.
- [ ] Rewrite the `**Base branch:** v6` lines in the bodies of open `6.0` and `6.1` issues.

### First alpha

- [ ] Grant GOLD on a main build. `6.0-alpha.0` appears:
  - as a GitHub pre-release, not Latest;
  - on Docker Hub;
  - in the S3 docs.

  It does not appear on self.dev or Slack, and no doc dispatch fires.
- [ ] `/release-milestone 6.0 6.0-alpha.0`.
- [ ] Unblock [yontrack-chart#124](https://github.com/yontrack/yontrack-chart/issues/124).
- [ ] Update the agent memory: the CLAUDE.md split is now on main.
- [ ] Unfreeze.

### Done when

`main` builds 6.0, `release/5.5` builds 5.5.x, `v6` and v6.dev are gone, and `6.0-alpha.0` is on Docker Hub.

---

## D ([#1875](https://github.com/yontrack/yontrack/issues/1875)) — #1875: add the self.dev slot narrowing

Addition to the existing body, under **What**:

> - `.yontrack/ci.yaml`, self.dev slot: narrow `branchPattern` from `^(main|release-.*)$` to
>   `^(main|release-6\..*)$`, in the same change. Once self.dev runs 6.x (database at `V98`), a `5.5.x`
>   RELEASE from `release/5.5` must no longer be deployable there: it would downgrade onto a migrated
>   schema.

And under **Done when**:

> - A RELEASE build of `release-5.5` is no longer eligible for the self.dev slot.

---
