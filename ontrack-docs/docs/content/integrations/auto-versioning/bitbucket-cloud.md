# Bitbucket Cloud auto-versioning post-processing

You can delegate the post-processing to a [Bitbucket pipeline](https://support.atlassian.com/bitbucket-cloud/docs/get-started-with-bitbucket-pipelines/).

Yontrack triggers a `custom:` pipeline with the [variables](#pipeline-variables) describing the upgrade, reports
the link to the pipeline run in the auto-versioning audit, and waits for the pipeline to complete. The
post-processing fails when the pipeline does not complete successfully, or does not complete in time.

!!! note

    The Bitbucket Cloud configuration used to trigger the pipeline needs a token with the pipelines write scope.
    See the [Bitbucket Cloud configuration](../../start/configuration/bitbucket-cloud.md).

## Settings

There is a global configuration, and there is a specific configuration at the branch level (in the
`postProcessingConfig` [parameter](auto-versioning.md#configuration)).

For the global configuration, you can go to _Settings > Bitbucket Cloud Auto Versioning Post Processing_ and define
the following attributes:

* _Configuration_ - Default Bitbucket Cloud configuration to use for the connection
* _Workspace_ - Default workspace of the repository containing the pipeline
* _Repository_ - Default repository containing the pipeline
* _Pipeline_ - Name of the custom pipeline containing the post-processing (like `yontrack-auto-versioning`)
* _Branch_ - Branch to run the pipeline on, `main` by default
* _Retries_ - The amount of times we check for the completion of the post-processing pipeline, 10 by default
* _Retry interval_ - The time (in seconds) between two checks for the completion of the post-processing pipeline,
  30 by default and never less than 10 seconds

Yontrack waits at most _Retries_ x _Retry interval_ for the pipeline to complete: 5 minutes by default.

The settings can also be set as code:

```yaml
ontrack:
  config:
    settings:
      bitbucket-cloud-av-post-processing:
        config: bitbucket-cloud
        workspace: my-workspace
        repository: post-processing
        pipeline: yontrack-auto-versioning
        branch: main
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
| `config`        | _Settings_    | Bitbucket Cloud configuration to use for the connection                    |
| `workspace`     | _Settings_    | Workspace of the repository containing the pipeline                        |
| `repository`    | _Settings_    | Repository containing the pipeline                                         |
| `pipeline`      | _Settings_    | Name of the custom pipeline to run                                         |
| `branch`        | _Settings_    | Branch to run the pipeline on                                              |

The `config`, `workspace`, `repository` and `pipeline` must be defined, either at the branch level or in the settings,
otherwise the post-processing fails before any pipeline is triggered.

The `dockerImage`, `dockerCommand` and `commitMessage` parameters, and the branch, are
[templated](../../appendix/templating.md) using the
[auto-versioning templating context](auto-versioning.md#pr-title-and-body). For example:

```yaml
commitMessage: "Upgrading to ${VERSION}"
```

Example of a simple configuration relying on the global settings:

```yaml
postProcessing: bitbucket-cloud
postProcessingConfig:
  dockerImage: eclipse-temurin:21
  dockerCommand: ./gradlew dependencies --write-locks
  commitMessage: "Resolving the dependency locks"
```

Example of a configuration running a pipeline defined in the target repository itself:

```yaml
postProcessing: bitbucket-cloud
postProcessingConfig:
  dockerImage: eclipse-temurin:21
  dockerCommand: ./gradlew dependencies --write-locks
  commitMessage: "Resolving the dependency locks"
  workspace: my-workspace
  repository: my-repository
  pipeline: yontrack-auto-versioning
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

The code below shows an example of a `bitbucket-pipelines.yml` custom pipeline suitable for post-processing:

```yaml
pipelines:
  custom:
    yontrack-auto-versioning:
      - variables:
          - name: REPOSITORY
          - name: UPGRADE_BRANCH
          - name: DOCKER_IMAGE
          - name: DOCKER_COMMAND
          - name: COMMIT_MESSAGE
          - name: VERSION
      - step:
          name: Post-processing
          services:
            - docker
          script:
            # Credentials able to push to the repository to process, as secured repository variables
            - git clone --branch "${UPGRADE_BRANCH}" "https://x-token-auth:${PUSH_TOKEN}@${REPOSITORY#https://}" work
            - cd work
            - docker run --rm -v "$PWD:/work" -w /work "${DOCKER_IMAGE}" sh -c "${DOCKER_COMMAND}"
            - git config user.name "Yontrack post-processing"
            - git config user.email "yontrack@example.com"
            - git add --all
            - git commit -m "${COMMIT_MESSAGE} [skip ci]"
            - git push origin "HEAD:${UPGRADE_BRANCH}"
```

!!! important

    * all the [variables Yontrack sends](#pipeline-variables) must be declared in the custom pipeline
    * committing and pushing the changed files to `UPGRADE_BRANCH` is required for the post-processing to be
      considered complete
    * `[skip ci]` in the commit message prevents the push from starting the other pipelines of the repository

    The rest of the pipeline can be adapted at will.
