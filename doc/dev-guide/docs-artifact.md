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
- **Which run**: found through the GitHub workflow run property on the build — see below.
- **Never empty**: the upload uses `if-no-files-found: error`. An empty artefact would only be
  discovered months later, by the release, on the one day it cannot be fixed by rebuilding.
- **Overwritable**: `overwrite: true`, so re-running the `docs` job alone after a transient failure
  works. Without it, `upload-artifact@v4` rejects the second upload with a 409 and `DOCS` could
  never go green for that run.

## Finding the run an artefact belongs to

The release runs days after the CI run that produced the artefact, so it has to resolve that run
from the build rather than from anything in its own context. It is already recorded: `yontrack ci
config` — the `Register the build` step — sets the **GitHub workflow run property** on every build
through `GitHubCIEngine.configureBuild`, and that property is the source to read.

| Field       | Value                                                       |
|-------------|-------------------------------------------------------------|
| `runId`     | the run id, as a number — what the `gh` API needs            |
| `url`       | the run's URL, `.../actions/runs/<runId>`                    |
| `name`      | the workflow name (`CI`)                                     |
| `runNumber` | the run number, as shown in the Actions UI                   |
| `event`     | the event that started it (`push`, `workflow_dispatch`, ...) |

Build has a field of its own for it, so no property-type name has to be spelled out:

```graphql
{
  build(id: 1234) {
    buildGitHubWorkflowRunProperty {
      value
    }
  }
}
```

`value` is the property's JSON — the run id is `value.workflows[0].runId`, ready to drop into
`gh api repos/yontrack/yontrack/actions/runs/<runId>/artifacts`. Taking the number from here beats
parsing it out of a URL, and searching Actions for the commit SHA would be guesswork anyway:
several runs can share a SHA, and only one of them built the artefact this build was stamped from.

**Run info** — the uniform model shared with validation runs — is deliberately *not* set on the
build. [#1671](https://github.com/yontrack/yontrack/issues/1671) added a step to `ci.yml` to do
exactly that before noticing the property above was already there, recording the same run since
long before, and recording it better: the run id as a number rather than as a path segment to parse
out of a URL. Two mechanisms carrying one fact is a maintenance cost with no reader, so the step
came out again.

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
