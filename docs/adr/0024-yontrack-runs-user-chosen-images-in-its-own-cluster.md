# Yontrack runs user-chosen images in its own cluster

Until 6.0, Yontrack never ran anybody's code. An auto-versioning post-processing - regenerating a
lock file, running a formatter after the version bump - was a remote pipeline that Yontrack
launched and waited for: GitHub Actions, Jenkins, GitLab, Bitbucket Cloud. The CI system owned the
runner, the clone credentials and the blast radius.

The `kubernetes` post-processing (#2171) removes the CI system: Yontrack creates a Kubernetes Job
in the cluster it runs in, with an image and a command taken from the auto-versioning
configuration. Whoever can set an auto-versioning configuration on a branch - a project-level
right, held by many - now chooses a container that runs next to Yontrack, on a repository Yontrack
holds write credentials for. The decisions are in `docs/grilling/2026-11-kubernetes-post-processing/`.

## Decisions

**Off by default, and switched on by the deployer only.** The extension does nothing unless
`ontrack.extension.kubernetes.enabled` is set: no post-processing bean, no Kubernetes client, and
`kubernetes` is an unknown post-processing id.

**Every knob is a static configuration property.** No settings page, no CasC context: the
namespace, the harness image, the allow-lists, the timeouts, the pod identity and the resources
are a security boundary, and they are set by whoever deploys Yontrack, where the Helm values are.
An administrator of the application cannot widen them from the UI, and neither can a leaked
administrator token.

**Deny by default.** A post-processing may only run an image matching `allowed.images`, and may
only reference a Secret named in `allowed.secrets`. Both lists are **empty by default, and empty
denies everything**: enabling the extension runs nothing until the deployer has said what may run.
A refusal names the property to set.

**The user step gets neither the SCM credentials nor the Git directory.** The Job has three steps
on shared volumes: `clone` and `push` run the **harness** - a stock Git image and a script supplied
by Yontrack - and `run` runs the user's image. The credentials are in an ephemeral Secret owned by
the Job, mounted in the harness steps only, never written to the remote URL nor to a Git
configuration file. The clone uses a separate Git directory on a volume that `run` does not mount:
with it, the user step could plant a hook or rewrite the configuration that `push` would then
execute with the credentials. **The harness commits; the user step never does.**

**The pod belongs to the deployer.** No service account token, non-root, no privilege escalation,
all capabilities dropped, one uid/gid/fsGroup for the three steps, resources and image pull secrets
from the static configuration. Nothing of it can be set from an auto-versioning configuration.

**Yontrack stores no secret value.** An auto-versioning configuration names Kubernetes Secrets; the
kubelet resolves them. Yontrack never reads them.

**One cluster, one namespace, in-cluster configuration only.**

*Rejected:* settings in the application, like the other four post-processings. Their settings pick
a default repository or a retry count; these decide what code runs with which secrets.

*Rejected:* letting the user step commit, as a CI pipeline does. It needs the credentials, or a Git
directory that the credentialed step trusts afterwards.

*Rejected:* a harness image published by Yontrack. An artifact more in the release pipeline, for a
dozen lines of shell that the Job can carry itself.

*Rejected:* Yontrack cloning on its own disk and shipping the working copy to the Job. The Job
holds the working copy; nothing touches the disk of a Yontrack pod, and no replica has anything to
remember (ADR 0021).

## Consequences

- **No `git` command works in the user step**, and an image that only works as root fails. Both
  are documented as constraints, not bugs.
- What the user step can still do: change any file of the working copy, which the harness then
  commits to the upgrade branch - the same power as the pipeline of the other post-processings,
  and it ends in a pull request or in the branch's own protections. It can also reach the network:
  a `NetworkPolicy` is the deployer's, and out of scope.
- `dockerCommand` is a template, like in the other four post-processings, and it is rendered into
  the script given to `sh -c`: a version crafted in the source project could run commands in the
  user step, next to the allowed Secrets. The step still has no SCM credentials, and the injected
  `VERSION` environment variable is the documented way to read the version.
- The harness runs Git with command-line configuration only (`GIT_CONFIG_NOSYSTEM`,
  `GIT_CONFIG_GLOBAL`, no hooks), so that nothing the user step leaves in the work tree - a
  `.gitattributes`, a `.git` directory - is executed next to the credentials, whatever the harness
  image carries.
- Only a failure of the Kubernetes API is retried. A refusal (a missing `Role`), or Yontrack not
  running in a cluster, fails the order for good.
- The allow-lists are only as tight as their patterns: `*` in `allowed.images` allows everything.
- A GitHub App token lasts one hour and is fetched when the Job is created, so `job.max-timeout`
  stays well under that.
- The Yontrack service account needs a `Role` in the namespace: Jobs (create, get, list, delete),
  Pods (get, list), Pods/log (get), Secrets (create, delete). The Helm chart does not grant it yet;
  the user documentation carries it.
- The post-processing contract stays synchronous: a queue consumer is blocked while the Job runs.
  Nothing in memory links Yontrack to a running Job - Jobs are labelled with the order UUID, and a
  retried order deletes its leftover (ADR 0021).
- A second feature running Jobs in the cluster reuses the same properties and the same rules, or
  amends this ADR.
