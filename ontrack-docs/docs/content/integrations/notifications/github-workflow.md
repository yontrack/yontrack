# GitHub workflows

Notifications can be used to trigger a GitHub Actions workflows on some events.

The workflow declares the `inputs` of the notification, and nothing else:

```yaml
on:
  workflow_dispatch:
    inputs:
      version:
        description: "Version to deploy"
        required: true
        type: string
jobs:
  my-job:
    runs-on: ubuntu-latest
    steps:
      - run: echo "Deploying {{ '${{ inputs.version }}' }}"
```

## Workflow ID

GitHub returns the run of the workflow a notification starts. When it does not, like on an older GitHub Enterprise
Server, Yontrack finds the run back through an `id` input. Sending it is set by the `workflowSendId` field of the
[GitHub configuration](../../start/configuration/github.md#dispatching-workflows), `false` by default, and overridden by
the `sendId` field of the notification.

With `sendId: true`, the workflow must declare the `id` input, or GitHub rejects the dispatch, and upload an
`inputs-<id>.properties` artifact for its run to be found:

```yaml
on:
  workflow_dispatch:
    inputs:
      id:
        description: "Correlation ID"
        required: true
        type: string
jobs:
  my-job:
    runs-on: ubuntu-latest
    steps:
      - name: logging
        run: touch inputs.properties
      - name: artifact
        uses: actions/upload-artifact@v4
        with:
          name: inputs-{{ '${{ inputs.id }}' }}.properties
          path: inputs.properties
          if-no-files-found: error
```

## See also

* [GitHub workflows configuration](../../generated/notifications/notification-backend-github-workflow.md)
