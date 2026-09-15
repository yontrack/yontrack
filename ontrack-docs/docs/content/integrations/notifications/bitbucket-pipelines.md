# Bitbucket Pipelines

Notifications can trigger a [Bitbucket pipeline](https://support.atlassian.com/bitbucket-cloud/docs/get-started-with-bitbucket-pipelines/)
on some events, using the `bitbucket-pipelines` channel. Since notifications can be used as
[workflow](../workflows/workflows.md) nodes, a pipeline can also be one step of a workflow.

## Configuration

The channel needs a [Bitbucket Cloud configuration](../../start/configuration/bitbucket-cloud.md) and targets a branch of a repository:

```yaml
channel: bitbucket-pipelines
channelConfig:
  config: my-bitbucket-cloud
  workspace: my-workspace
  repository: my-repository
  branch: main
  pipeline: deploy
  variables:
    - name: VERSION
      value: "${build.release}"
  callMode: SYNC
  timeoutSeconds: 600
```

* `pipeline` is the name of a pipeline defined under `pipelines.custom` in `bitbucket-pipelines.yml`. When left empty,
  the default pipeline of the branch is run.
* `variables` are passed as non-secured pipeline variables. A custom pipeline must declare them under its
  `variables:` section. Secrets have no place here: define them as secured repository or workspace variables in
  Bitbucket instead.
* Only branches can be targeted - not tags nor commits.

## Templating

The `workspace`, `repository`, `branch` and `pipeline` fields, as well as the value of every variable, are
[templates](../../appendix/templating.md) rendered against the event, as plain text.

For example, `${promotionLevel}` in a variable value is replaced by the name of the promotion level when the
subscription listens to new promotions.

## Call mode

* `ASYNC` (the default) - Yontrack triggers the pipeline and returns straight away. The notification is successful
  as soon as Bitbucket accepts the pipeline.
* `SYNC` - Yontrack triggers the pipeline and waits for its completion, for at most `timeoutSeconds`. The
  notification is successful only if the pipeline completes as `SUCCESSFUL`; a `FAILED`, `ERROR`, `STOPPED` or
  `EXPIRED` pipeline, or one still running at the timeout, gives an error.

In both modes, the output of the notification contains the pipeline UUID, its build number, the link to its run
and, in `SYNC` mode, its last known state.

While waiting, Yontrack polls the pipeline every 10 seconds, never more often: Bitbucket Cloud allows 1,000 API
requests an hour per token.

## Token scopes

The token of the Bitbucket Cloud configuration must be allowed to trigger and read pipelines:

| Kind of token | Scopes |
|---------------|--------|
| API token     | `read:pipeline:bitbucket` and `write:pipeline:bitbucket` |
| Access token  | **Pipelines**: _Read_ and _Write_ |

Without the write scope, Bitbucket refuses to start the pipeline, and the notification reports the `403` error.

## Build minutes

Every notification starts a real pipeline, which consumes build minutes from the Bitbucket workspace - 50 a month on
the Free plan. Bear this in mind when subscribing to frequent events such as new builds or validations, and
restrict the subscription with keywords or a more specific event when possible.

## See also

* [Bitbucket Pipelines channel reference](../../generated/notifications/notification-backend-bitbucket-pipelines.md)
