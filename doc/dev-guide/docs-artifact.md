# Documentation artefact

The `docs` job in [`ci.yml`](../../.github/workflows/ci.yml) builds the mkdocs site and carries it
forward to the release as a GitHub Actions artefact.

## Why it is built on every run

Nothing may be rebuilt at release time. A release republishes what an earlier CI run already
produced — images are re-tagged, not rebuilt — and the documentation is no different: it has to be
built once, while the build is being made, and carried forward. So the job runs on **every full
run** rather than only when `RELEASE=true`, and archives `ontrack-docs/site/` instead of pushing it
straight to S3. The one exception is a `JUST_BUILD_AND_PUSH` dispatch, which skips every test job
and skips this one too.

The job builds the site itself. `buildDocs` is excluded from `./gradlew build`, because the mkdocs
nav points at `ontrack-docs/docs/content/generated/` — gitignored, and written by the `ontrack-docs`
integration tests. The site is therefore built where its generator runs:

```
./gradlew :ontrack-docs:integrationTest :ontrack-docs:buildDocs
```

## The two stamps

| Stamp           | When                | Means                                                    |
|-----------------|---------------------|----------------------------------------------------------|
| `DOCS`          | every full run      | The site built and was archived as a run artefact         |
| `DOCUMENTATION` | release only        | The site was published to S3 under the released version   |

They are two names for two different events, and `DOCUMENTATION` keeps its name deliberately: it
carries the validation history of every past release, and renaming it would orphan all of it. The
S3 publication stays in `ci.yml` only until [#1673](https://github.com/yontrack/yontrack/issues/1673)
moves it into `release.yml`.

`DOCS` gates `BRONZE` ([#1669](https://github.com/yontrack/yontrack/issues/1669)). That is nearly
free in wall-clock because the job declares `needs: [setup, yontrack]` and nothing else — it
consumes no output of `build`, `integration`, `kdsl` or `ui-tests`, so it runs alongside them.

## The artefact contract

`release.yml` ([#1672](https://github.com/yontrack/yontrack/issues/1672)) has not landed yet, so
today the artefact is produced and read by nobody. It is nonetheless already an interface — this is
the shape that workflow is being written against, and changing it later means changing both sides:

- **Name**: `docs-site`. Fixed, not versioned — the release resolves the *base* version
  (`5.0.42`), which is not the version the CI run was named for (`5.0.42-rc-123`).
- **Layout**: the site's own files at the root of the artefact — `index.html` at the top, not
  `site/index.html`.
- **Which run**: found through the CI run recorded in the build's run info
  ([#1671](https://github.com/yontrack/yontrack/issues/1671)) — see below.
- **Never empty**: the upload uses `if-no-files-found: error`. An empty artefact would only be
  discovered months later, by the release, on the one day it cannot be fixed by rebuilding.
- **Overwritable**: `overwrite: true`, so re-running the `docs` job alone after a transient failure
  works. Without it, `upload-artifact@v4` rejects the second upload with a 409 and `DOCS` could
  never go green for that run.

## Finding the run an artefact belongs to

The release runs days after the CI run that produced the artefact, so it has to resolve that run
from the build rather than from anything in its own context. The `yontrack` job records it as the
build's **run info** — builds are `RunnableEntity`s, so this is the model Yontrack already has for
"where did this come from", and it is the same shape every validation in `ci.yml` reports:

| Field         | Value                                                        |
|---------------|--------------------------------------------------------------|
| `sourceType`  | `github-workflow` — what the GitHub ingestion also records    |
| `sourceUri`   | the run's URL, `.../actions/runs/<run_id>`                    |
| `triggerType` | the event that started it (`push`, `workflow_dispatch`, ...)  |
| `triggerData` | the commit SHA                                                |

`runTime` is null: the run is still going when this is recorded, and the point of it is the URL.
Getting that null takes a small server-side rule, because the CLI cannot send one — its run time is
a plain Go `int` with no `omitempty`, so omitting `--run-time` puts `0` on the wire rather than
nothing. Yontrack normalises a zero run time to none at all (`RunInfoServiceImpl`), so "not
measured" does not read as "instantaneous" and does not feed a 0.0 sample into
`ontrack_run_build_time_seconds` on every run.

Read it back through GraphQL:

```graphql
{
  build(id: 1234) {
    runInfo {
      sourceType
      sourceUri
      triggerType
      triggerData
    }
  }
}
```

The run id is the last path segment of `sourceUri`, which is what
`gh api repos/yontrack/yontrack/actions/runs/<run_id>/artifacts` needs. Searching Actions for the
commit SHA would be guesswork — several runs can share a SHA, and only one of them built the
artefact this build was stamped from.

## The retention deadline

The upload asks for `retention-days: 90`, and that is what turns retention into a real release
deadline: a build left waiting for `GOLD` longer than that loses its documentation.

**90 is a request, not a guarantee.** GitHub clamps `retention-days` to the repository's (or
organisation's) own *Artifact and log retention* setting, so the effective deadline is whichever is
shorter. `yontrack/yontrack` is on the 90-day maximum today — an artefact uploaded without an
explicit `retention-days` expires 90 days later, which is how you check it:

```bash
gh api "repos/yontrack/yontrack/actions/artifacts?per_page=3" \
  --jq '.artifacts[] | {name, created_at, expires_at}'
```

Lower that setting and this page silently becomes wrong: artefacts would expire before the deadline
it describes, and the first symptom is a release that cannot find its docs.

`release.yml` is meant to fail explicitly on a missing artefact — "docs artifact expired for
`<version>`; re-run CI on `<sha>` to regenerate" — rather than quietly publishing a release with no
docs or rebuilding something nobody reviewed. Re-running CI is the deliberate, human-triggered
exception to "nothing is rebuilt at release time".
