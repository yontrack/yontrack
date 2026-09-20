# GitLab auto-versioning post-processing

You can delegate the post-processing to a [GitLab pipeline](https://docs.gitlab.com/ci/pipelines/).

Yontrack triggers a pipeline on a project and a ref, with the [variables](#pipeline-variables) describing the
upgrade, reports the link to the pipeline in the auto-versioning audit, and waits for the pipeline to complete.
The post-processing fails when the pipeline does not complete successfully, or does not complete in time.

!!! note

    The GitLab configuration used to trigger the pipeline needs a token with the `api` scope, and the Developer
    role on the project - Maintainer when the ref is a protected branch. See the
    [GitLab configuration](../../start/configuration/gitlab.md).

Unlike Bitbucket Cloud, GitLab has no _named_ custom pipeline: what runs is the `.gitlab-ci.yml` of the project
on the ref. The post-processing job selects itself with a `rules:` clause on the variables Yontrack sends -
see the [example](#pipeline-definition) below.

## Settings

There is a global configuration, and there is a specific configuration at the branch level (in the
`postProcessingConfig` [parameter](auto-versioning.md#configuration)).

For the global configuration, you can go to _Settings > GitLab Auto Versioning Post Processing_ and define
the following attributes:

* _Configuration_ - Default GitLab configuration to use for the connection
* _Project_ - Default full path of the GitLab project containing the pipeline, like `group/subgroup/project`
* _Ref_ - Branch or tag to run the pipeline on, `main` by default
* _Retries_ - The amount of times we check for the completion of the post-processing pipeline, 10 by default
* _Retry interval_ - The time (in seconds) between two checks for the completion of the post-processing pipeline,
  30 by default and never less than 10 seconds

Yontrack waits at most _Retries_ x _Retry interval_ for the pipeline to complete: 5 minutes by default.

The settings can also be set as code:

```yaml
ontrack:
  config:
    settings:
      gitlab-av-post-processing:
        config: gitlab
        project: my-group/post-processing
        ref: main
        retries: 10
        retriesDelaySeconds: 30
```

## Branch configuration

The `postProcessingConfig` [property](auto-versioning.md#configuration) at the branch level accepts the following
parameters:

| Parameter       | Default value | Description                                                                |
|-----------------|---------------|----------------------------------------------------------------------------|
| `dockerImage`   | _Empty_       | This image defines the environment for the upgrade command to run in       |
| `dockerCommand` | _Empty_       | Command to run in the Docker container                                     |
| `commitMessage` | _Empty_       | Commit message to use to commit and push the result of the post-processing |
| `config`        | _Settings_    | GitLab configuration to use for the connection                             |
| `project`       | _Settings_    | Full path of the GitLab project containing the pipeline                    |
| `ref`           | _Settings_    | Branch or tag to run the pipeline on                                       |

The `config` and the `project` must be defined, either at the branch level or in the settings, otherwise the
post-processing fails before any pipeline is triggered.

The `dockerImage`, `dockerCommand` and `commitMessage` parameters, and the ref, are
[templated](../../appendix/templating.md) using the
[auto-versioning templating context](auto-versioning.md#pr-title-and-body). For example:

```yaml
commitMessage: "Upgrading to ${VERSION}"
```

Example of a simple configuration relying on the global settings:

```yaml
postProcessing: gitlab
postProcessingConfig:
  dockerImage: eclipse-temurin:21
  dockerCommand: ./gradlew dependencies --write-locks
  commitMessage: "Resolving the dependency locks"
```

Example of a configuration running a pipeline defined in the target project itself:

```yaml
postProcessing: gitlab
postProcessingConfig:
  dockerImage: eclipse-temurin:21
  dockerCommand: ./gradlew dependencies --write-locks
  commitMessage: "Resolving the dependency locks"
  project: my-group/my-project
  ref: main
```

## Pipeline variables

Yontrack triggers the pipeline with the following variables:

| Variable         | Description                                                                 |
|------------------|-----------------------------------------------------------------------------|
| `REPOSITORY`     | URI of the repository to process                                            |
| `UPGRADE_BRANCH` | Branch containing the changes to process                                    |
| `DOCKER_IMAGE`   | This image defines the environment for the upgrade command to run in        |
| `DOCKER_COMMAND` | Command to run in the Docker container                                      |
| `COMMIT_MESSAGE` | Commit message to use to commit and push the result of the post-processing  |
| `VERSION`        | The version which is upgraded to                                            |

The variable names are uppercase, like the ones of the
[Bitbucket Cloud post-processing](bitbucket-cloud.md#pipeline-variables), and unlike the lowercase inputs of the
[GitHub post-processing](github.md).

!!! warning

    The variables are **not** masked. A credential the pipeline needs - like the token used to push back to the
    upgrade branch - belongs in a masked CI/CD variable of the GitLab project, never in the post-processing
    configuration.

## Pipeline definition

The code below shows an example of a `.gitlab-ci.yml` job suitable for post-processing. The `rules:` clause is
what keeps the job out of the ordinary pipelines of the project.

```yaml
yontrack-auto-versioning:
  rules:
    - if: $UPGRADE_BRANCH
  image: docker:latest
  services:
    - docker:dind
  script:
    # PUSH_TOKEN is a masked CI/CD variable of this project, able to push to the repository to process
    - apk add --no-cache git
    - git clone --branch "${UPGRADE_BRANCH}" "https://oauth2:${PUSH_TOKEN}@${REPOSITORY#https://}" work
    - cd work
    - docker run --rm -v "$PWD:/work" -w /work "${DOCKER_IMAGE}" sh -c "${DOCKER_COMMAND}"
    - git config user.name "Yontrack post-processing"
    - git config user.email "yontrack@example.com"
    - git add --all
    - git commit -m "${COMMIT_MESSAGE} [skip ci]"
    - git push origin "HEAD:${UPGRADE_BRANCH}"
```

!!! important

    * committing and pushing the changed files to `UPGRADE_BRANCH` is required for the post-processing to be
      considered complete
    * `[skip ci]` in the commit message prevents the push from starting the other pipelines of the repository

    The rest of the pipeline can be adapted at will.
