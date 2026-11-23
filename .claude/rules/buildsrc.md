---
paths:
  - "buildSrc/**"
  - "compose/**"
---

# Test stack wiring (`buildSrc`, `compose/`)

- A new service in `compose/docker-compose-it.yml` is not wired until its port is added to
  `ItStack.BASE_PORTS` **and** the property pointing at it is added to
  `ItStackInstance.systemProperties`, both in `buildSrc`. Add a case to `ItStackTest` with it.
- The `integrationTest` task passes `spring.datasource.url`, `spring.rabbitmq.port`,
  `spring.elasticsearch.uris`, `ontrack.config.vault.uri` and the
  `ontrack.extension.audit-trail.storage.*` of the MinIO bucket to the tests; the
  `kdslAcceptanceTest` task passes the `ontrack.acceptance.*` properties, and an explicit `-D`
  still wins.
- MinIO's image, bucket and credentials live in `Minio` and are repeated in the dev, IT and
  KDSL Compose files and in `scripts/dev-stack.sh`: change all of them together, `MinioTest`
  checks it.
- KDSL acceptance stacks have four slots, not ten, and the `-ldap`, `-oidc`, `-ha` and
  `-kubernetes` variants share the slot of the main one.
- The `-ha` variant (`compose/docker-compose-kdsl-ha.yml`, #2076) is two backends behind an nginx
  proxy, run by `./gradlew :ontrack-kdsl-acceptance:kdslHaTest` for the `tests.ha` package only.
  The proxy is on the slot's Yontrack port; A and B have their own app and management ports
  (`KdslStack.BASE_HA_*`, recorded in `.yontrack-kdsl/instance.env`), passed to the tests as
  `ontrack.acceptance.ha.{a,b}.url` and `.mgt.url` by `KdslStackInstance.haSystemProperties`, with
  the Compose project as `ontrack.acceptance.ha.project` for the tests killing a backend. The
  backends' host names are `ontrack-a` and `ontrack-b`: the replica a job runs on (#2079). Both
  mount `ontrack-kdsl-acceptance/build/kdsl-ha/git` as `/git-remote`, the bare repositories the Git
  tests write (`ontrack.acceptance.ha.git.dir`, #2085).
- The `-kubernetes` variant (`compose/docker-compose-kdsl-kubernetes.yml`, #2172) is a single-node
  k3s cluster in a privileged container beside one backend, run by
  `./gradlew :ontrack-kdsl-acceptance:kdslKubernetesTest` for the `tests.kubernetes` package only.
  The API server and the Git NodePort are `KdslStack.BASE_KUBERNETES_API` and `BASE_KUBERNETES_GIT`
  (16443, 17080), recorded in `.yontrack-kdsl/instance.env`; the tests get the Git URL as
  `ontrack.acceptance.kubernetes.git.url` from `KdslStackInstance.kubernetesSystemProperties`. The
  backend is in-cluster without being a pod: `KUBERNETES_SERVICE_HOST`/`PORT` point at `k3s`, and
  `k3s-bootstrap` (`compose/kubernetes/bootstrap.sh`) writes the service account files (`token`,
  `ca.crt`, `namespace`), which the backend mounts where a pod gets them.
  `compose/kubernetes/manifests/rbac.yaml` is the documented RBAC with only the namespace changed,
  kept identical by `KubernetesRbacTest`. The mock SCM's `ontrack.config.extension.scm.mock.git.*`
  properties are set only in this variant.
- `docs/adr/0012-parallel-integration-test-stacks.md` and
  `docs/adr/0013-parallel-kdsl-acceptance-stacks.md` explain the scheme.
