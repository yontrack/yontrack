# Configuring Yontrack for GitLab

Yontrack connects to [GitLab](https://gitlab.com) — gitlab.com or a self-managed instance — through a
**configuration**, which holds the URL and the credentials, and a **project property**, which links a Yontrack
project to a GitLab project.

> Don't forget to check the [Configuration as Code](../../configuration/casc.md) section
> to see how to configure Yontrack using CasC.

!!! warning "Upgrading from an earlier version"

    A GitLab configuration used to carry a `user` and a `password`. It now carries a single `token`, and the
    user is gone. **Existing configurations are migrated automatically** when Yontrack starts: the password —
    which already held a personal access token in practice — becomes the token, and the user is dropped.

    Two things do need attention:

    * a **CasC** file setting up GitLab configurations must be updated: `user` is no longer accepted and
      `password` is now `token`;
    * nothing else changes — the GitLab project property, its `repository` field and the
      `setProjectGitLabConfigurationProperty` mutation are untouched.

**GitLab Free is enough.** Nothing in Yontrack's GitLab support depends on Premium or Ultimate.

## Authentication

A configuration authenticates with a **personal access token**, and nothing else. GitLab's REST API has no
password authentication; deploy tokens are barred from the API, and job tokens live only for the duration of a
job. Project and group access tokens do work against a self-managed instance, but are Premium on gitlab.com,
so a personal access token is the one credential that works everywhere.

Create it in GitLab under _Edit profile_ > _Access tokens_, with the **`api`** scope.

!!! note "Tokens expire"

    A GitLab personal access token lasts **365 days at most** — there is no such thing as a non-expiring one.
    Renewing it is an annual chore; Yontrack reports a configuration whose token is refused on the
    _Connectors_ page of the admin console.

Yontrack sends the token in the `PRIVATE-TOKEN` header, and checks it by calling `GET /api/v4/user` whenever a
configuration is saved or tested.

### Git

Yontrack clones the repositories over HTTPS, with the user name `oauth2` and the token as the password — GitLab
accepts any user name beside a personal access token, which is why the configuration holds none.

## Fields

| Field                  | Content                                                                          |
|------------------------|----------------------------------------------------------------------------------|
| `name`                 | Name of the configuration, referenced by the project property                    |
| `url`                  | URL of the instance, `https://gitlab.com` or the URL of your self-managed one    |
| `token`                | Personal access token with the `api` scope. Encrypted, and never shown back      |
| `ignoreSslCertificate` | Accepts any SSL certificate — for a self-managed instance behind an internal CA  |

## Configuration

In the UI, go to the user menu, _GitLab configurations_.

As code, in your _Yontrack Casc files_:

{% raw %}
```yaml
ontrack:
  config:
    gitlab:
      - name: gitlab.com
        url: https://gitlab.com
        token: "{{ secret }}"
      - name: internal
        url: https://gitlab.internal.example.com
        token: "{{ secret }}"
        ignoreSslCertificate: true
```
{% endraw %}

## Project property

The GitLab project property links a Yontrack project to a GitLab project:

| Field                                  | Content                                                                    |
|----------------------------------------|----------------------------------------------------------------------------|
| `configuration`                        | Name of the GitLab configuration to use                                    |
| `repository`                           | **Full path** of the GitLab project, subgroups included                    |
| `indexationInterval`                   | How often, in minutes, Yontrack syncs the Git repository. `0` disables it  |
| `issueServiceConfigurationIdentifier`  | Issue service to use. Left empty, GitLab's own issues are used             |

The `repository` field holds the whole path — `group/project`, or `group/subgroup/project` for a project in a
subgroup — exactly as it appears in the GitLab URL. Yontrack URL-encodes it when calling the API.

## Issues

Yontrack reads GitLab issues, and shows them in change logs and on the issue information page: state,
labels and milestone.

### Referencing an issue

Only the **`#123`** form is recognised, and it is looked up in the GitLab project the issue service names.
Two things GitLab itself understands are deliberately not parsed:

* **cross-project references** — `group/project#123` needs access to another project and is ambiguous about
  which one Yontrack should call, so it is not supported;
* **closing keywords** — `Closes #123`, `Fixes #123` and the rest are an instance-wide, administrator-editable
  pattern in GitLab. Yontrack links the issue either way; it does not try to tell a closing reference from a
  plain one.

### Choosing the issue service

The `issueServiceConfigurationIdentifier` of the project property says where a commit's `#123` is resolved:

* **left empty**, or set to `self` — the project's **own** GitLab project. This is the usual case, and what
  the _GitLab issues_ entry of the UI's issue-service list means.
* **set to another service** — any other issue service the instance offers, JIRA included. Every GitLab
  project already configured in Yontrack appears there as `configuration:group/project`, so a project whose
  code and issues do not live together — or a plain Git project, with no GitLab property of its own — can
  point at the GitLab project that holds its issues.

### The last commit of an issue

Some Yontrack features ask an issue service for the last commit that mentions an issue. GitLab has no
endpoint for it — an issue's own timeline of related commits is a paid feature — so Yontrack **searches the
project's commits** for the `#123` reference (`GET /projects/:id/search?scope=commits`) and keeps the most
recent match. The search is a substring one, so Yontrack discards the commits which merely start with the
same digits: a search for `#12` does not return the commit of `#123`.

## SCM

A project with the GitLab property has an SCM of engine `gitlab`. It gives the project:

* **change logs** between two builds, whose commits are set by the _Git commit_ property;
* links to the commits and to the comparison between two commits;
* branch creation and deletion, file download and upload — one commit per upload;
* merge request information for branches which are merge requests;
* branch merges, through Yontrack's local clone of the repository.

!!! note "Merge request creation"

    Creating a merge request — what [auto-versioning](../../integrations/auto-versioning/auto-versioning.md) does in
    `PR` mode — is not available yet. Yontrack refuses it with an explicit error rather than leaving a
    half-finished merge request behind. It arrives with
    [issue #1830](https://github.com/yontrack/yontrack/issues/1830).

### Change logs

The commits between two builds are read from GitLab's comparison endpoint
(`GET /projects/:id/repository/compare?from=…&to=…&straight=false`). `straight=false` is GitLab's `from...to`
form: the commits reachable from the second reference but not from the **merge base** of the two, which is what
a change log means and what GitHub and Bitbucket Cloud give. When the two builds are given in the reverse
order, the order is swapped.

The number of commits a change log returns is capped by the **max commits** setting (see below). The comparison
itself is one request whatever the range: GitLab returns the whole answer at once, and gives up on its own on
very large ranges.

Going through all the commits of a repository — looking for the build of a commit, or for the branches that
contain one — does **not** use the API: Yontrack reads them from its local clone of the repository, as it does
for GitHub and Bitbucket Cloud. That matters more on GitLab than elsewhere, because gitlab.com's announced
tier-aware rate limits drop the Free tier to a burst of a hundred requests a minute.

### File references

Files stored in GitLab can be referenced by `scm://` URIs, wherever Yontrack accepts them:

```
scm://gitlab/<configuration>/<project path>/<path>
```

* `<configuration>` — name of the GitLab configuration
* `<project path>` — **full path** of the GitLab project, subgroups included
* `<path>` — path to the file, read on the default branch of the project

For example, `scm://gitlab/gitlab.com/my-group/my-project/config/settings.yaml`, or
`scm://gitlab/gitlab.com/my-group/my-subgroup/my-project/config/settings.yaml`.

A GitLab project path is arbitrarily deep, so — unlike GitHub's `owner/repository` — no fixed number of leading
segments tells the project apart from the file path: `my-group/my-project/src/app.yaml` has exactly the same
shape as a project three subgroups down. Yontrack therefore **asks GitLab** rather than guessing: it offers each
candidate prefix of at least two segments to the API and keeps the one GitLab recognises as a project. At most
one of them ever can, since a GitLab project holds no namespace of its own — if `group/sub/project` is a
project then `group/sub` is necessarily a group — so the reference is unambiguous, at the cost of a few
lookups.

A reference naming no project of the configuration is an error, rather than an empty file.

### Settings

In the UI, go to _Settings_ > _GitLab_.

| Setting      | Default | Description                                          |
|--------------|---------|------------------------------------------------------|
| `maxCommits` | `1000`  | Maximum number of commits to return for a change log |

As code:

```yaml
ontrack:
  config:
    settings:
      gitlab:
        maxCommits: 1000
```

## Proxies

Yontrack honours the standard JVM proxy settings — `http.proxyHost`, `http.proxyPort`, `https.proxyHost`,
`https.proxyPort` and `http.nonProxyHosts` — when calling GitLab, as it does for GitHub
([issue #588](https://github.com/yontrack/yontrack/issues/588)).

## Self-managed instances

Self-managed instances are supported at whatever URL the configuration names. Two things are worth knowing:

* **SSL** — an instance behind an internal certificate authority either has that CA added to the JVM trust
  store, or has `ignoreSslCertificate` set on its configuration. The first is preferable.
* **Access tokens** — project and group access tokens are available on any self-managed licence, Free
  included, and Yontrack accepts one anywhere it accepts a personal access token.
* **Redirects** — Yontrack does **not** follow them when calling the API, and reports the `Location` as an
  error instead. Following one would replay the access token onto whatever host it names. No GitLab API v4
  endpoint Yontrack calls redirects, so hitting this means the configuration's `url` is not the one the
  instance answers on — typically an `http://` URL on an instance which forces `https://`. Fix the URL rather
  than working around it: the token was being sent in clear over that first request.
