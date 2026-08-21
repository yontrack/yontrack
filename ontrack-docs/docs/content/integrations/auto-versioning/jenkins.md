# Jenkins auto-versioning post-processing

You can delegate the post-processing to a Jenkins job.

There is a global configuration and there are a specific configuration at branch level (in the
`postProcessingConfig` [configuration parameter](auto-versioning.md#configuration)).

For the global configuration, you can go to _Settings > Jenkins Auto Versioning Processing_ and define the following
attributes:

* _Configuration_ - default Jenkins configuration to use for the connection
* _Job_ - default path to the job to launch for the post-processing, relative to the Jenkins root URL (note that `/job/`
  separators can be omitted)
* _Retries_ - the amount of times we check for successful scheduling and completion of the post-processing job
* _Retry interval_ - the time (in seconds) between two checks for successful scheduling and completion of the
  post-processing job

## Auto-versioning configuration

The `postProcessingConfig` property at branch level must contain the following parameters:

| Parameter       | Default value | Description                                                                |
|-----------------|---------------|----------------------------------------------------------------------------|
| `dockerImage`   | _Required_    | Docker image defining the environment                                      |
| `dockerCommand` | _Required_    | Command to run in the working copy inside the Docker container             |
| `commitMessage` | _Optional_    | Commit message for the post processed files                                |
| `config`        | _Optional_    | Jenkins configuration to use for the connection                            |
| `job`           | _Optional_    | Path to the job to launch for the post processing                          |
| `credentials`   | _Optional_    | List of credentials to inject in the command (see [below](#credentials))   |
| `parameters`    | _Optional_    | List of extra parameters to pass to the job (see [below](#extra-parameters)) |

Example of such a configuration:

```yaml
postProcessing: jenkins
postProcessingConfig:
  dockerImage: openjdk:11
  dockerCommand: ./gradlew dependencies --write-locks
  commitMessage: "Resolving the dependency locks"
```

When `commitMessage` is not set, a default message is used:
`Post processing for version change in <path> for <version>`.

The `dockerImage`, `dockerCommand` and `job` parameters, and every `parameters` value, are
[templated](../../appendix/templating.md) using the
[auto-versioning templating context](auto-versioning.md#pr-title-and-body). For example:

```yaml
dockerCommand: ./gradlew resolveAndLockAll --write-locks -Pversion=${VERSION}
```

### Extra parameters

On top of the parameters described in [Jenkins job definition](#jenkins-job-definition), which Yontrack always sends,
arbitrary parameters can be passed to the job:

```yaml
postProcessing: jenkins
postProcessingConfig:
  dockerImage: openjdk:11
  dockerCommand: ./gradlew dependencies --write-locks
  parameters:
    - name: TARGET_ENV
      value: staging
    - name: SOURCE_RELEASE
      value: ${sourceBuild.release}
```

Each value is templated. An extra parameter using the name of a built-in one overrides it.

### Credentials

The `credentials` parameter lists the Jenkins credentials to bind before running the command. Each entry has:

* `type` — the Jenkins credentials binding type: `usernamePassword`, `usernameColonPassword` or `string`
* `id` — the ID of the credentials in Jenkins
* `vars` — the environment variables to bind the credentials to: two for `usernamePassword` (user & password), one for
  `usernameColonPassword` and `string`

```yaml
postProcessing: jenkins
postProcessingConfig:
  dockerImage: openjdk:11
  dockerCommand: ./gradlew dependencies --write-locks
  credentials:
    - type: usernamePassword
      id: artifactory
      vars:
        - ARTIFACTORY_USER
        - ARTIFACTORY_PASSWORD
    - type: string
      id: npm-token
      vars:
        - NPM_TOKEN
```

The credentials are passed to the job in the `CREDENTIALS` parameter, as a pipe (`|`) separated list of
`<type>,<id>,<vars...>` entries — `usernamePassword,artifactory,ARTIFACTORY_USER,ARTIFACTORY_PASSWORD|string,npm-token,NPM_TOKEN`
for the example above. It is up to the job to bind them.

!!! note

    The same list can also be given directly in this short form, as a single string with one entry per line:

    ```yaml
    credentials: |
      usernamePassword,artifactory,ARTIFACTORY_USER,ARTIFACTORY_PASSWORD
      string,npm-token,NPM_TOKEN
    ```

## Jenkins job definition

The Jenkins job must accept the following parameters:

| Parameter        | Description                                                            |
|------------------|------------------------------------------------------------------------|
| `REPOSITORY_URI` | Git URI of the repository to upgrade                                   |
| `DOCKER_IMAGE`   | This image defines the environment for the command to run in.          |
| `DOCKER_COMMAND` | Command to run in the working copy inside the Docker container.        |
| `COMMIT_MESSAGE` | Commit message to use to commit and push the upgrade.                  |
| `UPGRADE_BRANCH` | Branch containing the code to upgrade.                                 |
| `CREDENTIALS`    | Pipe (\|) separated list of credential entries to pass to the command. |
| `VERSION`        | The version which is upgraded to                                       |

The Jenkins job is responsible to:

* running a Docker container based on the `DOCKER_IMAGE` image
* inject any credentials defined by `CREDENTIALS` parameter
* checkout the `UPGRADE_BRANCH` branch of the repository at `REPOSITORY_URI` inside the container
* run the `DOCKER_COMMAND` command inside the container
* commit and push any change using the `COMMIT_MESSAGE` message to the `UPGRADE_BRANCH` branch
