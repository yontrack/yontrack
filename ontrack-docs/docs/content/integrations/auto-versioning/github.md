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

* `dockerImage` - (required) This image defines the environment for the upgrade command to run in
* `dockerCommand` - (required) Command to run in the Docker container
* `commitMessage`  - (required) Commit message to use to commit and push the result of the post-processing
* `version` - (required) the version which is upgraded to
* `config` - GitHub configuration to use for the connection (optional, using defaults if not specified)
* `repository` - GitHub repository (`owner/repo`). To be set to override the default settings
* `branch` - If defined, overrides the default settings for the branch to use when launching the workflow
* `workflow` - If defined, name of the workflow in _this_ repository containing the post-processing (like
  `post-processing.yml`)

The following parameters are <<appendix-templating,templated>>:

* `dockerImage`
* `dockerCommand`
* `commitMessage`
* `branch`

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

The code below shows an example of a workflow suitable for post-processing:

{% raw %}
```yaml
name: post-processing

on:
  # Manual trigger only
  workflow_dispatch:
    inputs:
      id:
        description: "Unique client ID"
        required: true
        type: string
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

jobs:
  processing:
    runs-on: ubuntu-latest
    container:
      image: ${{ inputs.docker_image }}
    steps:
      - name: logging
        run: |
          touch inputs.properties
      - name: artifact
        uses: actions/upload-artifact@v4
        with:
          name: inputs-${{ inputs.id }}.properties
          path: inputs.properties
          if-no-files-found: error
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

    * all mentioned `inputs` are required by Yontrack
    * the `id` input and its output into a local file artifact is required by Yontrack to follow up on the workflow process ( the GitHub API does not allow to explicitly retrieve a workflow run when it's launched) - see the generation & archiving of the `inputs.properties` file in the example above.
    * commit & pushing the changed files is required for the post-processing to be considered complete
    
    The rest of the workflow can be adapted at will.
