# 2026-08 delivery pipeline — issue breakdown

Outcome of the grilling session on [2026-08-delivery.md](2026-08-delivery.md).

> **Corrected 2026-09-01.** As drafted, this document claimed the Yontrack build *name* was
> the rc version (`5.0.42-rc-123`) and therefore the GHCR tag. It is not: build names are
> opaque timestamp-run pairs (`20260901055547-123`), and the version lives in the `release`
> property. Sections 1, 2, 5 and 16 and the "Build identity" summary have been corrected.
> Consequence: slot auto-versioning templates `${build.release}`, never `${build}`.


All 19 issues carry the label `initiative: 2026-08-delivery` and cross-reference their
dependencies, including across repositories.

| # | Issue | Repo |
|---|---|---|
| 1 | [Fix the `self` slot's target version](https://github.com/yontrack/yontrack/issues/1660) | `yontrack/yontrack` |
| 2 | [Publish a durable version tag to GHCR](https://github.com/yontrack/yontrack/issues/1661) | `yontrack/yontrack` |
| 3 | [Align and repoint the demo environment](https://github.com/yontrack/yontrack-infra-gitops/issues/26) | `yontrack/yontrack-infra-gitops` |
| 4 | [Declare the demo environment and slot](https://github.com/yontrack/yontrack/issues/1662) | `yontrack/yontrack` |
| 5 | [Demo slot deployment workflow](https://github.com/yontrack/yontrack/issues/1663) | `yontrack/yontrack` |
| 6 | [`demo-deploy.yml`](https://github.com/yontrack/yontrack/issues/1664) | `yontrack/yontrack` |
| 7 | [Demo seed and reset program](https://github.com/yontrack/yontrack/issues/1665) | `yontrack/yontrack` |
| 8 | [`demo-smoke.yml`](https://github.com/yontrack/yontrack/issues/1666) | `yontrack/yontrack` |
| 9 | [SILVER](https://github.com/yontrack/yontrack/issues/1667) | `yontrack/yontrack` |
| 10 | [Build the docs on every run](https://github.com/yontrack/yontrack/issues/1668) | `yontrack/yontrack` |
| 11 | [Add `DOCS` to BRONZE](https://github.com/yontrack/yontrack/issues/1669) | `yontrack/yontrack` |
| 12 | [Wiki release-notes convention and agent](https://github.com/yontrack/yontrack/issues/1670) | `yontrack/yontrack` |
| 13 | [Record run info on the build](https://github.com/yontrack/yontrack/issues/1671) | `yontrack/yontrack` |
| 14 | [`release.yml`](https://github.com/yontrack/yontrack/issues/1672) | `yontrack/yontrack` |
| 15 | [Retire the old release path](https://github.com/yontrack/yontrack/issues/1673) | `yontrack/yontrack` |
| 16 | [ADR: build identity through the release](https://github.com/yontrack/yontrack/issues/1674) | `yontrack/yontrack` |
| 17 | [`CONTEXT.md`: environment, slot, deployment](https://github.com/yontrack/yontrack/issues/1675) | `yontrack/yontrack` |
| 18 | [Slot pipeline commands in the CLI](https://github.com/nemerosa/ontrack-cli/issues/42) | `nemerosa/ontrack-cli` |
| 19 | [Screenshot capture from the demo](https://github.com/yontrack/yontrack/issues/1676) | `yontrack/yontrack` |

## The design, settled

**Promotion chain** — `BRONZE → SILVER → GOLD → RELEASE`.

| Level | Requires | Meaning |
|---|---|---|
| `BRONZE` | `BUILD`, `UI_UNIT`, `INTEGRATION`, `KDSL.ACCEPTANCE`, `PLAYWRIGHT`, `DOCS` | The build is green |
| `SILVER` | `BRONZE` + `DEMO.SMOKE` | Deployed to the demo environment and verified |
| `GOLD` | `SILVER`, no validations, granted by hand | A human tested the demo and approved the release |
| `RELEASE` | `GOLD` + `DOCKER.HUB`, `GITHUB.RELEASE`, `DOCUMENTATION`, `WIKI` | Publication completed — a receipt, not a trigger |

GOLD *triggers* publication; RELEASE *records* that it succeeded. A promotion whose
validations are its own triggers would be circular, which is why both levels exist.

**Build identity** — the build that gets released is an rc build, because nothing may be
rebuilt. Its Yontrack build *name* is an opaque timestamp-run pair (`20260901055547-123`)
and never changes; the version lives in the `release` property (the display name), which
holds `5.0.42-rc-123` from BRONZE and is rewritten to `5.0.42` at GOLD; every published
artifact carries `5.0.42` and is produced by re-tagging or copying what BRONZE already
built.

**Images** — GHCR, with a durable `:5.0.42-rc-123` tag alongside the existing run-scoped
`:run-<id>`. Packages public, so the cluster needs no pull secret. Demo therefore pulls
from GHCR while self pulls from Docker Hub — a deliberate divergence of exactly two
values.

**Demo deployment** — a Yontrack environment + slot, mirroring the existing `self` slot.
Admission rules: `branchPattern(main, lastBranchOnly)` + `promotion: BRONZE`. Deployment
is *not* automatic on BRONZE: a scheduled/manual GitHub workflow resolves the build and
starts the slot pipeline.

**Demo data** — reproducible, reset through Yontrack's own API (not the database) by a
KDSL seed program that lives in `yontrack/yontrack`, so it versions with the features it
demonstrates.

**Release notes** — hand-quality end-user prose in the GitHub wiki, one page per release,
written by an agent that a human triggers and reviews *before* granting GOLD. The
`WIKI` validation is a check that the page exists, not a publication step.

### Sequencing constraint

Once `RELEASE` requires `GOLD` requires `SILVER` requires `DEMO.SMOKE`, **nothing can be
released until the demo pipeline works**. Phase 3 must therefore not start before phase 2
is green, and issues 14 and 15 have to land together — 14 builds the new publication path,
15 deletes the old one.

---

## Phase 0 — safe to land immediately

### 1. Fix the `self` slot's target version — [yontrack/yontrack#1660](https://github.com/yontrack/yontrack/issues/1660)

`.yontrack/ci.yaml` sets the self slot's auto-versioning `targetVersion: "${build}"`.
`${build}` renders the build *name*, which is an opaque timestamp-run pair
(`20260901055547-140`) — not a version, and not a tag of any kind.

`${build.release}` renders the `release` property (`ReleasePropertyTemplatingSource`,
field `release` on `BUILD`), which `ci.yml` sets on every build
(`yontrack build set-property release "$VERSION"`) and which holds the version self needs.

**Acceptance**: `targetVersion: "${build.release}"`; a release through the *current*
pipeline still deploys self correctly.

---

## Phase 1 — the demo can run a BRONZE build

### 2. Publish a durable version tag to GHCR — [yontrack/yontrack#1661](https://github.com/yontrack/yontrack/issues/1661)

`ci.yml` pushes `:run-<run_id>` for intra-run hand-off only. Add a second tag,
`:<version>` (e.g. `5.0.42-rc-123`), so one identifier spans CI, the Yontrack `release`
property, the gitops diff and the running pod. Keep `:run-<id>` exactly as it is.

The packages stay **private**. An earlier draft made them public so the cluster would
need no pull secret; keeping them private costs one sealed `imagePullSecret` in gitops
(issue 3) and is the deliberate trade.

**Acceptance**: both `ontrack` and `ontrack-ui` carry `:<version>` in GHCR after every
full run; `:run-<id>` still works for the jobs that consume it.

### 3. Align and repoint the demo environment — [yontrack/yontrack-infra-gitops#26](https://github.com/yontrack/yontrack-infra-gitops/issues/26)

`yontrack-demo` is on image `5.0.40` while `yontrack-self` is on `5.2.2`, and is
structurally thinner.

- Point at GHCR: override `image.repository` and `ontrack.ui.image` (the chart's two
  image keys share a single `image.tag`)
- Add an `imagePullSecret` for GHCR — the packages are private, so the demo namespace
  needs a sealed secret holding a read-only `read:packages` token
- Add the CPU/memory limits and requests self has
- Add `persistence.size`
- Enable the MCP release (`helmfile.mcp.enabled`)
- Drop the `ScheduledBackup` — the demo is recreated on every deployment
- Align `chartVersion` with self

Keep deliberately different: Keycloak auth (lets a visitor log in without provisioning an
Auth0 user) and the smaller Postgres volume.

**Acceptance**: demo runs a GHCR-hosted rc image with self's resource shape, pulled with
the sealed `imagePullSecret`; no backup schedule; `task render-yontrack` output committed.

### 4. Declare the demo environment and slot — [yontrack/yontrack#1662](https://github.com/yontrack/yontrack/issues/1662)

In `.yontrack/ci.yaml`, alongside `self.dev.yontrack.com`: a `demo.dev.yontrack.com`
environment (lower `order`) and a slot for the `yontrack` project with admission rules
`branchPattern(includes: [main], lastBranchOnly: true)` and `promotion: BRONZE`.

**Acceptance**: a BRONZE build on main shows as eligible for the demo slot; a build on any
other branch does not.

### 5. Demo slot deployment workflow — [yontrack/yontrack#1663](https://github.com/yontrack/yontrack/issues/1663)

The slot's `RUNNING` workflow, mirroring self's: `auto-versioning` of
`yontrack-helmfile.d/values/yontrack-demo/yontrack.yaml` → `image.tag` with
`targetVersion: "${build.release}"` (the version — this is the GHCR tag), then
`slot-pipeline-deployed`. Both slots template the same way; they differ only in target
path.

**Acceptance**: starting a pipeline for a BRONZE build opens and auto-merges a gitops PR
bumping the demo tag, and the pipeline reaches deployed.

### 6. `demo-deploy.yml` — [yontrack/yontrack#1664](https://github.com/yontrack/yontrack/issues/1664)

Scheduled (nightly) plus `workflow_dispatch` with an optional build display name,
defaulting to the latest BRONZE.

- Resolve: `yontrack build search --project yontrack --branch main --with-promotion BRONZE --count 1 --display-id`
- Start: the `startSlotPipeline(slotId, buildId)` GraphQL mutation — **the CLI has no slot
  commands**, so this goes through `yontrack graphql`
- Skip when the resolved build is already what demo is running, *unless* manually
  dispatched (a deliberate "reset my demo" is legitimate)

**Acceptance**: the nightly deploys the latest BRONZE; a second nightly with no new build
exits early; a manual dispatch always deploys.

---

## Phase 2 — the demo is verified, SILVER exists

### 7. Demo seed and reset program — [yontrack/yontrack#1665](https://github.com/yontrack/yontrack/issues/1665)

A KDSL program that resets the demo **through the Yontrack API** — deleting every project
and recreating the dataset. No Postgres hooks, no PVC churn, and no dependency on ArgoCD
sync timing. Settings stay covered by CasC; users live in Keycloak.

Content: a curated dataset (projects with branches, builds, promotions, validations, an
environment with slots, a dashboard) plus one project seeded from the real changelog since
the last release, so the demo does not go stale between the times someone remembers to
extend it.

**Acceptance**: running it twice in a row yields the same demo state; it is idempotent and
destructive by design.

### 8. `demo-smoke.yml` — [yontrack/yontrack#1666](https://github.com/yontrack/yontrack/issues/1666)

Dispatched by the demo slot workflow through the `github-workflow` notification channel.

1. Poll `https://demo.dev.yontrack.com/rest/info/application` until the reported version
   equals the deployed one, timeout ~10 min. **This poll is the first and most valuable
   smoke test** — the slot marks itself deployed when the gitops PR merges, which is well
   before ArgoCD has synced and the pods are serving.
2. Run the seed program (issue 7)
3. Assert, thinly: Keycloak login succeeds, the seeded project answers over GraphQL, one
   Playwright page renders
4. Report the `DEMO.SMOKE` validation

Deliberately thin. The heavy suites already ran for BRONZE; re-running them here tests the
deployment, not the code, and a fat smoke suite becomes the flaky thing that blocks
releases.

**Acceptance**: `DEMO.SMOKE` lands on the build; a deliberately broken demo fails it.

### 9. SILVER — [yontrack/yontrack#1667](https://github.com/yontrack/yontrack/issues/1667)

CasC: `SILVER` requiring `promotions: [BRONZE]` and `validations: [DEMO.SMOKE]`, so
auto-promotion grants it when the smoke tests pass — no workflow node needed. Add the
smoke dispatch node to the demo slot workflow. Add a Slack notification on SILVER to
`#notifications` carrying the demo URL and the version: this is the message that says "go
look and decide on GOLD". The BRONZE notification stays.

**Acceptance**: a green demo deployment ends with the build at SILVER and one Slack
message naming the demo URL.

---

## Phase 3 — publication moves to GOLD

### 10. Build the docs on every run — [yontrack/yontrack#1668](https://github.com/yontrack/yontrack/issues/1668)

The `docs` job becomes part of every full run instead of release-only: upload
`ontrack-docs/site/` as a GitHub Actions artifact (90-day retention) instead of pushing to
S3, and report a new `DOCS` validation.

Cut its dependencies to `needs: [setup, yontrack]`. It currently declares
`needs: [setup, yontrack, build, integration, integration-report, kdsl, ui-tests, ui-tests-report]`
but runs its own `./gradlew :ontrack-docs:integrationTest :ontrack-docs:buildDocs` from
scratch and uses none of those outputs — so it can be genuinely parallel and gating BRONZE
on it costs almost nothing in wall-clock.

Name it `DOCS`, not `DOCUMENTATION`: that name is taken by the S3 publication at release
time, and renaming it would orphan the validation history on every past build.

**Acceptance**: every full run produces a docs artifact and a `DOCS` stamp.

### 11. Add `DOCS` to BRONZE — [yontrack/yontrack#1669](https://github.com/yontrack/yontrack/issues/1669)

CasC one-liner. Land it after issue 10, never before.

### 12. Wiki release-notes convention and agent — [yontrack/yontrack#1670](https://github.com/yontrack/yontrack/issues/1670)

The wiki's release notes are hand-written end-user prose with screenshots, YAML samples
and warnings — not derivable from commits. So:

- Convention: one page per release, `Release-<version>.md`, reachable from the index.
  The existing `v5-release-notes` (824 lines) and `v4-release-notes` stay as the archive;
  the line is drawn at the next release.
- A `CLAUDE.md` and a `/release-notes <version>` skill in `.claude/` of the wiki checkout
  (which today holds only `settings.local.json`). Run locally, where the conventions,
  prior art and screenshots live, so the agent can match the existing voice by reading its
  neighbours. Human reviews before pushing.
- Primary source: **GitHub issues closed since the last release** — written in user terms
  by construction and already carrying the triage labels. Cross-check against the commit
  changelog for anything that landed without an issue, and treat a change under
  `ontrack-docs/docs/content/` as the strongest signal that something is user-visible.
- The instructions must pin down: audience; hard exclusions (refactors, test changes,
  dependency bumps, internals); page structure; when a `>` warning is warranted; how to
  link `docs.yontrack.com`; the `<version>-<slug>.png` image convention; the index update.

The agent marks where a screenshot would help; the human adds it during review.

**Note**: the release notes are written *before* GOLD is granted — writing them is part of
the same human act as approving the demo. This is the only ordering in which the GitHub
release can link to a page that is worth reading the moment it is published.

**Acceptance**: `/release-notes 5.2.3` produces a page indistinguishable in voice and
structure from the existing `#### 5.2.2` section.

### 13. Record run info on the build — [yontrack/yontrack#1671](https://github.com/yontrack/yontrack/issues/1671)

`ci.yml` is meticulous about run info on every validation and sets none on the build
itself, yet builds are `RunnableEntity`s (`RunnableEntityType.build`) and support it
natively. `release.yml` needs it to find the originating CI run — days later — to download
the docs artifact.

**Acceptance**: a build's run info carries the CI run URL; it is readable via GraphQL.

### 14. `release.yml` — [yontrack/yontrack#1672](https://github.com/yontrack/yontrack/issues/1672)

A **new** workflow, not a branch of `ci.yml`. Every publication job in `ci.yml` hangs off
`needs: [setup, yontrack, build, integration, …]`; a GOLD release runs against a commit
whose CI finished days earlier and must re-run none of it.

Dispatched by a GOLD promotion workflow through the `github-workflow` channel (the same
channel already dispatching `doc.yontrack.com`), with the build name as input. It:

1. Resolves the version — base version by default, explicit override allowed — and
   **hard-fails if the git tag or Docker Hub tag already exists**. Silently overwriting a
   published tag is the worst failure mode in this design and it costs ten lines to make
   impossible.
2. Checks the docs artifact **first**, failing with "docs artifact expired for `<version>`;
   re-run CI on `<sha>` to regenerate" rather than publishing a release with no docs. The
   90-day retention is a real release deadline; this is where the rebuild exception belongs
   — explicit and human-triggered.
3. Re-tags GHCR → Docker Hub (`nemerosa/*` and `yontrack/*`) → `DOCKER.HUB`
4. Uploads the docs artifact to `s3://yontrack-docs/release/<version>/docs/site/` →
   `DOCUMENTATION`
5. Creates the GitHub release, body composed as **the wiki link first, then the generated
   commit changelog** — end-user prose in the wiki, internals in the release where
   developers expect them, neither duplicated → `GITHUB.RELEASE`
6. Verifies the wiki page exists and is linked from the index → `WIKI`
7. Sets the `release` property to the final version (`yontrack build set-property release`)

Those four validations then grant RELEASE by auto-promotion.

**Acceptance**: granting GOLD on an rc build publishes everything under the base version,
rebuilds nothing, and leaves the build at RELEASE with its display name rewritten.

### 15. Retire the old release path — [yontrack/yontrack#1673](https://github.com/yontrack/yontrack/issues/1673)

**Lands together with issue 14.** Delete from `ci.yml`: the `RELEASE` and
`JUST_BUILD_AND_PUSH` inputs and everything keyed off them, the `docker-hub` and `release`
jobs, the S3 upload in `docs`, and every `startsWith(github.ref_name, 'release/')`
condition (the release train is main-only now). The `feature/*-publication` Docker Hub
escape hatch goes too — once every full run pushes a durable GHCR tag, "I just want an
image somewhere" is satisfied by default, and these were the remaining ways to put an
unreviewed image on Docker Hub.

Add the CasC for `GOLD` (manual, `promotions: [SILVER]`, no validations) and its
publication workflow, and restructure `RELEASE` to require `GOLD` plus the four
publication validations.

**Acceptance**: no path remains that publishes to Docker Hub outside `release.yml`.

---

## Phase 4 — follow-ups, not blockers

### 16. ADR: build identity through the release — [yontrack/yontrack#1674](https://github.com/yontrack/yontrack/issues/1674)

The one decision here that is genuinely expensive to reverse: an immutable opaque build
name carrying a mutable display name, and artifacts named for the base version. Worth an
ADR because a future reader *will* wonder why the build whose display name read
`5.0.42-rc-123` is the thing tagged `5.0.42` everywhere else.

### 17. `CONTEXT.md`: environment, slot, deployment — [yontrack/yontrack#1675](https://github.com/yontrack/yontrack/issues/1675)

The glossary has `Promotion level` and `Promotion run` but no entry for `Environment`,
`Slot` or `Slot pipeline`, which this design leans on throughout.

**Not** BRONZE/SILVER/GOLD — those are Yontrack's own delivery process, not the product
domain. The glossary defines what a promotion level *is*; naming this project's particular
levels there would be a category error.

### 18. Slot pipeline commands in the CLI — [nemerosa/ontrack-cli#42](https://github.com/nemerosa/ontrack-cli/issues/42)

`demo-deploy.yml` drives `startSlotPipeline` through raw GraphQL because the CLI has no
slot or pipeline commands. A `yontrack slot pipeline start` would make that workflow (and
anyone else's) readable. Nice-to-have, not a blocker.

### 19. Screenshot capture from the demo — [yontrack/yontrack#1676](https://github.com/yontrack/yontrack/issues/1676)

The demo runs the exact build being released against a seeded dataset, so a Playwright
script could capture named screenshots for the release notes on demand. Attractive, but
it is a second system and should not hold up the delivery pipeline for polish.
