# Releasing

Granting `GOLD` on a build publishes it. `.github/workflows/release.yml` does the publishing and
reports the four validations that grant `RELEASE`.

Nothing is rebuilt. The images are re-tagged from the GHCR tags CI already pushed, the
documentation is the artefact CI already archived, and what ships is the release candidate itself
under its base version — see
[ADR 0006](../../docs/adr/0006-build-identity-through-the-release.md).

## The chain

| Level     | Requires                                                     | Meaning                            |
|-----------|--------------------------------------------------------------|------------------------------------|
| `BRONZE`  | `BUILD`, `UI_UNIT`, `INTEGRATION`, `KDSL.ACCEPTANCE`, `PLAYWRIGHT`, `DOCS` | The build is green    |
| `SILVER`  | `BRONZE` + `DEMO.SMOKE`                                      | Deployed to the demo and verified  |
| `GOLD`    | `SILVER`, granted **by hand**                                | A human tested the demo and approved the release |
| `RELEASE` | `GOLD` + `DOCKER.HUB`, `GITHUB.RELEASE`, `DOCUMENTATION`, `WIKI` | Publication completed          |

`GOLD` *triggers* publication; `RELEASE` *records* that it succeeded. A promotion whose validations
were its own triggers would be circular, which is why both levels exist. `RELEASE` is a receipt.

Because `RELEASE` requires `GOLD` requires `SILVER` requires `DEMO.SMOKE`, **nothing can be released
until the demo pipeline works**. That is the intended constraint, not a side effect.

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

`release.yml` publishes under the **base** version: `5.3.0-rc-100` ships as `5.3.0`. An explicit
`release_version` input overrides it, for a human at the Actions button — the promotion has no way
to mean one.

The rc version is still needed throughout: it is the GHCR tag the images are re-tagged *from*.

## What is no longer possible

`ci.yml` publishes nothing. The `RELEASE` and `JUST_BUILD_AND_PUSH` dispatch inputs, the
`docker-hub` and `release` jobs, the S3 upload and the `feature/*-publication` escape hatch are all
gone, and the release train is main-only.

Every full run pushes a durable `:<version>` tag to GHCR, so "I just want an image somewhere" is
satisfied by default. Those inputs were the remaining ways to put an unreviewed image on Docker Hub.

## See also

* [Demo smoke test](demo-smoke.md) — how a build reaches `SILVER`
* [Documentation artefact](docs-artifact.md) — how the docs reach the release
* [ADR 0006](../../docs/adr/0006-build-identity-through-the-release.md) — why the released build is an rc build
