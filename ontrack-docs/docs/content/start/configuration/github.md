# Configuring Yontrack for GitHub

In this section, we'll see how to configure Yontrack for GitHub workflows.

> Don't forget to check the [Configuration as Code](../../configuration/casc.md) section
> to see how to configure Yontrack using CasC.

Yontrack needs to be able to access the GitHub API. Create the GitHub token with the following permissions:

* `repo`
* `read:user`, `user:email`

Create a secret in the same namespace as Yontrack with the name `yontrack-github` and with the value of the GitHub token
in the `token` key.

In your _Yontrack Casc files_, define:

{% raw %}
```yaml
ontrack:
  config:
    github:
      name: github
      url: https://github.com
      oauth2Token: {{ secrets.yontrack-github.token }}
```
{% endraw %}

In the _Helm chart values_ for your Yontrack installation, declare this secret:

```yaml
ontrack:
  casc:
    secrets:
      mapping: file
      names:
        - yontrack-github
```

When Yontrack is restarted, it will be able to access the GitHub API. You can check this by navigating to your user menu
at _Configurations_ > _GitHub configurations_. You should see the GitHub configuration you just created and you can test
it by using the :octicons-question-16: button.

## Dispatching workflows

The [auto-versioning post-processing](../../integrations/auto-versioning/github.md) and the
[GitHub workflow notifications](../../integrations/notifications/github-workflow.md) dispatch GitHub workflows, and
Yontrack follows the run each dispatch creates.

GitHub returns this run in its answer to the dispatch. When it does not, like on an older GitHub Enterprise Server
(Yontrack dispatches again without asking for the run if GitHub rejects the request), Yontrack searches for the run using an `id` input it passes to the workflow, which the workflow uploads as an
artifact.

The `workflowSendId` field of the GitHub configuration (`workflow-send-id` in CasC, _Send workflow ID_ in the form)
says whether this `id` input is sent. It is `true` by default, and each auto-versioning post-processing configuration
and each notification can override it with its own `sendId` field.

GitHub rejects a dispatch whose inputs do not match the inputs declared by the workflow:

|                    | Workflow declares `id` (required) | Workflow does not declare `id` |
|--------------------|-----------------------------------|--------------------------------|
| `id` is sent       | OK                                | Rejected                       |
| `id` is not sent   | Rejected                          | OK                             |

Set it to `false` only for workflows which do not declare `id`, on a GitHub which returns the run of a dispatch:
without the `id` input, a run which is not returned cannot be searched for, and the dispatch fails.
