# Auto-versioning post-processing through Kubernetes jobs — grilling session

Grilling session of 2026-11-23 on a fifth auto-versioning post-processing for Yontrack 6.0, which runs
the user's image as a Kubernetes Job instead of launching a remote pipeline. Issues:
[issues.md](issues.md). The full specification is the body of
[#2171](https://github.com/yontrack/yontrack/issues/2171); this file keeps the reasoning.

## The request

- Post-processing is done today by remote pipelines (GitHub, Jenkins, GitLab, Bitbucket Cloud), which
  users must write, host and wire.
- Optionally, run it as a Kubernetes Job: an image, parameters, maybe secrets, set in the
  auto-versioning configuration.
- The CI used to clone and push; here Yontrack provides a **harness**: clone the target, run the job
  on the working copy, get the result, push to the branch.
- Its own extension, enabled by a static configuration property, off by default.
- The Helm chart (service account, RBAC) comes later.

## Research (6.0, `main`)

- **Extension point**: `PostProcessing<T>` — synchronous and blocking, inside the transaction of a
  queue consumer under an advisory lock. Discovered as an extension bean. Its config is parsed at
  processing time only, never when the auto-versioning configuration is saved.
- **Four implementations**, not three: GitLab has one too. All share a shape: settings with defaults,
  `retries × retriesDelaySeconds` as the timeout (5 minutes by default), a run URL reported through
  `onPostProcessingInfo`, a failure exception carrying a link.
- **When post-processing starts, the upgrade branch already exists on the remote** with the version
  bump, committed through the SCM API.
- **`SCM` exposes no clone credentials**, but `GitConfiguration.authenticator` has them for all four
  Git SCMs, including short-lived GitHub App tokens.
- **No Kubernetes client** in the build; **no generic "extension enabled" switch** — the pattern is
  `@ConditionalOnProperty` on a constant of a `@ConfigurationProperties` class, with nested properties.
- **No per-project secret storage**; an auto-versioning configuration carries no secret value today
  (Jenkins `credentials` are ids only).
- **UI**: one `Display.js` per post-processing id, no form.
- **ADR 0021**: no state in memory across replicas — nothing may remember a running Job.

## Decisions

### Shape of the job

1. **The Job holds the working copy, not Yontrack.** One Job, three sequential containers on shared
   volumes: `clone` (init, harness), `run` (init, user image), `push` (main, harness). Yontrack
   creates, polls and reads the result. No file transfer, nothing on the Yontrack pod's disk.
2. **The harness is a stock Git image plus a script supplied by Yontrack.** Publishing a harness image
   would add an artifact to the release pipeline for a dozen lines of shell.
3. **The harness commits; the user step never does.** All changes become one commit; no change is a
   success; a non-zero exit pushes nothing.
4. **Shallow clone of the upgrade branch**, committed with an identity from static configuration.
5. **`backoffLimit: 0`**: retrying belongs to auto-versioning's reschedule, not to Kubernetes.

### Trust model

6. **The user step gets no SCM credentials**: an ephemeral Secret owned by the Job, mounted in the
   harness steps only, never written to a URL or a Git config.
7. **The user step gets no Git directory either.** Otherwise it could plant a hook, or rewrite the
   config, that `push` would execute with the credentials. The clone uses a separate Git directory on
   a volume `run` does not mount. Cost, accepted: no `git` command works in the user step.
8. **Allow-lists for images and Secret names, deny-by-default**: anyone who can set an
   auto-versioning configuration could otherwise run any image with any Secret of the namespace.
9. **Pod settings are the admin's**: no service account token, non-root, no privilege escalation,
   resources, image pull secrets, and one uid/gid/fsGroup for the three containers so that the user
   step can write what the harness cloned. An image needing root fails.
10. **User secrets are references to existing Kubernetes Secrets.** Yontrack stores no value.
11. **Every admin knob is a static property**, unlike the other post-processings' settings: they are
    a security boundary set by whoever deploys, and map onto Helm values.

### Scope

12. **One cluster (in-cluster), one namespace.**
13. **All four Git SCMs** through a new optional capability on `SCM`.
14. **Module `ontrack-extension-kubernetes`**, id `kubernetes`, switch
    `ontrack.extension.kubernetes.enabled`. The name leaves room for other Kubernetes features.
15. **fabric8** client: Kotlin-friendly, automatic in-cluster configuration, a mock server for tests.
16. **Same synchronous contract** as the other four. An asynchronous one would change the extension
    point for all five. Jobs are labelled with the order UUID; a retried order deletes its leftover.
17. **Config names follow the other four** (`dockerImage`, `dockerCommand`, `commitMessage`), plus
    `env`, `secrets` (`{secret, key, env}`) and `timeoutSeconds`.
18. **No URL to report**: the audit gets the namespace and Job name; a failure names the step, its
    exit code and the log tail, with an empty link.
19. **Validation at processing time only**, as today for the other four.
20. **Tests need no cluster**: manifest unit tests and the fabric8 mock server.
21. **No demo seed entry**: off by default, and the demo has no cluster for Jobs.

### Non-goals

`NetworkPolicy`, a concurrency limit beyond the queue consumers, file-mounted secrets, volumes,
remote clusters, per-project namespaces, save-time validation, asynchronous post-processing.

## Orchestration

One issue, sliced for subagents in its body. When the orchestrating session runs on Fable, the
subagents are spawned on Opus or Sonnet.
