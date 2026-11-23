# Kubernetes auto-versioning post-processing

You can run the post-processing as a **Kubernetes Job**, in the cluster Yontrack runs in. No CI system is involved: no
workflow or pipeline has to be written, hosted or wired.

Yontrack runs your image in a Job and supplies the _harness_ a CI system would otherwise provide:

1. it clones the [upgrade branch](auto-versioning.md#the-upgrade-branch),
2. it runs your command in the working copy,
3. it commits the result and pushes it to the upgrade branch.

The `postProcessing` and `postProcessingConfig` parameters are set at the branch level, like for the other
[post-processing mechanisms](auto-versioning.md#post-processing).

## Enabling

This post-processing is **off by default**. Until it is enabled, no Kubernetes client is created and `kubernetes` is an
unknown post-processing ID.

Enable it with the `ontrack.extension.kubernetes.enabled` configuration property, or with the
`ONTRACK_EXTENSION_KUBERNETES_ENABLED` environment variable:

```yaml
ontrack:
  extension:
    kubernetes:
      enabled: true
```

Yontrack must run in a Kubernetes cluster: the connection to the cluster is the in-cluster one, and the Jobs are
created in that very cluster. See [RBAC](#rbac) for the permissions Yontrack needs.

All the settings are **static configuration properties**: there is no settings page and no
[Configuration as Code](../../configuration/casc.md) for them. They are a security boundary, set by whoever deploys Yontrack,
and not by those who write an auto-versioning configuration.

The full list, generated from the code, is in the
[configuration properties](../../generated/configurations/net.nemerosa.ontrack.extension.kubernetes.KubernetesConfigProperties.md).

## Configuration properties

All the properties start with `ontrack.extension.kubernetes`.

| Property                  | Default                | Description                                                                                   |
|---------------------------|------------------------|-----------------------------------------------------------------------------------------------|
| `enabled`                 | `false`                | Switch of the extension                                                                       |
| `namespace`               | _Yontrack's namespace_ | Namespace where the Jobs and their Secrets are created. When blank, the one Yontrack runs in  |
| `harness.image`           | `alpine/git:v2.49.1`   | Image of the `clone` and `push` steps. Must provide `sh` and `git`, and run as a non-root user |
| `git.user-name`           | `Yontrack`             | Name of the author of the commit made by the harness                                          |
| `git.user-email`          | `no-reply@yontrack.invalid` | Email of the author of the commit made by the harness                                    |
| `allowed.images`          | _Empty_                | Patterns the `dockerImage` must match. **Empty denies every image**                           |
| `allowed.secrets`         | _Empty_                | Names of the Secrets a configuration may reference. **Empty denies every Secret**             |
| `job.timeout`             | `10m`                  | Time given to a Job when the configuration sets no `timeoutSeconds`                           |
| `job.max-timeout`         | `30m`                  | Cap on `timeoutSeconds`. Keep it well under one hour, see [constraints](#constraints)         |
| `job.ttl`                 | `1h`                   | How long a finished Job is kept before Kubernetes deletes it (`ttlSecondsAfterFinished`)      |
| `job.poll-interval`       | `5s`                   | Interval between two readings of the status of a running Job                                  |
| `job.log-tail-lines`      | `100`                  | Number of log lines of the failing step kept in the audit                                     |
| `job.image-pull-secrets`  | _Empty_                | Names of the image pull secrets applied to every Job                                          |
| `job.run-as-user`         | `1000`                 | User ID the three steps run as. Never `0`                                                     |
| `job.run-as-group`        | `1000`                 | Group ID the three steps run as                                                               |
| `job.fs-group`            | `1000`                 | Group ID owning the volumes of a Job                                                          |
| `job.resources.requests.cpu`    | `100m`           | CPU requested by the `run` step                                                               |
| `job.resources.requests.memory` | `256Mi`          | Memory requested by the `run` step                                                            |
| `job.resources.limits.cpu`      | `1`              | CPU limit of the `run` step                                                                   |
| `job.resources.limits.memory`   | `1Gi`            | Memory limit of the `run` step                                                                |

!!! warning

    `allowed.images` and `allowed.secrets` are empty by default, and **an empty list denies everything**. Until you set
    them, every `kubernetes` post-processing fails, with a message naming the property to set.

An image pattern uses `*` for any sequence of characters, and **must match the whole image name**. For example:

```yaml
ontrack:
  extension:
    kubernetes:
      enabled: true
      namespace: yontrack-jobs
      allowed:
        images:
          - "eclipse-temurin:*"
          - "registry.example.com/build-tools/*"
        secrets:
          - npm-token
      job:
        max-timeout: 20m
```

Here, `eclipse-temurin:21` and `registry.example.com/build-tools/node:20` are allowed, but `eclipse-temurin` (no tag)
or `docker.io/eclipse-temurin:21` are not.

As environment variables, the same settings are for example `ONTRACK_EXTENSION_KUBERNETES_ALLOWED_IMAGES` and
`ONTRACK_EXTENSION_KUBERNETES_JOB_MAX_TIMEOUT`.

## Auto-versioning configuration

The `postProcessingConfig` [parameter](auto-versioning.md#configuration) at the branch level accepts:

| Parameter        | Default value | Description                                                                                          |
|------------------|---------------|------------------------------------------------------------------------------------------------------|
| `dockerImage`    | _Required_    | Image running the command. Must match `allowed.images`, have a shell and run as a non-root user      |
| `dockerCommand`  | _Required_    | Command run through `sh -c` at the root of the working copy                                          |
| `commitMessage`  | `Post processing for version change in <path> for <version>` | Commit message to use to commit and push the result of the post-processing |
| `env`            | _Optional_    | List of `{name, value}` environment variables for the command. Values are [templates](#templating)   |
| `secrets`        | _Optional_    | List of `{secret, key, env}`: one key of a Kubernetes Secret exposed as one environment variable     |
| `timeoutSeconds` | _Optional_    | Time given to the Job, in seconds. Capped by `job.max-timeout`                                       |

Example:

```yaml
postProcessing: kubernetes
postProcessingConfig:
    dockerImage: eclipse-temurin:21
    dockerCommand: ./gradlew dependencies --write-locks -Pversion="$VERSION"
    commitMessage: "Resolving the dependency locks"
    env:
      - name: TARGET_VERSION
        value: ${VERSION}
    secrets:
      - secret: npm-token
        key: token
        env: NPM_TOKEN
    timeoutSeconds: 900
```

### Templating

`dockerImage`, `dockerCommand`, `commitMessage` and the `value` of every `env` entry are
[templated](../../appendix/templating.md) using the
[auto-versioning templating context](auto-versioning.md#pr-title-and-body). The image is checked against
`allowed.images` once rendered.

### Injected variables

These variables are always set in the command environment, and **cannot be overridden** by `env`:

| Variable          | Description                                      |
|-------------------|--------------------------------------------------|
| `VERSION`         | The version which is upgraded to                 |
| `UPGRADE_BRANCH`  | Upgrade branch being post-processed              |
| `REPOSITORY_URI`  | URI of the repository                            |
| `PROJECT`         | Name of the Yontrack project being upgraded      |
| `BRANCH`          | Name of the Yontrack branch being upgraded       |

### Secrets

Yontrack **stores no secret value**: an auto-versioning configuration holds Secret _names_ only. For each entry of
`secrets`:

* the Secret must exist in the namespace where the Jobs are created,
* its name must be in `allowed.secrets`,
* the `key` of the Secret is exposed to the command as the `env` environment variable.

Yontrack does not read these Secrets: the kubelet resolves them when it starts the Job.

## Steps of a Job

Yontrack creates one Job per post-processing. Three containers run in sequence:

| Step    | Kind           | Image                 | Mounts                                          | What it does                                                                                                 |
|---------|----------------|-----------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `clone` | init container | Harness image         | Work tree, Git directory, SCM credentials       | Shallow clone (`--depth 1`) of the upgrade branch, the Git directory being kept apart from the work tree     |
| `run`   | init container | Your `dockerImage`    | **Work tree only**                              | Runs `sh -c <dockerCommand>` at the root of the working copy                                                  |
| `push`  | main container | Harness image         | Work tree, Git directory, SCM credentials       | Stages all changes. If there are none, it stops. Otherwise, one commit and a non-forced push to the upgrade branch |

The outcome rules:

* every change in the working copy ends up in **one commit**, made by the harness - your command never commits,
* **no change** is a success, without any commit,
* a **non-zero exit** of the command fails the post-processing, and nothing is pushed.

## Constraints

!!! warning "No `git` command in your command"

    The `run` step has neither the Git directory nor the SCM credentials: they are mounted in the `clone` and `push`
    steps only. This way, the command cannot leak the credentials, nor plant a Git hook or alter a Git configuration
    which `push` would later run with the credentials. As a consequence, **no `git` command works** in your command.

!!! warning "Shell and non-root"

    The image must have `sh`. It must also work as a **non-root user**: the three steps run with the user, group and
    file system group set by `job.run-as-user`, `job.run-as-group` and `job.fs-group`, so that your command can write
    the files the harness cloned. An image which only works as root fails.

!!! warning "No home directory"

    Your command runs as a user which usually does not exist in the image, and `HOME` is not set by Yontrack. A tool
    writing to its home directory (a package cache, for example) may fail: point it to a writable location through
    `env`, for example `HOME=/tmp`.

!!! warning "No retry by Kubernetes"

    A Job has `backoffLimit: 0` and never restarts its pods. Retrying is the business of auto-versioning, through its
    [automatic retries](auto-versioning.md#automatic-retries).

!!! warning "Timeouts"

    The post-processing is synchronous: the auto-versioning queue consumer is **blocked while the Job runs**. This is
    why every Job has a timeout (`timeoutSeconds`, `job.timeout` by default, never above `job.max-timeout`).

!!! warning "GitHub App tokens"

    The token of a GitHub App lasts one hour and is fetched when the Job is created. Keep `job.max-timeout` well under
    one hour.

The Jobs also run without a service account token, as non-root, without privilege escalation and with all the
capabilities dropped.

## Supported SCMs

The harness needs the clone URL and the credentials of the repository. They are supported for:

* GitHub, with a token or a GitHub App
* GitLab
* Bitbucket Server
* Bitbucket Cloud

With another SCM, the post-processing fails with an explicit message.

## Failures

Yontrack waits for the Job to complete. In the [auto-versioning audit](auto-versioning.md#audit-logs):

* the post-processing shows the **namespace** and the **name of the Job** - there is no URL to follow,
* a failure names the **failing step** (`clone`, `run` or `push`), its **exit code** or the **timeout**, and the
  **tail of its logs** (`job.log-tail-lines` lines).

When Yontrack cannot create the Job because the Kubernetes API does not answer or fails, the failure is transient and
the request can be [retried automatically](auto-versioning.md#automatic-retries). A refusal by the API - a missing
[`Role`](#rbac) for example - or Yontrack not running in a cluster is not retried: it would fail the same way.

A finished Job is kept for `job.ttl`. Its resources are labelled with the UUID of the auto-versioning order:

```bash
kubectl get jobs -n yontrack-jobs -l yontrack.io/auto-versioning-order=<order UUID>
kubectl logs -n yontrack-jobs job/<job name> -c run
```

Before creating a Job, Yontrack deletes any leftover Job carrying the same order label.

## RBAC

The Helm chart does not grant these permissions yet. The service account Yontrack runs as needs the following `Role`,
in the namespace where the Jobs are created (`namespace`, or Yontrack's own namespace if not set). In this example,
the namespace is `yontrack-jobs` and the service account `yontrack` of the `yontrack` namespace:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: yontrack-post-processing
  namespace: yontrack-jobs
rules:
  - apiGroups: ["batch"]
    resources: ["jobs"]
    verbs: ["create", "get", "list", "delete"]
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list"]
  - apiGroups: [""]
    resources: ["pods/log"]
    verbs: ["get"]
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["create", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: yontrack-post-processing
  namespace: yontrack-jobs
subjects:
  - kind: ServiceAccount
    name: yontrack
    namespace: yontrack
roleRef:
  kind: Role
  name: yontrack-post-processing
  apiGroup: rbac.authorization.k8s.io
```

The only Secret Yontrack creates is an **ephemeral one holding the SCM credentials** of the Job. It is owned by the Job
and garbage-collected with it. Yontrack never reads the Secrets referenced by `secrets`, and has no permission to: the
kubelet resolves them.

## Security notes

!!! danger

    Whoever can set an auto-versioning configuration can run an allowed image with an allowed Secret, in your cluster.
    Keep `allowed.images` and `allowed.secrets` as tight as possible, and only list Secrets meant for these Jobs.

* `dockerCommand` is a template, rendered **before** it is given to `sh -c`: a value rendered into it becomes part of
  the script. Read the version from the `$VERSION` environment variable (with the dollar sign alone, it is left to the
  shell) rather than from the `${VERSION}` template, so that a crafted version in the source project cannot run commands
  next to your Secrets.
* Use a dedicated namespace for the Jobs, holding only the Secrets listed in `allowed.secrets`.
* Restrict what the Jobs can reach with a `NetworkPolicy` on that namespace. Yontrack does not create one.
