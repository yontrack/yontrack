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
