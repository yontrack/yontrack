# GitLab pipelines

Notifications can trigger a [GitLab pipeline](https://docs.gitlab.com/ci/pipelines/) on some events, using the
`gitlab-pipeline` channel. Since notifications can be used as [workflow](../workflows/workflows.md) nodes, a
pipeline can also be one step of a workflow.

## Configuration

The channel needs a [GitLab configuration](../../start/configuration/gitlab.md) and targets a branch or a tag
of a project:

```yaml
channel: gitlab-pipeline
channelConfig:
  config: my-gitlab
  project: my-group/my-subgroup/my-project
  ref: main
  variables:
    - name: VERSION
      value: "${build.release}"
  callMode: SYNC
  timeoutSeconds: 600
```

* `project` is the **full path** of the GitLab project, subgroups included - the part of its URL after the host,
  without any `.git`. Yontrack URL-encodes it before calling the API.
* `ref` is a branch or a tag. The pipeline runs the `.gitlab-ci.yml` of that reference.
* `variables` are passed to the pipeline as ordinary CI/CD variables, available to every job. They are **not**
  masked: secrets have no place here, define them as
  [CI/CD variables](https://docs.gitlab.com/ci/variables/) of the GitLab project or group instead.

GitLab pipeline [`inputs`](https://docs.gitlab.com/ci/inputs/) are not supported: they need a `spec:inputs`
header in the target pipeline, while variables work with any pipeline.

## Templating

The `project` and `ref` fields, as well as the value of every variable, are
[templates](../../appendix/templating.md) rendered against the event, as plain text.

For example, `${promotionLevel}` in a variable value is replaced by the name of the promotion level when the
subscription listens to new promotions.

## Call mode

* `ASYNC` (the default) - Yontrack triggers the pipeline and returns straight away. The notification is
  successful as soon as GitLab accepts the pipeline.
* `SYNC` - Yontrack triggers the pipeline and waits for its completion, for at most `timeoutSeconds`. The
  notification is successful only if the pipeline completes with the `success` status; a `failed`, `canceled`,
  `skipped` or `manual` pipeline, or one still running at the timeout, gives an error.

In both modes, the output of the notification contains the identifier of the pipeline, its number inside the
project, the link to it and its last known status.

While waiting, Yontrack polls the pipeline every 10 seconds, never more often. Today's gitlab.com limit is
generous - 2,000 API requests a minute - but the announced tier-aware limits drop the Free tier to a burst of
100 requests a minute.

## Permissions

The token of the GitLab configuration must belong to a user with at least the **Developer** role on the
project, and the **Maintainer** role when `ref` is a [protected branch](https://docs.gitlab.com/user/project/repository/branches/protected/).
The token itself needs the `api` scope.

Yontrack deliberately does not use [pipeline trigger tokens](https://docs.gitlab.com/api/pipeline_triggers/):
they would mean a second secret, per project, for something the configuration's own token already does.

## Compute minutes

Every notification starts a real pipeline, which consumes compute minutes from the GitLab namespace - 400 a
month on the Free tier when GitLab's shared runners are used. Bear this in mind when subscribing to frequent
events such as new builds or validations, and restrict the subscription with keywords or a more specific event
when possible.

GitLab also caps pipeline creation at **25 a minute per project**; beyond that the API answers a `429` and the
notification reports it.

## See also

* [GitLab pipeline channel reference](../../generated/notifications/notification-backend-gitlab-pipeline.md)
* [GitLab configuration](../../start/configuration/gitlab.md)
