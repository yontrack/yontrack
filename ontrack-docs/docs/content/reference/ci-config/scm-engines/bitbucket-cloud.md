# Bitbucket Cloud SCM engine (`bitbucket-cloud`)

The [SCM engine](../../../configuration/ci-config.md#scm-engines) is used to connect Yontrack projects to Bitbucket Cloud
repositories.

## Detection

The [CI engine](../../../configuration/ci-config.md#ci-engines) provides a SCM URL and the engine is selected when this URL
points to `bitbucket.org`, in any of these forms:

* `https://bitbucket.org/<workspace>/<repository>.git`
* `https://<user>@bitbucket.org/<workspace>/<repository>.git`
* `git@bitbucket.org:<workspace>/<repository>.git`

For example, a Jenkins job building a Bitbucket Cloud repository provides its URL through the `GIT_URL` environment
variable.

## Configuration

The project is attached to a Bitbucket Cloud configuration in Yontrack. Since a Bitbucket Cloud configuration is not
bound to any URL, it is selected this way:

* if the `scmConfig` project property of the CI configuration is set, the Bitbucket Cloud configuration with this name
  is used
* otherwise, if there is only one Bitbucket Cloud configuration, it is used
* otherwise, the configuration fails and asks for `scmConfig` to be set

```yaml
version: v1
configuration:
  defaults:
    project:
      scmConfig: my-bitbucket-cloud-config
      scmIndexationInterval: 30
```

The workspace and the repository are read from the SCM URL. The `scmIndexationInterval` and `issueServiceIdentifier`
project properties are used for the indexation interval and the issue service.

The branch is attached to the repository branch. Branches named like `PR-123` are considered as pull requests.

The build is linked to the [Git commit](../../../generated/properties/property-net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType.md).
