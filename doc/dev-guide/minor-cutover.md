# Minor cutover

Moving `main` from one minor to the next — 5.3 to 5.4 — is one file and one branch deletion. This
page says what happens as a consequence, because most of it is automatic and some of it is easy to
misread.

## The cutover

### 1. Bump `VERSION`

`VERSION` holds `X.Y`, the minor `main` is building towards. Nothing else changes.

```
5.4
```

`VersionCalculator.computeMainVersion` reads it, scans `git tag -l` for `5.4.<n>`, and answers the
next free patch. With no `5.4.*` tag yet that is `5.4.0`, so **the first `main` build after the bump
is `5.4.0-rc-<run>`** — a release candidate for a version that has never been published, which is
exactly what it is. CI produces no bare version at all. Since #2068 the candidate of a 6.x minor is
`6.1.0-rc.<run>`, decided by `VersionCalculator` — see [Versions](release.md#versions).

The bump is Damien's call, not an agent's.

### 2. Retire the previous release branch

Once 5.4.0 is out, 5.3 stops being patched: rule 1 in [Patch releases](patch-release.md) keeps one
live release branch at a time, and it is always the previous minor.

**Delete the git branch:**

```bash
git push origin --delete release/5.3
git branch -D release/5.3   # if you have a local copy
```

**Keep the Yontrack branch.** `release-5.3` holds the builds, validations and promotions for
everything 5.3.x shipped — the audit trail for released software. Deleting it destroys that, and
gains nothing: a branch with no incoming builds costs nothing to keep.

The git tags stay too, forever. `rel_check_unpublished` reads them to refuse republishing a version,
and `rel_previous_version` reads them to bound a changelog.

### 3. Cut the next release branch lazily

Do **not** create `release/5.4` as part of the cutover. Rule 3: release branches are cut from the
released tag when a defect appears, because an always-on release branch pays a full CI run per push
for a branch that may ship nothing.

## What the cutover does not change

* **The demo.** The demo slot admits `main` only (`branchPattern: includes: [main]`,
  `lastBranchOnly: true`), so it follows the new minor from the first `BRONZE` build.
* **The pipeline.** `release/5.4`, when it is eventually cut, gets the same `.yontrack/ci.yaml` as
  `main`, and the branch conditions in it do the rest.
* **The "Latest release" badge.** It moves when 5.4.0 is published, and a later 5.3.x patch does not
  take it back — `resolve` passes `--latest=false` for anything that is not the highest version.

## See also

* [Patch releases](patch-release.md) — fixing the previous minor from a release branch
* [Releasing](release.md) — the release train on `main`
