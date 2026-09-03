# The build that ships is the release candidate, renamed but never rebuilt

A Yontrack release publishes an **rc build**. Build `5.3.0-rc-100` is the thing
tagged `5.3.0` on Docker Hub, released as `5.3.0` on GitHub and published under
`5.3.0` on the documentation site. No separate release build is ever made.

Three identifiers do three different jobs, and only one of them changes:

| | Example | Mutable | What it is |
|---|---|---|---|
| Build **name** | `20260901055547-100` | never | The build's identity in Yontrack, assigned at registration |
| `release` **property** (display name) | `5.3.0-rc-100` → `5.3.0` | once, at GOLD | The version a human reads |
| Artefact tag | `5.3.0` | n/a | What every published image, release and docs path carries |

The build name is an opaque timestamp-run pair. It is not a version, it is not a
tag, and nothing outside Yontrack ever sees it. The version lives in the
`release` property, which `ci.yml` sets on every build and `release.yml`
rewrites once, at the moment of publication.

## Why

Nothing may be rebuilt at release time. That is the constraint everything else
follows from: what is tested is what ships. `BRONZE` means the build is green,
`SILVER` that it ran on the demo and was verified there, and `GOLD` that a human
looked at that demo and approved it. All three statements are about one set of
artefacts. Producing a second set at release time would make every one of those
statements about something that is no longer being shipped.

So publication is re-tagging and copying: images are re-tagged from the durable
GHCR tag CI pushed, and the documentation is the artefact CI archived. The only
thing that changes between `GOLD` and `RELEASE` is the *name* the artefacts are
published under.

And that name has to be `5.3.0`, not `5.3.0-rc-100`. A candidate suffix is
meaningful inside the pipeline and meaningless outside it — nobody installs
`yontrack/yontrack:5.3.0-rc-100` — so the version the world sees is the base
version, while the build that produced it keeps its candidate identity in
Yontrack forever.

## Considered options

**A separate release build**, which is what the pipeline did before, was ruled
out by the no-rebuild constraint rather than by preference. It is the
obvious-looking option and it is what a reader will expect to find, which is most
of the reason this record exists. Its appeal is that the released thing is named
for the release from birth, with no rewriting anywhere. Its cost is that the
artefacts it publishes were built by a different run from the one that was
tested, demoed and approved — so `BRONZE`, `SILVER` and `GOLD` end up certifying
a build that is not the one that ships, and the demo a human signed off on was
running different bytes.

**Rebuilding at release time from the release commit** has the same defect in a
tidier package and adds a new one: a build is not a pure function of its commit.
Dependency resolution, toolchain and base images all move. Two builds of one
commit weeks apart are not the same artefact, and the second one has been tested
by nobody.

**Making the build name the version** — registering the Yontrack build as
`5.3.0-rc-100` rather than `20260901055547-100` — would remove the indirection
that makes this ADR necessary, and it is tempting for exactly that reason. It was
rejected because the build name is the primary key: it is what `yontrack
validate --build` addresses, what `slot pipeline start` takes, and what every
validation and promotion hangs off. Making it carry a version means either it
cannot be rewritten at `GOLD` — so the released build is addressed by its
candidate name forever, in every URL and every API call — or it can be, and the
primary key is mutable. The display name exists precisely so that the readable
identifier and the stable one can differ.

**Keeping the display name at `5.3.0-rc-100` after release** and letting the
artefacts alone carry `5.3.0` was the cheapest option: one fewer mutation, and
the build reads exactly as it did when it was tested. It was rejected because
Yontrack is the place someone goes to ask "what is in 5.3.0?", and a released
build that does not answer to its release version makes that question
unanswerable from the UI. The changelog boundary — "since the last build promoted
to RELEASE" — reads the same either way, so this was a legibility decision, not a
functional one.

## Consequences

**The version is derived by stripping a suffix.** `release.yml` computes the
published version as the display name with a trailing `-rc-<digits>` removed, and
refuses anything that is not then three dot-separated numbers. A build with no
`release` property has its own name as its display name, and that guard is what
stops a timestamp being published as a version.

**A release cannot be found by its rc name afterwards.** The rewrite is the last
step of `release.yml` for that reason: every step before it resolves the build by
the display name `GOLD` was granted on, so rewriting earlier would break a re-run
of any of them. After a successful release, a re-dispatch with the rc version
fails at resolution rather than doing damage, which is the safe direction.

**The guards matter more than they look.** Since the published version is derived
rather than assigned, the same version can in principle be derived twice — two
candidates of `5.3.0` both reduce to `5.3.0`. In practice `writeVersion` derives
the base version from `git tag -l`, so publishing `5.3.0` makes the next main
build compute `5.3.1`, and the situation does not arise. Nothing *enforces* that,
so `release.yml` hard-fails when the git tag or any of the four Docker Hub tags
already exists. Silently overwriting a published tag is the worst failure this
design can produce, and it is the derivation that makes it possible at all.

**Auto-versioning templates read `${build.release}`, never `${build}`.** The slot
that deploys to the demo needs the GHCR tag, which is the version, not the build
name. This was got wrong once already — the `self` slot's `targetVersion` was
`${build}`, which renders the name — and the shape of this decision is what makes
that error easy to make and easy to miss: both render *something*, and only one
of them is a tag.
