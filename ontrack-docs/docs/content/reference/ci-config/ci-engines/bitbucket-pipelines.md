# Bitbucket Pipelines CI engine (`bitbucket-pipelines`)

The Bitbucket Pipelines [CI engine](../../../configuration/ci-config.md#ci-engines) is the one used when you call
Yontrack from [Bitbucket Pipelines](https://support.atlassian.com/bitbucket-cloud/docs/get-started-with-bitbucket-pipelines/).

It's typically used together with the [`bitbucket-cloud` SCM engine](../scm-engines/bitbucket-cloud.md), which is
detected from the SCM URL this engine provides.

## Detection

The Bitbucket Pipelines CI engine is automatically detected when the `BITBUCKET_BUILD_NUMBER` environment variable is
set (which is done by default in every Bitbucket pipeline).

## Project name

The project name is, by order of priority:

* the value of the `PROJECT_NAME` environment variable if available
* the repository slug in the `BITBUCKET_REPO_SLUG` environment variable

## Branch name

The branch name is, by order of priority:

* the value of the `BRANCH_NAME` environment variable if available
* `PR-<id>` when the `BITBUCKET_PR_ID` environment variable is set - pull request pipelines are therefore
  rejected, like for the other CI engines
* the value of the `BITBUCKET_BRANCH` environment variable

!!! note

    `BITBUCKET_BRANCH` is not set for tag pipelines: in this case, the branch name must be given explicitly with
    `BRANCH_NAME`.

## Build configuration

This engine links the Yontrack build to the Bitbucket Pipelines run, using the "Bitbucket Pipelines run" build
property, which points to `https://bitbucket.org/<workspace>/<repository>/pipelines/results/<build number>`.

The default build name suffix is the value of the `BITBUCKET_BUILD_NUMBER` environment variable, unless `BUILD_NUMBER`
is set.

## Environment variables

Pass the Bitbucket Pipelines variables to Yontrack with the Yontrack CLI:

```shell
yontrack ci config --env-all BITBUCKET_
```

These environment variables are used for:

| Name                        | Description                                                           |
|-----------------------------|-----------------------------------------------------------------------|
| `BITBUCKET_BUILD_NUMBER`    | Detection of the engine, build suffix and link to the pipeline run    |
| `BITBUCKET_GIT_HTTP_ORIGIN` | SCM URL (a `.git` suffix is added if missing)                         |
| `BITBUCKET_COMMIT`          | Git commit associated with the build                                  |
| `BITBUCKET_BRANCH`          | SCM branch name                                                       |
| `BITBUCKET_PR_ID`           | Detection of pull request pipelines                                   |
| `BITBUCKET_REPO_SLUG`       | Project name and link to the pipeline run                             |
| `BITBUCKET_WORKSPACE`       | Link to the pipeline run                                              |
| `BITBUCKET_PIPELINE_UUID`   | UUID of the pipeline run, stored in the build property                |
| `PROJECT_NAME`              | Explicit project name                                                 |
| `BRANCH_NAME`               | Explicit branch name                                                  |
| `VERSION`                   | Build label/release                                                   |
