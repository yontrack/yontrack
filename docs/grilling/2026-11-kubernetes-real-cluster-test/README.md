# Kubernetes post-processing: acceptance test against a real cluster — grilling session

Grilling session of 2026-11-23 on the acceptance test of the `kubernetes` auto-versioning
post-processing against a real cluster, for Yontrack 6.0. It follows
[the session on the post-processing itself](../2026-11-kubernetes-post-processing/README.md), which
left this test as a placeholder. The full specification is the body of
[#2172](https://github.com/yontrack/yontrack/issues/2172); this file keeps the reasoning.

## The request

- #2171 tests the Kubernetes Job post-processing with unit tests and the fabric8 mock server only,
  and the mock server runs no pod.
- Add a real cluster (k3s or kind) to a test stack, so that an acceptance test runs a real Job:
  clone, user step, commit and push.
- Open points at the start: which stack, the wiring through `buildSrc` and `compose/` with ports per
  worktree, which Git server the Job pushes to, the CI cost.
- The issue must be ready for an agent that delegates tasks to subagents on lower models.

## Research (6.0, `main`)

- **Cluster access is in-cluster only** (`KubernetesClientConfiguration`): `KUBERNETES_SERVICE_HOST`
  and `PORT`, plus the token, `ca.crt` and `namespace` files of the service account. No kubeconfig,
  no API URL. `inClusterConfig` has no test.
- **Only the KDSL stack can host the test**: Yontrack runs there as a container on the compose
  network, with the `dev` profile. In the integration stack it runs inside the test JVM.
- **No Git server is both drivable by Yontrack's SCM API and cloneable by a pod.**
  - The mock SCM, used by all 23 auto-versioning acceptance tests, is in memory and has no clone
    access.
  - The HA trick (a GitLab configuration over `file:///git-remote`) only serves indexation:
    `createBranch`, `download`, `upload` and `createPR` go through the GitLab REST client.
  - No acceptance test runs auto-versioning against real GitHub; the `@TestOnGitHub` tests post
    canned webhook payloads.
- **The clone URL has no scheme restriction**: `http://` reaches the harness as-is.
- **The existing post-processing acceptance tests are all mocks** inside the backend (`mock-gitlab`,
  `mock-bitbucket-cloud`, the mock Jenkins client), and all use the mock SCM.
- **CI**: no job runs a privileged container; every job is `ubuntu-latest` with compose on the
  runner's Docker daemon. The KDSL job takes about 7.5 minutes per shard. The HA variant has its own
  compose file, Gradle task and CI job.
- **Nothing in the repo uses k3s, kind or Testcontainers.**

## Decisions

### Scope of the test

1. **Two scenarios**: a happy path and a failing user step. The real cluster earns its cost on what
   the mock cannot show — image pull, the clone, the credentials and the push. Timeouts, TTL, cleanup
   and log edge cases stay with the mock-server tests.
2. **The happy path covers the allow-lists and a secret reference too**: a pinned `busybox`,
   allow-listed exactly, writing a file from `$VERSION` and from a variable injected from a
   Kubernetes Secret.
3. **The failure path is `exit 3`**: the audit names the `run` step, the exit code and the log tail,
   and nothing is pushed beyond the version change.
4. **Assertions go through Yontrack and Git only**, with no Kubernetes client in the test code. The
   test checks what a user sees; the manifest details are already unit-tested.

### Stack

5. **k3s, not kind**: one container, like the rest of the middleware. kind drives Docker from outside
   and creates its own containers, which fits the compose stacks and the per-worktree ports poorly.
6. **A separate KDSL variant**, modelled on HA: its own compose file, Gradle tasks and CI job. The
   main suite keeps its start-up time and gets no privileged container, and a k3s failure cannot take
   the other auto-versioning tests down.
7. **No opt-in flag locally**: the variant's one command brings everything up, as for every stack.
8. **The development stack is out of scope.**

### Reaching the cluster

9. **The Yontrack container is made to look in-cluster**, with no production change: the two
   environment variables and a shared volume at the service account path. It exercises the real
   `inClusterConfig` path. Rejected: running Yontrack as a pod in k3s (the image and all the
   middleware would have to reach the cluster), and an out-of-cluster mode in the extension (a
   product feature of its own).
10. **A one-shot bootstrap service applies the RBAC exactly as the user docs give it**, and mints the
    token. The test then proves the documented `Role` is sufficient; if it is not, the docs are wrong.
11. **The namespace property stays blank**: the namespace comes from the mounted file, the default
    path of a real installation.

### Git server

12. **The mock SCM gains a dev-only clone access backed by a real Git server.** At
    `getCloneAccess()`, right after auto-versioning has uploaded the version change, it writes its
    in-memory repository to a bare repository and pushes it. Off by default, driven by properties set
    in the Kubernetes variant only.
    - Rejected: real GitHub behind `@TestOnGitHub` — it runs only where the secrets exist, depends on
      github.com from inside the pods and leaves branches and pull requests to clean up.
    - Rejected: a real GitLab or Bitbucket container — about 4 GB and minutes to boot.
    - Cost, accepted: the mock's in-memory pull request does not see the pod's commit, so the
      assertion is on the Git server.
13. **The Git server runs inside k3s**, reached by pods through cluster DNS and by Yontrack and the
    test through a NodePort. That means two URLs, push and clone. A compose service would need pods
    to resolve a compose service name, which k3s in Docker does unreliably.
14. **Basic auth on clone and push**: an anonymous server would leave the ephemeral Secret and the
    credential helper, the riskiest part of the harness, untested.

### CI and documentation

15. **The test runs on every full CI run**, in a job copied from `kdsl-ha`. A gated test is the one
    nobody notices is broken.
16. **The duration is measured, and the decision stays Damien's**: the agent reports it and flags it
    above 10 minutes of runner time, without gating the job itself.
17. **`DEVELOPMENT.md` and `.claude/rules/buildsrc.md` only.** No new ADR: ADRs 0004, 0012 and 0013
    cover the stack scheme, and this adds a variant, not a rule. No user docs, no demo seed entry, no
    mobile impact.
18. **Milestone 6.0**, like #2171.

## Orchestration

One issue, with a *Delegation* section in its body.

- **The lead** runs on the session's model. It keeps the contracts between tasks (property names,
  service names, ports, URLs), the review of every diff, a rerun of each task's "done when" check,
  the full local run and the landing workflow. A subagent's report is a claim; the rerun is the
  proof.
- **Subagents** work in the lead's worktree on disjoint files, and never commit, push or touch the
  issue. After two failed attempts, the lead does the task itself.
- **Seven tasks**: a spike, the mock SCM clone access, the `buildSrc` wiring, the compose variant and
  the two scenarios on Sonnet; the CI job and the docs on Haiku, as are lookups. The spike, the mock
  SCM and the wiring start in parallel.
- **If the spike shows decision 9 cannot work, the lead stops and reports**: it does not switch to a
  rejected alternative. Moving the Git server to a compose service is the lead's call.
