# GitLab SCM engine (`gitlab`)

The [SCM engine](../../../configuration/ci-config.md#scm-engines) is used to connect Yontrack projects to GitLab
projects, on gitlab.com or on a self-managed instance.

## Detection

The [CI engine](../../../configuration/ci-config.md#ci-engines) provides a SCM URL and the engine is selected when the
**host** of this URL is the host of one of the [GitLab configurations](../../../start/configuration/gitlab.md) of
Yontrack. Unlike Bitbucket Cloud, no host is hardcoded: a self-managed GitLab is on an arbitrary one.

All these forms are recognised, for a configuration on `https://gitlab.com`:

* `https://gitlab.com/<group>/<project>.git`
* `https://<user>@gitlab.com/<group>/<project>.git`
* `git@gitlab.com:<group>/<project>.git`
* `ssh://git@gitlab.com:<port>/<group>/<project>.git`

The scheme and the port are not compared: the configuration URL is the HTTPS endpoint of the API, while a clone URL is
just as legitimately SSH on another port. The host, on the other hand, must be exactly the configured one - a URL like
`https://gitlab.com.example.org/group/project.git` names another host and does not match.

For example, a Jenkins job building a GitLab project provides its URL through the `GIT_URL` environment variable.

## Configuration

The project is attached to a GitLab configuration in Yontrack:

* if the `scmConfig` project property of the CI configuration is set, the GitLab configuration with this name is used
* otherwise, the configuration whose URL matches the SCM URL is used
* if no configuration matches, or if several configurations are registered for the same instance, the configuration
  fails and asks for `scmConfig` to be set

```yaml
version: v1
configuration:
  defaults:
    project:
      scmConfig: my-gitlab-config
      scmIndexationInterval: 30
```

The project path is everything after the host, with a trailing `.git` removed. GitLab
[subgroups](https://docs.gitlab.com/user/group/subgroups/) nest arbitrarily deep, and the whole path is kept:
`https://gitlab.com/nemerosa/tools/ci/yontrack.git` gives the path `nemerosa/tools/ci/yontrack`. When the instance is
served under a relative URL root, like `https://dev.example.com/gitlab`, that root is not part of the project path.

The `scmIndexationInterval` and `issueServiceIdentifier` project properties are used for the indexation interval and
the issue service.

The branch is attached to the repository branch. Branches named like `PR-123` are considered as pull requests.

The build is linked to the [Git commit](../../../generated/properties/property-net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType.md).
