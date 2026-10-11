# Upgrades without downtime — Yontrack 6.0

Outcome of the grilling session of 2026-10-11. Today an upgrade restarts the backend pod of a
1-replica StatefulSet, and Yontrack is unavailable for the restart plus the migrations. The goal is the
**minimal** set of changes that lets an upgrade *within a major* complete with no failed request and no
lost queued work. This is not a high-availability setup: one replica in steady state, two only during
the rollout.

The issues are listed at the end, under the `initiative: no-downtime` label, across three
repositories.

## Where we start from

Facts read from `main` (42c51f08c7) and from the chart (`yontrack-chart` e5aa6d1) before the decisions
were taken.

- **Chart.** The backend is a `StatefulSet` with no `replicas` (so 1), no `updateStrategy`, one
  `ReadWriteOnce` PVC at `/var/ontrack/data`, no `preStop`, the default 30s grace period, and a
  readiness probe with a 60s period. A StatefulSet cannot surge: the old pod stops before the new one
  starts. The UI is a separate `Deployment`, stateless (next-auth JWT). The key store defaults to
  `jdbc`. The deployments of demo.dev and self.dev (`yontrack-infra-gitops`) commit `helmfile template`
  output and let ArgoCD sync plain manifests; both use the `secret` key store.
- **Migrations** run at startup, inside `StartupStrategy`, followed synchronously by every
  `StartupService`, before readiness. Only Flyway's own lock protects concurrent migrators. Several 6.0
  migrations are destructive (`DROP TABLE SEARCH_DOCUMENTS`, `DROP TABLE ENTITY_DATA`, deletions of
  removed features' data) and V102 widened hot columns under an ACCESS EXCLUSIVE lock. No
  expand/contract discipline exists.
- **Jobs** live in the in-memory `DefaultJobScheduler`. No lock, no leader election: two instances run
  every scheduled and cron job twice. The auto-versioning scheduler is check-then-act and can
  dispatch an order twice.
- **RabbitMQ** queues are shared and durable, with one consumer per queue per instance; auto-versioning
  relies on that single consumer for its per-target-branch serialisation. Notifications, workflows and
  auto-versioning ack **before** processing (`QueueAckMode.IMMEDIATE`): a pod stopped mid-message
  loses the work, today, on every restart.
- **Per-instance state:** the `settings` (10h) and `properties` (1 day) Caffeine caches with local
  eviction; the startup search rebuild (unlocked, in a coroutine, deletes documents it did not write);
  connector statuses; a few client caches. Sessions are stateless and CSRF is off. The audit trail
  already takes advisory locks and is multi-instance safe. With the `jdbc` or `secret` key store,
  nothing irreplaceable is on the PVC: it holds re-creatable Git clones.
- **Nothing** in the ADRs, dev guide or user docs mentions replicas, rolling upgrades or zero downtime.

## Decisions

### Scope

| Decision | Choice |
|---|---|
| What "no downtime" means | No request fails, read or write, and in-flight work completes. CI pipelines calling the API cannot retry. |
| Which upgrades | Within a major, from 6.0.x → 6.0.y onward. **5.5 → 6.0 keeps its downtime**, as any major upgrade may. |
| Deployment target | Helm chart (`yontrack/yontrack-chart`), operated mostly through ArgoCD. Docker Compose keeps the restart. |
| Who orchestrates | Kubernetes rolling-update semantics and readiness probes. The application never detects other instances; it *tolerates* them for the overlap. |
| Rollback | Best effort. |
| Configurability | **One Helm value**, `ontrack.zeroDowntimeUpgrades`, default `false` = today's behaviour (restart on upgrade). `true` switches every piece below. demo.dev and self.dev run with `true`. |
| Milestone | **6.0.** The feature pays off from the first upgrade after the release that ships it; in 6.0 that is Beta.N → Beta.N+1, with every 6.x upgrade gapless afterwards. In 7.0 the first gapless upgrade would be a year away. |

### Schema: expand and contract

Destructive changes within a major are **not forbidden**. They move to a second phase that runs after the
previous version's pod is gone.

- `db/migration` is the **expand** phase, run at startup as today. A test fails `DROP`, `RENAME`,
  `ALTER COLUMN TYPE`, `TRUNCATE`, unqualified `DELETE`, `SET NOT NULL`, `NOT NULL` without default,
  `CREATE INDEX` without `CONCURRENTLY`, `ADD CONSTRAINT` without `NOT VALID`, with an allow-list file.
  Lock hazards count: V102 blocked the old pod as surely as a drop would have.
- `db/contract` is the **contract** phase: `DROP` and `DELETE` only, its own history table
  (`schema_version_contract`), the same version sequence. A test fails an expand that recreates a name a
  pending contract drops, and a version used in both locations.
- A property selects when contracts run: `startup` (default; dev stacks, ITs, KDSL and Compose are
  unchanged) or `deferred`. A run-and-exit mode of the regular image applies pending contracts and exits.
- In zero-downtime mode the chart sets `deferred` and runs the contract phase from a
  `post-install,post-upgrade` hook Job, with the explicit `argocd.argoproj.io/hook: PostSync`
  annotations as well, because pre-rendered manifests do not get Helm's hook mapping. Plain Helm needs
  `--wait`. A failed hook leaves the application up on the expanded schema; the next upgrade retries.
- Migrations up to the one at HEAD when the test lands are grandfathered.

Rejected: *declared downtime releases* (a flag per release, operators use `Recreate` for that version),
because some upgrades would keep their downtime; and *the app runs the contract when alone* through a
heartbeat table, because a hard-killed pod would block everything until its heartbeat expired, and it is
HA machinery.

### Overlap: two instances for a minute

| Problem | Choice | Rejected |
|---|---|---|
| Jobs run twice | `pg_try_advisory_lock` per job run, on a dedicated connection for the run; the loser skips silently (debug log, no history row). Closes the auto-versioning scheduler race too. | "Do not start jobs until alone" (needs instance awareness); leader election (HA). |
| Lost queued work | Ack after processing. Redelivery is idempotent through the queue record state under `SELECT ... FOR UPDATE`; auto-versioning also takes an advisory lock per routing key, restoring per-branch serialisation across two consumers. A duplicate notification on a crash between send and commit is accepted and documented. Workflow nodes outlive their message: each is claimed by its process, whose heartbeat keeps it; the nodes of a process without heartbeat are resumed if they had not started executing, put in error otherwise (#2064). | Leaving `IMMEDIATE` ack: the upgrade would still lose work, less visibly. |
| Stale caches | `settings` TTL 1 minute, `properties` 5 minutes. | A cache bus or DB version column (HA). |
| Search rebuild race | Deferred past the overlap (configurable delay) and run under the job lock. | Accepting an incomplete index. |
| Draining | `spring.lifecycle.timeout-per-shutdown-phase` 45s; a test that RabbitMQ listeners finish the message in hand on shutdown. | — |

### Chart

- The backend becomes a `Deployment` in both modes, one template. Default mode: `Recreate` with today's
  PVC, which behaves like the StatefulSet. Zero-downtime mode: `RollingUpdate` with `maxSurge 1`,
  `maxUnavailable 0`; readiness period 5s, threshold 2; `preStop` sleep 10s; grace period 60s; the UI
  Deployment rolls the same way; the render fails with a `file` key store.
- The data volume in zero-downtime mode is a **generic ephemeral volume** (an inline PVC template,
  one per pod, deleted with it), sized by the existing `persistence.size`; an `emptyDir` with
  `sizeLimit` is the fallback. GA since Kubernetes 1.23, supported on OpenShift 4.11+ where
  `restricted-v2` allows `ephemeral` volumes. `persistence.enabled` is ignored in this mode so the flag
  stays the only value to change. Rejected: an `emptyDir` alone (node disk, eviction loop on a large
  clone) and a `ReadWriteMany` PVC (ties the feature to RWX storage and needs cross-process Git locks).
- ArgoCD sync waves order the backend before the UI. Outside ArgoCD the two roll together; the
  supported direction is old UI against new backend, which the deprecation policy already guarantees.
  A user with a page open keeps the old UI, including its Next.js chunks, until reload.
- The switch from StatefulSet to Deployment is a one-time restart, in either mode. Git clones are
  re-created on first use after each rollout; pruning them by last use is a possible follow-up.

### Proof

End to end in the chart repository: a `kind` CI job installs the previous chart, hammers the API and the
UI, upgrades with `--wait` and asserts zero failed requests, the hook `Succeeded` and the contract
migration applied. A cheaper job covers default mode. Lock and migration rules are unit and integration
tested in `yontrack/yontrack`. On demo.dev, the second sync after flipping the flag is the first real
gapless upgrade.

## Issues

Landing order. Backend first, the ack change earliest for beta exposure.

| # | Repository | Issue |
|---|---|---|
| 1 | yontrack/yontrack | [#2055](https://github.com/yontrack/yontrack/issues/2055) Queue consumers ack after processing and tolerate redelivery |
| 2 | yontrack/yontrack | [#2056](https://github.com/yontrack/yontrack/issues/2056) Job runs are guarded by a Postgres advisory lock |
| 3 | yontrack/yontrack | [#2057](https://github.com/yontrack/yontrack/issues/2057) Two-phase database migrations: expand at startup, contract after the rollout |
| 4 | yontrack/yontrack | [#2058](https://github.com/yontrack/yontrack/issues/2058) Overlap-safe startup and shutdown: cache TTLs, deferred search rebuild, graceful drain |
| 5 | yontrack/yontrack-chart | [#150](https://github.com/yontrack/yontrack-chart/issues/150) Backend as a Deployment with a rolling update, ephemeral data volume and tuned probes |
| 6 | yontrack/yontrack-chart | [#151](https://github.com/yontrack/yontrack-chart/issues/151) Contract-migration post-upgrade hook, ArgoCD sync waves and a rolling-upgrade CI test |
| 7 | yontrack/yontrack-infra-gitops | [#236](https://github.com/yontrack/yontrack-infra-gitops/issues/236) demo.dev and self.dev: enable zero-downtime upgrades |
| 8 | yontrack/yontrack | [#2059](https://github.com/yontrack/yontrack/issues/2059) Operations doc: upgrading Yontrack without downtime |

Backend milestone 6.0; chart milestone `6.x`; the gitops repository has none. `/run-initiative` runs
the `yontrack/yontrack` issues only and names the others as out of reach.
