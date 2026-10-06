# GitHub auto-versioning post-processing

You can delegate the post-processing to a GitHub workflow.

There is a global configuration, and there are a specific configuration at the branch level (in the
`postProcessingConfig` [parameter](auto-versioning.md#configuration)).

For the global configuration, you can go to _Settings > GitHub Auto Versioning Post Processing_ and define the following
attributes:

* _Configuration_ - Default GitHub configuration to use for the connection
* _Repository_ - Default repository (like `owner/repository`) containing the workflow to run
* _Workflow_ - Name of the workflow containing the post-processing (like `post-processing.yml`)
* _Branch_ - Branch to launch for the workflow
* _Retries_ - The amount of times we check for successful scheduling and completion of the post-processing job
* _Retry interval_ - The time (in seconds) between two checks for successful scheduling and completion of the
  post-processing job

The `postProcessingConfig` [property](auto-versioning.md#configuration) at the branch level must contain the following
parameters:

| Parameter       | Default value | Description                                                                                                |
|-----------------|---------------|--------------------------------------------------------------------------------------------------------------|
| `dockerImage`   | _Required_    | This image defines the environment for the upgrade command to run in                                       |
| `dockerCommand` | _Required_    | Command to run in the Docker container                                                                     |
| `commitMessage` | _Required_    | Commit message to use to commit and push the result of the post-processing                                 |
| `config`        | _Optional_    | GitHub configuration to use for the connection, using the defaults if not specified                        |
| `repository`    | _Optional_    | GitHub repository (`owner/repo`), to override the default settings                                         |
| `branch`        | _Optional_    | Branch to use when launching the workflow, to override the default settings                                |
| `workflow`      | _Optional_    | Name of the workflow containing the post-processing (like `post-processing.yml`), to override the defaults |
| `parameters`    | _Optional_    | List of extra inputs to pass to the workflow (see [below](#extra-parameters))                              |
| `sendId`        | _Optional_    | Whether to pass the `id` input, to override the [GitHub configuration](#workflow-id)                       |

The `dockerImage`, `dockerCommand` and `commitMessage` parameters, and every `parameters` value, are
[templated](../../appendix/templating.md) using the
[auto-versioning templating context](auto-versioning.md#pr-title-and-body). For example:

```yaml
commitMessage: "Upgrading to ${VERSION}"
```

The `workflow` branch configuration property can be used to set the post-processing workflow to one in the very branch
targeted by the auto versioning process.
This would override the global settings.

Example of a simple configuration relying on the global settings:

```yaml
postProcessing: github
postProcessingConfig:
    dockerImage: openjdk:11
    dockerCommand: ./gradlew dependencies --write-locks
    commitMessage: "Resolving the dependency locks"
```

### Transient failures

The call starting the workflow is made up to three times in a row, waiting 2 then 4 seconds, when GitHub answers
with a `502`, `503` or `504` HTTP error, or when Yontrack cannot connect to GitHub. Any other error, like a `4xx`, is
not retried, and neither is a read timeout: GitHub has then most likely started the workflow already.

!!! note

    GitHub may answer with an error after having actually accepted the start of the workflow. Retrying the call may
    then start the workflow twice. Yontrack follows the run of the call which succeeded, but the other run goes on.

Once started, the workflow run is the one GitHub returns. When GitHub does not return it, the run is looked up in the
list of runs of the repository, as many times as the _Retries_ setting says and with its _Retry interval_ between two
attempts - see [Workflow ID](#workflow-id).

A client error (`4xx`) while looking for the run, like a missing permission, fails the post-processing right away.

If the call still fails after its three attempts, or if the run cannot be found in time, the post-processing fails on
a _transient_ error: the whole auto-versioning request can then be
[retried automatically](auto-versioning.md#automatic-retries), if enabled.

!!! warning

    When the run of the workflow cannot be found in time, it may still exist and complete later, pushing to the
    upgrade branch while the automatic retry recreates it. The delay before an automatic retry makes this unlikely.

A workflow run which completes without success, or which does not complete in time, is a final failure and is never
retried automatically.

### Extra parameters

On top of the [inputs Yontrack always sends](#workflow-inputs), arbitrary inputs can be passed to the workflow:

```yaml
postProcessing: github
postProcessingConfig:
    dockerImage: openjdk:11
    dockerCommand: ./gradlew dependencies --write-locks
    commitMessage: "Resolving the dependency locks"
    parameters:
      - name: source_release
        value: ${sourceBuild.release}
```

Each value is templated. An extra parameter using the name of a built-in input overrides it.

!!! warning

    Every input passed to the workflow — including the extra parameters — must be declared in the workflow's
    `workflow_dispatch.inputs` section, otherwise GitHub rejects the dispatch.

## Workflow inputs

Yontrack launches the workflow with the following inputs:

| Input            | Description                                                                        |
|------------------|-------------------------------------------------------------------------------------|
| `id`             | Unique client ID, to find the run back - only when sent, see [Workflow ID](#workflow-id) |
| `repository`     | Repository to process, like `yontrack/yontrack`                                     |
| `upgrade_branch` | Branch containing the changes to process                                            |
| `docker_image`   | This image defines the environment for the upgrade command to run in                |
| `docker_command` | Command to run in the Docker container                                              |
| `commit_message` | Commit message to use to commit and push the result of the post-processing          |
| `version`        | The version which is upgraded to                                                    |

The code below shows an example of a workflow suitable for post-processing:

{% raw %}
```yaml
name: post-processing

on:
  # Manual trigger only
  workflow_dispatch:
    inputs:
      repository:
        description: "Repository to process, like 'yontrack/yontrack'"
        required: true
        type: string
      upgrade_branch:
        description: "Branch containing the changes to process"
        required: true
        type: string
      docker_image:
        description: "This image defines the environment for the upgrade command to run in"
        required: true
        type: string
      docker_command:
        description: "Command to run in the Docker container"
        required: true
        type: string
      commit_message:
        description: "Commit message to use to commit and push the result of the post processing"
        required: true
        type: string
      version:
        description: "The version which is upgraded to"
        required: true
        type: string

jobs:
  processing:
    runs-on: ubuntu-latest
    container:
      image: ${{ inputs.docker_image }}
    steps:
      - name: checkout
        uses: actions/checkout@v3
        with:
          repository: ${{ inputs.repository }}
          ref: ${{ inputs.upgrade_branch }}
          token: ${{ secrets.YONTRACK_AUTO_VERSIONING_POST_PROCESSING }}
      - name: processing
        run: ${{ inputs.docker_command }}
      - name: publication
        run: |
          git config --local user.email "<some email>"
          git config --local user.name "<some name>"
          git add --all
          git commit -m "${{ inputs.commit_message }}"
          git push origin "${{ inputs.upgrade_branch }}"
```
{% endraw %}

!!! important

    * all the [inputs Yontrack sends](#workflow-inputs) must be declared, otherwise GitHub rejects the dispatch
    * the `id` input is declared only when Yontrack sends it, which it does not by default - see [Workflow ID](#workflow-id)
    * commit & pushing the changed files is required for the post-processing to be considered complete

    The rest of the workflow can be adapted at will.

### Workflow ID

GitHub returns the run of the workflow it has started. When it does not, like on an older GitHub Enterprise Server,
Yontrack finds the run back through an `id` input. Sending it is set by the `workflowSendId` field of the
[GitHub configuration](../../start/configuration/github.md#dispatching-workflows), `false` by default, and overridden by
the `sendId` parameter.

With `sendId: true`, the workflow must declare the `id` input, or GitHub rejects the dispatch, and upload an
`inputs-<id>.properties` artifact for its run to be found:

{% raw %}
```yaml
on:
  workflow_dispatch:
    inputs:
      id:
        description: "Unique client ID"
        required: true
        type: string
      # ... the other inputs
jobs:
  processing:
    steps:
      - name: logging
        run: touch inputs.properties
      - name: artifact
        uses: actions/upload-artifact@v4
        with:
          name: inputs-${{ inputs.id }}.properties
          path: inputs.properties
          if-no-files-found: error
      # ... the other steps
```
{% endraw %}
