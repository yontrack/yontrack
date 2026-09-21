# GitLab CI engine (`gitlab-ci`)

The GitLab CI [CI engine](../../../configuration/ci-config.md#ci-engines) is the one used when you call Yontrack
from a [GitLab CI/CD](https://docs.gitlab.com/ci/) pipeline.

It's typically used together with the [`gitlab` SCM engine](../scm-engines/gitlab.md), which is detected from the
SCM URL this engine provides.

## Detection

The GitLab CI engine is automatically detected when the `GITLAB_CI` environment variable is set to `true` (which is
done by default in every GitLab job).

## Project name

The project name is, by order of priority:

* the value of the `PROJECT_NAME` environment variable if available
* the value of the `CI_PROJECT_NAME` environment variable - the project name without its namespace

## Branch name

The branch name is, by order of priority:

* the value of the `BRANCH_NAME` environment variable if available
* `PR-<iid>` when the `CI_MERGE_REQUEST_IID` environment variable is set - merge request pipelines are therefore
  rejected, like for the other CI engines
* the value of the `CI_COMMIT_REF_NAME` environment variable

!!! note

    `CI_COMMIT_BRANCH` is _not_ set for merge request and tag pipelines, which is why `CI_COMMIT_REF_NAME` is the
    variable being read. In a tag pipeline, `CI_COMMIT_REF_NAME` is the tag name: give the branch name explicitly
    with `BRANCH_NAME` if that is not what you want.

## SCM URL

The SCM URL is the value of the `CI_PROJECT_URL` environment variable, with a `.git` suffix added.

!!! warning

    `CI_REPOSITORY_URL` is **never** used by this engine, and you should not pass it to Yontrack either. GitLab sets
    it to `https://gitlab-ci-token:<job token>@<host>/<path>.git`: using it as the SCM URL would store the job token
    in the project's configuration, where it would also be displayed. `CI_PROJECT_URL` names the same repository
    without any credential.

This works for gitlab.com and for self-managed instances alike: the [`gitlab` SCM engine](../scm-engines/gitlab.md)
matches the URL against the GitLab configurations registered in Yontrack.

## Build configuration

This engine links the Yontrack build to the GitLab pipeline, using the "GitLab pipeline" build property, which
points to the value of `CI_PIPELINE_URL`.

The default build name suffix is the value of the `CI_PIPELINE_IID` environment variable, unless `BUILD_NUMBER`
is set.

## Environment variables

Pass the GitLab CI/CD variables to Yontrack with the Yontrack CLI:

```shell
yontrack ci config \
  --file .yontrack/ci.yaml \
  --env GITLAB_CI="$GITLAB_CI" \
  --env CI_PROJECT_URL="$CI_PROJECT_URL" \
  --env CI_PROJECT_PATH="$CI_PROJECT_PATH" \
  --env CI_PROJECT_NAME="$CI_PROJECT_NAME" \
  --env CI_COMMIT_SHA="$CI_COMMIT_SHA" \
  --env CI_COMMIT_REF_NAME="$CI_COMMIT_REF_NAME" \
  --env CI_MERGE_REQUEST_IID="$CI_MERGE_REQUEST_IID" \
  --env CI_PIPELINE_ID="$CI_PIPELINE_ID" \
  --env CI_PIPELINE_IID="$CI_PIPELINE_IID" \
  --env CI_PIPELINE_URL="$CI_PIPELINE_URL"
```

!!! note

    Unlike the other CI engines, the variables are listed one by one rather than passed wholesale with
    `--env-all CI_`. The `CI_` namespace contains `CI_REPOSITORY_URL` and `CI_JOB_TOKEN`, which both carry the
    job token: none of them is used by this engine, and there is no reason to send them to Yontrack at all. Each
    `--env` takes a `KEY=VALUE` pair: the CLI rejects a bare variable name.

These environment variables are used for:

| Name                   | Description                                             |
|------------------------|---------------------------------------------------------|
| `GITLAB_CI`            | Detection of the engine                                  |
| `CI_PROJECT_URL`       | SCM URL (a `.git` suffix is added)                       |
| `CI_PROJECT_PATH`      | Project path stored in the pipeline build property       |
| `CI_PROJECT_NAME`      | Project name                                             |
| `CI_COMMIT_SHA`        | Git commit associated with the build                     |
| `CI_COMMIT_REF_NAME`   | SCM branch name                                          |
| `CI_MERGE_REQUEST_IID` | Detection of merge request pipelines                     |
| `CI_PIPELINE_ID`       | ID of the pipeline, stored in the build property         |
| `CI_PIPELINE_IID`      | Build suffix, and IID stored in the build property       |
| `CI_PIPELINE_URL`      | Link to the pipeline, stored in the build property       |
| `PROJECT_NAME`         | Explicit project name                                    |
| `BRANCH_NAME`          | Explicit branch name                                     |
| `VERSION`              | Build label/release                                      |

## See also

* [Feeding Yontrack from GitLab CI/CD](../../../start/feeding/gitlab.md)
* [`gitlab` SCM engine](../scm-engines/gitlab.md)
* [Setting up GitLab](../../../start/configuration/gitlab.md)
