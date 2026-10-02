# GitHub workflows

Notifications can be used to trigger a GitHub Actions workflows on some events.

## Workflow ID

GitHub returns the run of the workflow a notification starts. When it does not, like on an older GitHub Enterprise
Server, Yontrack finds the run back through an `id` input it passes to the workflow, which the workflow uploads as an
artifact:

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

The artifact steps are needed only when GitHub does not return the run. The `id` input, however, must be declared
if and only if it is sent, or GitHub rejects the dispatch.

Whether it is sent is set by the `workflowSendId` field of the
[GitHub configuration](../../start/configuration/github.md#dispatching-workflows), `true` by default, and overridden by
the `sendId` field of the notification. A workflow which does not declare `id` needs `sendId: false`, which works only
with a GitHub returning the run of a dispatch.

## See also

* [GitHub workflows configuration](../../generated/notifications/notification-backend-github-workflow.md)
