# Workflows

Workflows allow the execution of several actions orchestrated in a DAG (directed acyclic graph).

!!! note

    As of version {{ yontrack_version }}, workflows can only be triggered using [notifications](../notifications/index.md).

    There is already some partial and undocumented support through API calls to run some standalone workflows but this is very experimental.

## Workflows definitions

To run a workflow, you can define a notification with channel [
`workflow`](../../generated/notifications/notification-backend-workflow.md).

This can be done through the UI or as code.

A workflow:

* has a name, used for information and display purposes
* has a list of nodes

Each node:

* has an ID which must be unique inside the workflow
* an executor ID that points to a _workflow node executor_
* some data for the _workflow node executor_
* a list of parent nodes

The list of parent nodes is what defines the workflow DAG.

!!! note

    When defining or running workflows, graph cycles are automatically detected.

Workflows notifications can be defined as code, like all other [notifications](../notifications/index.md).

For example:

```yaml
channel: workflow
channelConfig:
workflow:
  name: My workflow
  nodes:
    - id: ticket
      executorId: notification
      data:
      channel: jira-creation
      channelConfig:
      # Configuration for the ticket creation
    - id: mail
      executorId: notification
      parents:
        - id: ticket
      data:
        channel: mail
        channelConfig:
        # Configuration for the mail
        template: |
          Link to ticket: ${workflow.ticket?path=url}
```

## Workflows nodes executors

A _workflow node executor_ is a component which is responsible to "run a node."

See the [reference](../../generated/workflow-node-executors/index.md) for a list of all existing workflow node
executors.

## Workflow templating

Many elements in the workflow definition are subject to [templating](../../appendix/templating.md).

The workflow name is itself considered as a template when being run as a notification.

When
using [notifications as node executors](../../generated/workflow-node-executors/workflow-node-executor-notification.md),
the configuration elements are templates as usual.

Note that for a workflow notification, the event is passed as a context element and all template functions and sources
are available.

Additionally, when a notification is run as part of a workflow, a new templating function is available: [
`workflow`](../../generated/templating/renderables/templating-renderable-workflow.md).

This function allows the access to the output data of any successful node in the workflow.

For example, let's take a workflow which:

* creates a ticket in Jira
* then send a link to this ticket with an email

```yaml
channel: workflow
channelConfig:
  workflow:
    name: My workflow
    nodes:
      - id: ticket
        executorId: notification
        data:
        channel: jira-creation
        channelConfig:
        # Configuration for the ticket creation
      - id: mail
        executorId: notification
        parents:
          - id: ticket
        data:
          channel: mail
          channelConfig:
          # Configuration for the mail
        template: |
          Link to ticket: ${workflow.ticket?path=url}
```

The `ticket` node runs and set some information in its output (see
the [reference](../../generated/notifications/notification-backend-jira-creation.md) for the full details), including a
`url` property.

Then, the `mail` node is run and is using the `notification` _workflow node executor_ again, with the [
`mail` channel](../../generated/notifications/notification-backend-mail.md) being configured to send an email.

This channel can use the `template` for the mail's body and is using the `workflow` function to get the output of the
`ticket` node and the `url` property of its output.

## Workflows management

The progress of running workflows can be accessed in _Information > Workflow audit_.

This page, and the list of workflow instances behind it, need the **administrator** right: a workflow
instance carries the whole event which triggered it, the full workflow definition and the output of
every node, across all projects.

Clicking on a workflow displays more details about its current status, node per node. An
administrator can open any of them; anyone else can open a workflow instance only when they can view
the project its event names.

When using the _workflow notification channel_, the workflow status link is also accessible from the _Information >
Notification recordings_, when selecting the notification.

### Workflows of a promotion

Both routes above need the _notification recording_ access right. The workflows triggered by a promotion are also shown
directly on the **promotion run page**, to anyone who can view the project.

The _Workflows_ section is the primary content of that page. It lists every workflow the promotion has triggered, most
recent first, one card per run. Each card carries the workflow name, its status, its duration and a link to the full
workflow instance page, plus a strip of its nodes: one column per depth in the workflow graph, so nodes which can run
concurrently share a column. When a workflow fails, the error output of the first failed node is shown on the card
itself.

The strip is a summary, not a graph — a column boundary is not an edge, and wide columns are clamped (failed nodes are
never clamped away). Use _Open workflow_ to read the exact graph.

Enable _auto refresh_ on the section to watch a running workflow progress in place.

### Workflows on the delivery map

A branch's [Delivery map](../../concepts/branch-views/index.md#workflows-on-the-map) draws workflows as checkpoints of
their own, so that what a promotion set off is read beside the promotion itself. It shows the workflows of the promotion
run the map names — that build's promotion, and no other — each with its status, how long it took, and a link to the run.

The same view draws the workflows configured on a project's [environment slots](../environments/environments.md), where
the trigger matters: a workflow on `CANDIDATE` or `RUNNING` is a hard gate the deployment cannot get past until it
passes, and the map draws it as a prerequisite of the slot. One that has never run is drawn all the same — it is very
often the reason nothing has ever been deployed there.

## Workflows settings

Workflow statuses are saved by default for 14 days.

To change this value, you can go to _System > Settings > Workflows_.

This can also be defined as code using [CasC](../../configuration/casc.md):

```yaml
ontrack:
  config:
    settings:
      workflows:
        retentionDuration: 14d
```
