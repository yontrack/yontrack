# Releasing

Granting `GOLD` on a build publishes it. `.github/workflows/release.yml` does the publishing and
reports the four validations that grant `RELEASE`. An alpha, a beta and a GA go through the same
steps: see [Pre-releases](#pre-releases) for the few cells that differ.

Nothing is rebuilt. The images are re-tagged from the GHCR tags CI already pushed, the
documentation is the artefact CI already archived, and what ships is the release candidate itself
under its base version — see
[ADR 0006](../../docs/adr/0006-build-identity-through-the-release.md).

## The chain

| Level     | Requires                                                     | Meaning                            |
|-----------|--------------------------------------------------------------|------------------------------------|
| `BRONZE`  | `BUILD`, `UI_UNIT`, `INTEGRATION`, `KDSL.ACCEPTANCE`, `KDSL.HA`, `PLAYWRIGHT`, `DOCS` | The build is green |
| `SILVER`  | `BRONZE` + `DEMO.SMOKE` on `main`; `BRONZE` alone on a release branch | Deployed to the demo and verified — or, on a release branch, green |
| `GOLD`    | `SILVER`, granted **by hand**                                | A human tested the demo and approved the release |
| `RELEASE` | `GOLD` + `DOCKER.HUB`, `GITHUB.RELEASE`, `DOCUMENTATION`, `WIKI` | Publication completed — alpha, beta or GA |

`GOLD` *triggers* publication; `RELEASE` *records* that it succeeded. A promotion whose validations
were its own triggers would be circular, which is why both levels exist. `RELEASE` is a receipt.

Because `RELEASE` requires `GOLD` requires `SILVER` requires `DEMO.SMOKE`, **nothing can be released
until the demo pipeline works**. That is the intended constraint, not a side effect.

`SILVER` is the one level that means something different on a release branch, where it is `BRONZE`
alone: a patch is never deployed to the demo. See [Patch releases](patch-release.md), including why
`.yontrack/ci.yaml` has to declare `SILVER` weakly and add `DEMO.SMOKE` back on `main` rather than
the other way round.

## Releasing a build

Only a build of `main` or of a release branch is released. `release.yml` refuses any other branch
before it looks at anything else — a `v<N>` major branch and a feature branch build development
versions only, see [Major branch](major-branch.md).

1. Watch for the `SILVER` message in `#notifications`. It carries the demo URL and the version.
2. Look at the demo. It is running that exact build against a seeded dataset.
3. Write the release notes of the build's stage in the wiki — `/release-notes <version>` in the
   wiki checkout — and push them. The page is named for the stage, not the build:
   `Release-6.0.0-Alpha` for every alpha, `Release-6.0.0-Beta` for every beta, `Release-6.0.0` for
   the GA, then `Release-6.0.1`, … When the stage moves on, the page is **renamed** to the new
   stage's name, with a "Moved to …" stub left at the old name, and linked from `Home.md` under
   its new name. This happens **before** `GOLD`, not after: it is the only ordering in which the
   GitHub release can link to a page worth reading the moment it is published.
4. Grant `GOLD` on the build.
5. Optionally, once `release.yml` has finished and the build is granted `RELEASE`: start the
   self.dev candidate when you want the release there. A beta or a GA becomes eligible for the
   self.dev slot, and nothing deploys it there automatically — see
   [Keeping an alpha off self.dev](#pre-releases).

That is the whole manual part of the release itself. The `GOLD` promotion workflow dispatches
`release.yml` through the `github-workflow` notification channel, passing the version and the
branch.

### Why `GOLD` uses `dependsOn`, not `promotions`

In `.yontrack/ci.yaml`, `GOLD` is declared as `dependsOn: [SILVER]`. Writing what looks like the
same thing — `promotions: [SILVER]`, the way `SILVER` and `RELEASE` are declared — would break the
gate completely:

| Key | Configurator | Effect |
|---|---|---|
| `promotions` | `AutoPromotionPropertyTypeConfigurator` | Auto-promotion: granted **automatically** once the listed promotions and validations hold |
| `dependsOn` | `PromotionDependenciesPropertyTypeConfigurator` | A prerequisite: nothing grants it, and granting it by hand is **refused** unless the listed promotions hold |

`GOLD` with `promotions: [SILVER]` would be auto-granted the instant `SILVER` landed —
`AutoPromotionPrerequisites.areSatisfied` is true when every listed prerequisite holds, and `SILVER`
would be the only one — so every build that passed the demo smoke tests would publish itself to
Docker Hub. `dependsOn` gives the intended shape: manual only, and still refused before `SILVER`.

## What `release.yml` does

| # | Step | Stamp |
|---|------|-------|
| 1 | Refuse any branch but `main` and a release branch; resolve the build, work out the version, refuse to publish over anything that exists | |
| 2 | Check the docs artefact is still on the CI run | |
| 3 | Check the stage's wiki page exists and is linked from the index | `WIKI` |
| 4 | Re-tag GHCR → Docker Hub, both organisations | `DOCKER.HUB` |
| 5 | Upload the docs artefact to S3 under the released version | `DOCUMENTATION` |
| 6 | Create the GitHub release | `GITHUB.RELEASE` |
| 7 | Rewrite the `release` property to the final version, and record the wiki page as the `wiki-page` meta-info item | |

Step 7 runs before the `GITHUB.RELEASE` stamp, the last `RELEASE` needs: everything keyed off
`RELEASE` reads the build as it is at that instant — the self.dev slot its version, the `#releases`
announcement its page.

Steps 1 to 3 are the ones that can refuse, and they all run before step 4 — the first step that
cannot be undone. An expired docs artefact or a missing wiki page costs a re-run, not a half-done
release.

The logic lives in `scripts/release.sh`, exercised by `scripts/release-test.sh` against stubbed
clients. Most of it is about refusing to run, which is the part worth testing: `docker push` needs
no help.

### Why a workflow of its own

Every publication job used to live in `ci.yml` behind
`needs: [setup, yontrack, build, integration, …]`. A `GOLD` release runs against a commit whose CI
finished days earlier and must re-run none of it, and expressing "all those needs, but skipped" is
how you rebuild by accident. So `release.yml` needs nothing: it starts from a build name and reads
everything else off Yontrack.

### The guards

`resolve` hard-fails when the version is already published — the git tag, or any of
`nemerosa/ontrack`, `nemerosa/ontrack-ui`, `yontrack/yontrack`, `yontrack/yontrack-ui` on Docker
Hub. Silently overwriting a published tag is the worst failure mode in this design and it costs ten
lines to make impossible.

In practice it also self-corrects: `writeVersion` derives the base version from `git tag -l`, so
publishing `5.3.0` makes the next main build compute `5.3.1`. Nothing *enforces* that, which is
exactly why the check is there.

### Versions

`release.yml` publishes under the **base** version: `6.0.1-rc.700` ships as `6.0.1`, and
`6.0.0-beta.7.rc.700` as `6.0.0-beta.7`. The image was built as the base version and displays it
(`info.version.display`, while `info.version.full` keeps the rc version), so it can be published
under nothing else: `resolve` refuses a `release_version` input that differs from the base version.

Every version CI builds is valid SemVer 2.0.0 and a valid Docker tag
([ADR 0020](../../docs/adr/0020-every-build-version-is-semantic.md)):

| Kind | Format | Example |
|---|---|---|
| GA release | `X.Y.Z` | `6.0.1` |
| Pre-release | `X.Y.0-alpha.N`, `X.Y.0-beta.N` | `6.0.0-beta.7` |
| Candidate (`main`, `release/*`, `v<N>`) | `-rc.<run>`, or `.rc.<run>` on a pre-release | `6.0.1-rc.700`, `6.0.0-beta.7.rc.700` |
| Feature branch | `X.Y.0-<branch>-<sha>`, the branch reduced to `[0-9A-Za-z-]` and cut to 124 characters overall (128 with a local `.dev`) | `6.0.0-claude-foo-pipeline-abc1234` |
| Local build | `-dev`, or `.dev` on a pre-release | `6.0.1-dev`, `6.0.0-beta.7.dev` |

`VersionCalculator` computes all of them; `writeVersion` writes the base version to
`build/version.txt` and the candidate to `build/candidate-version.txt`, which `ci.yml` reads. The
legacy shapes — `6.0-alpha.0` to `6.0-beta.6`, and `5.5.N-rc-<run>` from `release/5.5` — are
published and never renamed, and `release.sh` still accepts them.

The rc version is still needed throughout: it is the GHCR tag the images are re-tagged *from*.

Whether the published version takes GitHub's "Latest release" badge is decided rather than defaulted:
`resolve` compares it against every released tag and passes `--latest=<true|false>`. Left at `gh`'s
default of true, a 5.3.2 patch published after 5.4.0 would take the badge and flip the README's
shields.io version to the previous minor. A pre-release is never Latest, and does not count in
the comparison — see [Pre-releases](#pre-releases).

### The changelog

The release body is the same for every release: `## Release notes` with the link to the stage's
wiki page, then `## Changes`. The changes are Yontrack's changelog from:

* the previous `RELEASE` build of the branch, for a pre-release and for a GA following a GA —
  `6.0.0-beta.6` from `6.0.0-beta.5`, `6.0.1` from `6.0.0`;
* the previous GA's build, for a GA following pre-releases of its own `X.Y.Z` — `6.0.0` covers
  everything since `5.5.x`, not just since the last beta. That build is found across the project,
  by its `release` property, since it may sit on another branch.

When Yontrack has nothing to give — a freshly cut release branch, or a previous GA it cannot find —
the git range from the previous release's tag stands in: the previous pre-release of the same `X.Y`
for a pre-release, the previous GA for a GA.

## Pre-releases

A `VERSION` of `X.Y-alpha` or `X.Y-beta` on `main` builds `X.Y.0-alpha.N` or `X.Y.0-beta.N` —
`VersionCalculator` numbers them from the existing tags, starting at 0 and counting the legacy
`X.Y-alpha.N` and `X.Y-beta.N` tags too, so `6.0-beta.6` is followed by `6.0.0-beta.7` — and CI
turns that into the `X.Y.0-beta.N.rc.<run>` candidate.

There are two kinds of build, and only two:

* **Development**: every build of a `v<N>` major branch and of a feature branch, and every rc
  candidate before `GOLD`.
* **Release**: a build granted `GOLD` on `main` or on a release branch, whatever its stage.

Every release goes through the same steps. Only the cells marked *stage* differ:

| | Alpha | Beta | GA (and 6.x patches) |
|---|---|---|---|
| Candidates deployed to | demo.dev, every green build of `main` | same | same; none for a patch |
| `GOLD` | by hand, `main` or `release/*` only | same | same |
| Wiki page, required before `GOLD` | *stage*: `Release-6.0.0-Alpha` | *stage*: the same page renamed `Release-6.0.0-Beta` | *stage*: renamed `Release-6.0.0`; then `Release-6.0.1`, … |
| Git tag, Docker Hub, S3 docs, GitHub release | one per version: `6.0.0-alpha.N` | `6.0.0-beta.N` | `6.0.0` |
| GitHub release body | wiki link + Yontrack changelog | same | same |
| GitHub release flags | *stage*: pre-release, never Latest | *stage*: same | Latest when the highest GA |
| Validations reported | `WIKI`, `DOCKER.HUB`, `DOCUMENTATION`, `GITHUB.RELEASE` | same | same |
| `RELEASE` | ✓ | ✓ | ✓ |
| Slack `#internal-releases`, `#releases` | ✓ | ✓ | ✓ |
| docs.yontrack.com rebuilt | on `RELEASE` | same | same |
| self.dev | *stage*: ✗ | eligible (deployed by hand) | eligible (deployed by hand) |

**Ordering:** `6.0.0-alpha.N` < `6.0.0-beta.M` < `6.0.0`, and `5.5.7` < `6.0.0-alpha.0`. A legacy
pre-release is patch 0: `6.0-beta.6` < `6.0.0-beta.7`.

**The wiki page** is the version with the pre-release number removed and the stage capitalised:
`6.0.0-alpha.3` → `Release-6.0.0-Alpha`, `6.0.0-beta.6` → `Release-6.0.0-Beta`, `6.0.1` →
`Release-6.0.1`. `release.yml` records it on the build as the `wiki-page` meta-info item, which the
`#releases` announcement links.

**Keeping an alpha off self.dev:** every release is granted `RELEASE`, so the self.dev slot tells
the stages apart by the version, with a `versionPattern` admission rule admitting `X.Y.Z` and
`X.Y.Z-beta.N` only. By `RELEASE`, the build's display name is the base version. Eligibility is
all the slot decides: an eligible build is listed on the slot, nothing more. Deploying it is a
human step, like `GOLD`. *Deploy* on the slot creates a candidate, and since the slot has no
`CANDIDATE` workflow, the candidate stays one until someone clicks *Start the deployment*. Its
`RUNNING` workflow then writes the gitops change, with `autoApproval`, and marks the deployment
done.

**docs.yontrack.com** is rebuilt on `RELEASE`, alongside `#internal-releases`. Which versions the
site shows — one entry per stage, `6.0.0 Alpha`, then `6.0.0 Beta`, replaced by the GA — is decided
in `yontrack/doc.yontrack.com`.

**Legacy pre-releases** (`6.0-alpha.N`, `6.0-beta.N`) were published before this process, with no
wiki page and no `RELEASE`. They cannot be re-released, and are left as they are.

**Cutting one:**

1. Set `VERSION` to `X.Y-alpha` (later `X.Y-beta`, then `X.Y`) on `main` and push.
2. Wait for the build to reach `SILVER`, as for any release.
3. Write or rename the stage's wiki page, as in [Releasing a build](#releasing-a-build).
4. Grant `GOLD`.
5. `/release-milestone X.Y X.Y.0-alpha.N` to mark the milestone's ready issues as released in it.

`scripts/security-rescan.sh` still rescans GA releases only: a pre-release is skipped even though it
carries `RELEASE`, and the latest `RELEASE` of a minor is its latest GA.

### From alpha to GA

Each move also renames the stage's wiki page before the next `GOLD`: `Release-X.Y.0-Alpha` becomes
`Release-X.Y.0-Beta`, which becomes `Release-X.Y.0`, each time with a "Moved to …" stub at the old
name.

The stage moves on a change of `VERSION`, against these criteria:

* **Alpha → beta** (`X.Y-alpha` → `X.Y-beta`): the `X.Y` milestone has no open feature left. Bugs
  may remain. From the first beta, releases become eligible for self.dev.
* **Beta → GA** (`X.Y-beta` → `X.Y`), when all three hold:
  * the deprecation gate passes — no `Removed in V<X>` marker left and an empty marker baseline,
    see [the gate](major-branch.md#the-gate);
  * the `X.Y` milestone is empty;
  * an upgrade from a real database of the previous major's last minor has been run — a 5.5.x
    database for 6.0, whose floor is `V68`.

## What is no longer possible

`ci.yml` publishes nothing. The `RELEASE` and `JUST_BUILD_AND_PUSH` dispatch inputs, the
`docker-hub` and `release` jobs, the S3 upload and the `feature/*-publication` escape hatch are all
gone.

The release train runs on `main` and on the one live `release/*` branch, and on nothing else —
`release.yml` refuses any other branch. Patches
to the previous minor go through the same four validations and the same human `GOLD` gate — see
[Patch releases](patch-release.md).

Every full run pushes a durable `:<version>` tag to GHCR, so "I just want an image somewhere" is
satisfied by default. Those inputs were the remaining ways to put an unreviewed image on Docker Hub.

## See also

* [Patch releases](patch-release.md) — releasing 5.3.2 from `release/5.3` once `main` is 5.4
* [Minor cutover](minor-cutover.md) — moving `main` to the next minor and retiring the old release branch
* [Demo smoke test](demo-smoke.md) — how a build reaches `SILVER`
* [Documentation artefact](docs-artifact.md) — how the docs reach the release
* [ADR 0006](../../docs/adr/0006-build-identity-through-the-release.md) — why the released build is an rc build
