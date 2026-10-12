# Every build version is a semantic version and a Docker tag

Up to 6.0 beta 6, only a GA version (`5.5.8`, `6.0.0`) was valid [SemVer 2.0.0](https://semver.org).
A pre-release had two numeric components (`6.0-beta.5`), a candidate appended `-rc-<run>` as
**one** alphanumeric identifier — compared as text, so `rc-1000` sorted below `rc-999` — and a
feature-branch version kept `_` and `.` from the branch name. Since #2068, every version CI
produces on `main`, `release/6.*`, `v<N>` and a feature branch is valid SemVer 2.0.0 **and** a
valid Docker tag (`[A-Za-z0-9_.-]`, at most 128 characters):

| Kind | Format | Example |
|---|---|---|
| GA release | `X.Y.Z` | `6.0.1` |
| Pre-release | `X.Y.0-alpha.N`, `X.Y.0-beta.N` | `6.0.0-beta.7` |
| Candidate (`main`, `release/*`, `v<N>`) | `-rc.<run>` on a version with no pre-release, `.rc.<run>` on a pre-release | `6.0.1-rc.700`, `6.0.0-beta.7.rc.700` |
| Feature branch | `X.Y.0-<branch>-<sha>` | `6.0.0-claude-foo-pipeline-abc1234` |
| Local build (`CI` unset) | `-dev`, or `.dev` on a version that has a pre-release | `6.0.1-dev`, `6.0.0-beta.7.dev` |

`VersionCalculator` computes all of them, including the candidate, which `writeVersion` writes to
`build/candidate-version.txt` for `ci.yml` to read: the candidate shape used to be a `case` in the
workflow, out of reach of any test, and `VersionCalculatorTest` now runs every shape against the
semver.org regex and the Docker tag one.

## Decisions

**`rc.<run>`, not `rc-<run>`.** Two identifiers, the second purely numeric, so that the run number
compares as a number.

**A pre-release candidate sorts above its pre-release** (`6.0.0-beta.7` < `6.0.0-beta.7.rc.700`).
An accepted wart: only `+build` metadata could place it below, and a Docker tag refuses `+`.
Candidates live on GHCR only, and nothing orders them against releases.

**No `+build` metadata anywhere**, for the same reason.

**The `VERSION` file keeps its format** (`6.0`, `6.0-alpha`, `6.0-beta`): it names a line, not a
version.

**The feature branch is one identifier.** The stage of `VERSION` is dropped, any run of
characters outside `[0-9A-Za-z-]` becomes one `-`, the hyphens left at either end go, and the
branch — never the base version nor the SHA — is cut so that the whole version fits in 128
characters, with room left for the `.dev` of a local build, whose `jibDockerBuild` tags an image
too. The SHA is joined by a hyphen, so an all-digit SHA is never a numeric identifier of
its own, where a leading zero would be illegal.

**Numbering continues across the switch.** The first new-shape beta follows the last legacy one:
`VersionCalculator` counts both the `X.Y-stage.N` and the `X.Y.0-stage.N` tags.

**Legacy versions stay.** `6.0-alpha.0` … `6.0-beta.6` are published — git tags, Docker Hub,
GitHub releases — and are never renamed. Every parser accepts them: `scripts/release.sh` orders a
legacy pre-release as patch 0 (`6.0-beta.6` < `6.0.0-beta.7`), and still strips a legacy
`-rc-<run>`.

**5.5 is not touched.** `release/5.5` keeps its versions and its own `ci.yml`. But `release.yml`
always runs from `main`, so `main`'s `scripts/release.sh` keeps releasing `5.5.N-rc-R` builds
exactly as before — `release-test.sh` pins it.

## Consequences

**Every alpha and beta is a GitHub pre-release**, never Latest, whatever its shape; a GA never is.

**The `#releases` subscription** (`.yontrack/ci.yaml`) matches `\d+\.\d+\.\d+(-rc\.\d+)?`, the GA
candidate shape. A pre-release candidate does not match, as before.

**Other repositories that parse versions must accept the new shape first.** doc.yontrack.com
(yontrack/doc.yontrack.com#5) must accept `6.0.0-beta.N` before the first new-shape pre-release is
granted GOLD, or the site rebuild after that release fails.

See also [ADR 0006](0006-build-identity-through-the-release.md), which this changes the candidate
shape of.
