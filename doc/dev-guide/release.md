# Releasing

Granting `GOLD` on a build publishes it. `.github/workflows/release.yml` does the publishing and
reports the four validations that grant `RELEASE` — three of them for a pre-release, which is never
granted `RELEASE`: see [Pre-releases](#pre-releases).

Nothing is rebuilt. The images are re-tagged from the GHCR tags CI already pushed, the
documentation is the artefact CI already archived, and what ships is the release candidate itself
under its base version — see
[ADR 0006](../../docs/adr/0006-build-identity-through-the-release.md).

## The chain

| Level     | Requires                                                     | Meaning                            |
|-----------|--------------------------------------------------------------|------------------------------------|
| `BRONZE`  | `BUILD`, `UI_UNIT`, `INTEGRATION`, `KDSL.ACCEPTANCE`, `PLAYWRIGHT`, `DOCS` | The build is green    |
| `SILVER`  | `BRONZE` + `DEMO.SMOKE` on `main`; `BRONZE` alone on a release branch | Deployed to the demo and verified — or, on a release branch, green |
| `GOLD`    | `SILVER`, granted **by hand**                                | A human tested the demo and approved the release |
| `RELEASE` | `GOLD` + `DOCKER.HUB`, `GITHUB.RELEASE`, `DOCUMENTATION`, `WIKI` | Publication of a GA completed |

`GOLD` *triggers* publication; `RELEASE` *records* that it succeeded. A promotion whose validations
were its own triggers would be circular, which is why both levels exist. `RELEASE` is a receipt.

Because `RELEASE` requires `GOLD` requires `SILVER` requires `DEMO.SMOKE`, **nothing can be released
until the demo pipeline works**. That is the intended constraint, not a side effect.

`SILVER` is the one level that means something different on a release branch, where it is `BRONZE`
alone: a patch is never deployed to the demo. See [Patch releases](patch-release.md), including why
`.yontrack/ci.yaml` has to declare `SILVER` weakly and add `DEMO.SMOKE` back on `main` rather than
the other way round.

## Releasing a build

1. Watch for the `SILVER` message in `#notifications`. It carries the demo URL and the version.
2. Look at the demo. It is running that exact build against a seeded dataset.
3. Write the release notes in the wiki — `/release-notes <version>` in the wiki checkout — and
   push them. This happens **before** `GOLD`, not after: it is the only ordering in which the
   GitHub release can link to a page worth reading the moment it is published.
4. Grant `GOLD` on the build.

That is the whole manual part. The `GOLD` promotion workflow dispatches `release.yml` through the
`github-workflow` notification channel, passing the version.

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
| 1 | Resolve the build, work out the version, refuse to publish over anything that exists | |
| 2 | Check the docs artefact is still on the CI run | |
| 3 | Check the wiki release page exists and is linked from the index | `WIKI` |
| 4 | Re-tag GHCR → Docker Hub, both organisations | `DOCKER.HUB` |
| 5 | Upload the docs artefact to S3 under the released version | `DOCUMENTATION` |
| 6 | Create the GitHub release | `GITHUB.RELEASE` |
| 7 | Rewrite the `release` property to the final version | |

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

`release.yml` publishes under the **base** version: `5.3.0-rc-100` ships as `5.3.0`. The image was
built as `5.3.0` and displays that version (`info.version.display`, while `info.version.full` keeps
`5.3.0-rc-100`), so it can be published under nothing else: `resolve` refuses a `release_version`
input that differs from the base version.

The rc version is still needed throughout: it is the GHCR tag the images are re-tagged *from*.

Whether the published version takes GitHub's "Latest release" badge is decided rather than defaulted:
`resolve` compares it against every released tag and passes `--latest=<true|false>`. Left at `gh`'s
default of true, a 5.3.2 patch published after 5.4.0 would take the badge and flip the README's
shields.io version to the previous minor. A pre-release is never Latest — see
[Pre-releases](#pre-releases).

## Pre-releases

A `VERSION` of `X.Y-alpha` or `X.Y-beta` on `main` builds `X.Y-alpha.N` or `X.Y-beta.N` —
`VersionCalculator` numbers them from the existing tags, starting at 0 — and CI turns that into the
usual `X.Y-alpha.N-rc-<run>` candidate. A pre-release is released through `GOLD` like any other
version, and the guards, the Docker Hub tags and the S3 docs under `release/<version>/` are the same.
What differs:

| | GA (`6.0.0`) | Pre-release (`6.0-alpha.2`) |
|---|---|---|
| GitHub release | Latest when it is the highest GA | Marked as a pre-release, never Latest |
| Wiki page | Required, checked, stamped `WIKI` | None: not checked, `WIKI` not stamped |
| Release body | Wiki link, then Yontrack's changelog since the last `RELEASE` | Git log since the previous pre-release |
| `RELEASE` | Granted | **Never granted** |
| self.dev, `#internal-releases`, `#releases`, doc.yontrack.com | Yes | No |

**Ordering:** `6.0-alpha.N` < `6.0-beta.M` < `6.0.0`, and `5.5.7` < `6.0-alpha.0`.

**Notes base:** a pre-release's git log starts at the previous pre-release of the same `X.Y` (alpha
or beta), or at the highest GA below it when it is the first: `6.0-alpha.0` starts at `5.5.7` and
`6.0-beta.0` at the last alpha. A GA ignores pre-releases, so `6.0.0` covers everything since
`5.5.7`.

**Why no `RELEASE`:** a pre-release must not deploy to self.dev, post on Slack or dispatch
doc.yontrack.com, and those cannot be filtered on the version. The self.dev slot's admission rules
see branches and promotions only, and CI never removes the `#releases` subscription that the last
GA build created on `main`. So `release.yml` records the publication with `DOCKER.HUB`,
`DOCUMENTATION` and `GITHUB.RELEASE` and leaves `WIKI` out. `RELEASE` requires all four, so it is
never granted and none of these fire. As a bonus, `6.0.0`'s Yontrack changelog then runs from the
last `RELEASE` on `main` — `5.5.7`, the last GA released from it — rather than from its last beta.

**Cutting one:**

1. Set `VERSION` to `X.Y-alpha` (later `X.Y-beta`, then `X.Y`) on `main` and push.
2. Wait for the build to reach `SILVER`, as for any release.
3. Grant `GOLD`. No wiki page is needed.
4. `/release-milestone X.Y X.Y-alpha.N` to mark the milestone's ready issues as released in it.

`scripts/security-rescan.sh` still rescans GA releases only.

## What is no longer possible

`ci.yml` publishes nothing. The `RELEASE` and `JUST_BUILD_AND_PUSH` dispatch inputs, the
`docker-hub` and `release` jobs, the S3 upload and the `feature/*-publication` escape hatch are all
gone.

The release train runs on `main` and on the one live `release/*` branch, and on nothing else. Patches
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
