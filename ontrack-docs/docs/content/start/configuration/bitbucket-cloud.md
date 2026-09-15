# Configuring Yontrack for Bitbucket Cloud

Yontrack connects to [Bitbucket Cloud](https://bitbucket.org) through a **configuration**, which holds the
credentials, and a **project property**, which links a Yontrack project to a repository of a workspace.

> Don't forget to check the [Configuration as Code](../../configuration/casc.md) section
> to see how to configure Yontrack using CasC.

!!! warning "Upgrading from an earlier version"

    Bitbucket Cloud configurations used app passwords, which Atlassian removed on 2026-07-28. The
    configuration, its CasC schema, the project property and the SCM catalog provider id have changed without
    any migration: **Bitbucket Cloud configurations must be recreated**, and the Bitbucket Cloud property of
    each project must be set again, with its workspace.

## Authentication

A configuration uses one of two authentication types, set in `authType`.

| `authType`     | Credentials                                      | HTTP authentication | Bitbucket plan                                             |
|----------------|--------------------------------------------------|---------------------|------------------------------------------------------------|
| `API_TOKEN`    | `email` of an Atlassian account + its API `token` | Basic               | Every plan                                                 |
| `ACCESS_TOKEN` | an access `token`                                | Bearer              | Repository access tokens: every plan. Workspace and project access tokens: Premium only |

An **API token** acts as its Atlassian account and reaches every workspace and repository that account can
read. Create it in your Atlassian account settings, under _Security_ > _API tokens_, as an _API token with
scopes_ for Bitbucket.

An **access token** is not tied to a user. A repository access token covers a single repository, which is
enough for a project linked to that repository; a workspace or project access token covers more, but needs the
Premium plan. Yontrack does not care which kind of access token it is given.

### Scopes

For an API token, the Bitbucket scopes:

* `read:user:bitbucket` — needed to test the configuration;
* `read:workspace:bitbucket`, `read:project:bitbucket`, `read:repository:bitbucket` — to read the repositories;
* `read:pullrequest:bitbucket` — to read pull requests.

For an access token: _Repositories: Read_, and _Pull requests: Read_.

Features writing to Bitbucket, like [auto-versioning](#auto-versioning), need the corresponding write scopes
(`write:repository:bitbucket` and `write:pullrequest:bitbucket`, or _Repositories: Write_ and _Pull requests:
Write_).

### Testing the configuration

Yontrack checks the credentials when a configuration is saved or tested:

* with an API token, by calling `GET /2.0/user`;
* with an access token, by calling `GET /2.0/hook_events` — an access token has no user, so `/2.0/user` is
  refused to it, while `/2.0/hook_events` needs no scope and still rejects an invalid token.

### Git

Yontrack clones the repositories over HTTPS, with the user name Bitbucket documents for each token type:

* `x-bitbucket-api-token-auth` for an API token — the Atlassian account email is refused by Git;
* `x-token-auth` for an access token.

## Auto-merge identity

Bitbucket does not let a user approve their own pull request. When auto-versioning is to merge its pull requests
automatically, give the configuration a second identity to approve them: `autoMergeEmail` and `autoMergeToken`,
the email and API token of another Atlassian account with write access to the repositories. The API token needs
`read:user:bitbucket`, `read:repository:bitbucket`, `read:pullrequest:bitbucket` and
`write:pullrequest:bitbucket`.

Both tokens are encrypted in the Yontrack database and never shown back.

## Configuration

In the UI, go to the user menu, _Bitbucket Cloud configurations_.

As code, in your _Yontrack Casc files_:

{% raw %}
```yaml
ontrack:
  config:
    bitbucket-cloud:
      # Using an API token
      - name: bitbucket-cloud
        authType: API_TOKEN
        email: yontrack-bot@example.com
        token: {{ secrets.yontrack-bitbucket-cloud.token }}
        autoMergeEmail: yontrack-approver@example.com
        autoMergeToken: {{ secrets.yontrack-bitbucket-cloud.approver-token }}
      # Using a repository access token
      - name: my-repository
        authType: ACCESS_TOKEN
        token: {{ secrets.yontrack-bitbucket-cloud.my-repository-token }}
```
{% endraw %}

## Project property

The _Bitbucket Cloud configuration_ property of a project links it to a repository:

| Field                                 | Description                                                                 |
|---------------------------------------|-----------------------------------------------------------------------------|
| `configuration`                       | Name of the Bitbucket Cloud configuration                                   |
| `workspace`                           | Slug of the workspace                                                       |
| `repository`                          | Slug of the repository in the workspace                                     |
| `indexationInterval`                  | How often to index the repository, in minutes. `0` disables the indexation. |
| `issueServiceConfigurationIdentifier` | Issue service, for example `jira//my-jira`                                  |

With the [Yontrack CLI](https://github.com/yontrack/yontrack-cli) or the GraphQL API, the property is set by the
`setProjectBitbucketCloudConfigurationProperty` mutation.

## SCM catalog

The SCM catalog provider for Bitbucket Cloud has the id `bitbucket-cloud` (Bitbucket Server keeps `bitbucket`).

A configuration does not name a workspace, and Bitbucket Cloud offers no way to list the workspaces an API token
or an access token can read. The catalog therefore lists, for each configuration, all the repositories of the
workspaces already used by the Bitbucket Cloud properties of the Yontrack projects. A workspace no project uses
does not appear in the catalog.

## SCM

A project with the Bitbucket Cloud property has an SCM of engine `bitbucket-cloud`. It gives the project:

* **change logs** between two builds, whose commits are set by the _Git commit_ property;
* links to the commits and to the comparison between two commits;
* branch creation and deletion, file download and upload — one commit per upload;
* pull request information for branches which are pull requests;
* pull request creation, approval and merge, and branch merges, for [auto-versioning](#auto-versioning).

### Change logs

The commits between two builds are read from the Bitbucket Cloud API
(`GET /2.0/repositories/{workspace}/{repository}/commits?include={to}&exclude={from}`). When the two builds are
given in the reverse order, the order is swapped.

Bitbucket Cloud allows **1,000 API requests per hour per token**, and returns at most 100 commits per request. A
change log between two builds far apart could therefore use a large part of this allowance, so the number of
commits it returns is capped by the **max commits** setting (see below): with the default of 1,000, a change log
costs at most 10 requests.

Going through all the commits of a repository, like when looking for the build of a commit, does not use the
API: Yontrack reads them from its local clone of the repository.

### File references

Files stored in Bitbucket Cloud can be referenced by `scm://` URIs, wherever Yontrack accepts them:

```
scm://bitbucket-cloud/<configuration>/<workspace>/<repository>/<path>
```

* `<configuration>` — name of the Bitbucket Cloud configuration
* `<workspace>` — slug of the workspace
* `<repository>` — slug of the repository
* `<path>` — path to the file, read on the main branch of the repository

For example, `scm://bitbucket-cloud/bitbucket-cloud/my-workspace/my-repository/config/settings.yaml`.

### Settings

In the UI, go to _Settings_ > _Bitbucket Cloud_.

| Setting             | Default           | Description                                                                                         |
|---------------------|-------------------|-----------------------------------------------------------------------------------------------------|
| `maxCommits`        | `1000`            | Maximum number of commits to return for a change log                                                |
| `mergeStrategy`     | `squash`          | Strategy to merge the auto-versioning pull requests: `merge_commit`, `squash` or `fast_forward`     |
| `autoMergeTimeout`  | `600000` (10 min) | Milliseconds to wait for an auto-versioning pull request to be mergeable                            |
| `autoMergeInterval` | `30000` (30 s)    | Milliseconds between two attempts to merge an auto-versioning pull request                          |
| `autoDeleteBranch`  | `true`            | Deleting the source branch when an auto-versioning pull request is merged                           |

As code:

```yaml
ontrack:
  config:
    settings:
      bitbucket-cloud:
        maxCommits: 1000
        mergeStrategy: squash
        autoMergeTimeout: 600000
        autoMergeInterval: 30000
        autoDeleteBranch: true
```

## Auto-versioning

A project with the Bitbucket Cloud property can be the target of
[auto-versioning](../../integrations/auto-versioning/auto-versioning.md). Yontrack creates the upgrade branch, commits
the new version on it, then:

* in `PUSH` mode, merges the upgrade branch into the target branch through its local clone of the repository;
* in `PR` mode, opens a pull request from the upgrade branch to the target branch, named `PR-<id>`.

The `reviewers` of the auto-versioning configuration are added to the pull request. Give them as account UUIDs
(`{...}`), or as account IDs, nicknames or display names, which are looked up among the members of the workspace
— an unknown reviewer fails the pull request creation.

### Auto approval

With `autoApproval: true`, the configuration must have an [auto-merge identity](#auto-merge-identity): Bitbucket
does not let the author of a pull request approve it. Yontrack then:

1. approves the pull request with the auto-merge identity;
2. every `autoMergeInterval`, checks that all the builds of the pull request have passed and, if so, asks
   Bitbucket Cloud to merge it with the `mergeStrategy` and the commit message of the auto-versioning
   configuration — Bitbucket refuses while its merge checks, like required approvals, do not pass;
3. gives up after `autoMergeTimeout`, leaving the pull request open.

When `autoDeleteBranch` is set, Bitbucket Cloud deletes the source branch as part of the merge.

An auto-versioning order with auto approval and no auto-merge identity fails before any pull request is created.
So does an order with the `SCM` auto approval mode: the Bitbucket Cloud API cannot schedule a merge for when the
checks pass. Its _allow auto-merge when builds pass_ branch restriction only enables the button in the Bitbucket
UI.

Each attempt costs up to three API requests out of the 1,000 per hour of the token: with the defaults, twenty
attempts over ten minutes.

### Token scopes

| Identity                                   | Scopes                                                                                                                                   |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| Configuration, API token                   | `read:repository:bitbucket`, `write:repository:bitbucket`, `read:pullrequest:bitbucket`, `write:pullrequest:bitbucket`, and `read:workspace:bitbucket` when reviewers are not given as UUIDs |
| Configuration, access token                | _Repositories: Write_, _Pull requests: Write_                                                                                            |
| Auto-merge identity, API token             | `read:user:bitbucket`, `read:repository:bitbucket`, `read:pullrequest:bitbucket`, `write:pullrequest:bitbucket`                          |

The account of the configuration merges the pull requests: it must be allowed to by the branch restrictions of
the target branch.
