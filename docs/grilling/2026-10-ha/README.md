# High availability — several backend replicas in steady state

Outcome of the grilling session of 2026-10-13, held once for the whole `initiative: ha` and dispatched
into its issues as each section was settled. It builds on
[`initiative: no-downtime`](../2026-10-no-downtime/README.md), which made a *short* overlap of two
backends safe during a rolling upgrade.

In scope: yontrack/yontrack#2076–#2081, #2083, #2085–#2088, #2090 and yontrack/yontrack-chart#168,
#169. Already done (`initiative: ha-quick-wins`): #2082, #2084, #2089, chart#166, chart#167. #2091
(7.0) was specified before the session.

## Decisions

### Scope

| Decision | Choice |
|---|---|
| Milestone | **6.0**, every backend issue. 6.0 GA waits for the initiative. Chart issues: `6.x`. |
| What HA guarantees | **Planned disruption** (node drain, eviction, rolling upgrade): no failed request. **Unplanned loss of a pod** (crash, OOM, node failure): the requests in flight on that pod fail, the service carries on, queued work is redelivered, and the jobs running there are taken over at their next fire or marked failed. The second promise is written down, so that "HA" is not read as "no failed request even on a crash". |
| Topology | **Active-active**: every replica serves HTTP, runs cluster jobs and consumes every queue, except the single-active-consumer queues of #2082. N replicas, tested and documented from 2. Rejected: active-passive (leader election, a failover gap). |
| Backend switch | **None.** The backend behaves the same at 1 and N replicas: one code path, one test matrix. `ontrack.replicas` in the chart (chart#168) is the only knob. The chart's `ontrack.zeroDowntimeUpgrades` stays (see *Chart*). |
| Chart issues | chart#168 and chart#169 are specified in this session, last. The chart repository has no `status:*` labels: they only get `ready-for-agent`. |

### Two-backend test stack (#2076)

| Decision | Choice | Rejected |
|---|---|---|
| Suites | **KDSL only.** The ITs run the backend in the test JVM with no HTTP server and already simulate "another instance" where it matters. | Two backends for the ITs: a rewrite of the IT harness. |
| Shape | `docker-compose-kdsl-ha.yml`: `ontrack-a`, `ontrack-b`, one Postgres, RabbitMQ and MinIO, no UI. Shares the KDSL slot, sequenced like `-ldap`/`-oidc`. | Its own slot range. |
| Proxy | **nginx**, plain round-robin. A test fixture only: in production the Kubernetes Service balances, nothing goes to the chart. Tests needing "A then B" address the backends directly. | HAProxy, Traefik. |
| Tests | Package `…tests.ha`, `ACCHA*`, base class with `ontrackA`, `ontrackB`, `ontrack` (proxy); task `kdslHaTest`; excluded from `kdslAcceptanceTest`. The full suite cannot run behind the proxy: some tests read per-JVM state (in-memory notifications, mock SCM). | JUnit tags. |
| Pending tests | #2076 lands one smoke test. Each issue writes its own cross-replica test first. | `@Disabled` placeholders. |
| CI | `kdsl-ha` job in `ci.yml` on every full run, parallel to `kdsl`: it gates `status:ready`. | Nightly. |
| Dev stack | No two-backend mode. | — |

### Job engine (#2077)

| Decision | Choice | Rejected |
|---|---|---|
| Engine | **Quartz, clustered JDBC store**, behind `JobScheduler`. Quartz does not interrupt a run on another node nor list the runs of the cluster: the shared status of #2079 carries both. | db-scheduler (less standard); a DB table of next runs in the in-house scheduler (rewriting Quartz). |
| Resolution | Every job comes from a `JobProvider` or a `JobOrchestratorSupplier`, evaluated by every replica; nothing else calls `schedule` (the scorecard recompute jobs become supplier jobs). Local registry refreshed on every replica; triggers and `JOBS` rows reconciled once for the cluster. A fire for an unknown key refreshes once, then skips. | — |
| Job ids | A `JOBS` table with a serial id: same on every replica, so REST, GraphQL and the UI keep `id`. No deprecation. | Addressing by `JobKey` and deprecating `id`. |
| Pause all | Persistent and cluster-wide. `ontrack.config.jobs.paused-at-startup` deprecated (ADR 0018). | Per-node standby. |
| Timing | Fixed rate (was fixed delay), scattering kept on the start time; misfire "fire once now"; `requestsRecovery = false`. | — |
| Rollout | `JdbcJobRunLock` and its pool removed. The search startup rebuild becomes a one-shot Quartz job per type. The double run during the one pre-Quartz rollout only affects beta → beta: changelog only. | Keeping the lock for one release. |

### Settings and properties (#2080, #2081)

| Decision | Choice | Rejected |
|---|---|---|
| Memo | A **read memo per unit of work**: opened by the HTTP filter and around each queue message; settings by type, decoded properties by (type, entity); writes in the unit update it. Jobs open none (a run can last hours). Built by #2080, reused by #2081. | Cache invalidation across replicas (LISTEN/NOTIFY, version column). |
| Settings | One query per settings type (`getAllValues`). | — |
| Properties | **Prefetch** on the main GraphQL list fields, one `IN (...)` query, into the memo. The hot readers are synchronous services, which a `DataLoader` would miss. | `DataLoader`. |
| Measure | `QueryCounter` on `datasource-proxy` in `ontrack-it-utils`. Budgets against today's **cold** cache: one `SecuritySettings` query per request; property queries constant in the number of builds or branches. | — |

### Per-replica jobs and shared job status (#2078, #2079)

| Decision | Choice | Rejected |
|---|---|---|
| Fewer per-replica jobs | `ConnectorStatusJob` and `SCMCatalogMetricsJob` become cluster jobs storing their results in `STORAGE`. Per replica by nature: `EvidenceMetrics`, `EvidenceStorageProbeJob`, the GitHub App rate-limit gauges. | Running them all per replica. |
| Per-replica jobs | Scope `REPLICA`, a local `TaskScheduler`, never Quartz. Not on the jobs page, no history, pause or "run now"; a timer and an error counter tagged by pod. A test lists them. | One status row per replica on the page. |
| Running state | From Quartz's executing fires, instance id = pod name; Quartz's cluster recovery clears a dead pod's. Progress, last run, counters: columns of `JOBS`. | A heartbeat of our own. |
| Stop, run now | `STOP_REQUESTED` polled by the running replica; "run now" answers "already running" or "triggered". Job gauges DB-wide (`max`), duration timer per replica. | — |

### Ordering, Git, key, startup (#2083, #2085, #2086, #2087)

| Decision | Choice | Rejected |
|---|---|---|
| Assisted changes, renames | Both compute from current state, so only interleaving matters. A `QueueProcessor` each (`assisted-change` by build id, `search-rename` by entity id), ack at the end, a blocking advisory lock per key. In-memory lanes and scopes removed. Branch documents have no reconciliation job, so "the reconciliation repairs it" was not an option. | An advisory lock only (work still lost on stop). |
| Git reads | **Fetch on read** behind one `ensureFresh()`, freshness window 1 min. `lookupCommit` and `isRepositorySynched` removed (no callers). | Fetch on every replica each period; reads through SCM APIs. |
| Git reset | A **reset generation** per repository in the DB; a stale clone is re-cloned on next use. `project-sync` status from the shared job status. No broadcast. | A per-replica job or a RabbitMQ fan-out. |
| Encryption key | Generation under a lock, read back. A **key version** in `STORAGE`, checked through the read memo (on each use in jobs); a change reloads the key. `file` store with another live Quartz instance: a warning. | Reload on decrypt failure (a stale replica keeps encrypting with the old key); failing the startup (a dead pod's row lingers). |
| Startup | **One session advisory lock** around all the `StartupService`s. CasC re-applies on each start (idempotent once serialised); no hash, no upsert. | A lock per task, a CasC hash. |

### Metrics and docs (#2088, #2090)

| Decision | Choice | Rejected |
|---|---|---|
| Remaining state | Missing-remote counter in `STORAGE` (atomic upsert); "index being rebuilt" derived from the shared job status; GitHub App rate-limit gauges re-synced by a `REPLICA` job. | — |
| Tags | `application_instance` = pod name (`${HOSTNAME}`, default `ontrack`): changelog and HA page, not the migration page. DB-wide gauges aggregated with `max`. | Relying on the scraper's labels (the InfluxDB and Elastic exports have none). |
| Docs | The guarantees word for word; per-connection Service balancing; Git clones on every pod; Quartz in the connection budget; per-replica jobs off the jobs page; persistent "pause all"; fixed rate; the `file` key store warning. | — |

### Chart (chart#168, chart#169)

| Decision | Choice | Rejected |
|---|---|---|
| `replicas > 1` | The render **fails** without `zeroDowntimeUpgrades: true`, naming the value. HA is two explicit values. | `replicas > 1` implying the rolling mode. |
| Default | `zeroDowntimeUpgrades` stays `false`. | Flipping it in 6.0 (moves everyone to ephemeral volumes and a hook Job). |
| CI | Drain and rolling upgrade with zero failures, plus an **unplanned loss**: a force-deleted pod, failures limited to its in-flight requests, recovery within 10 s, no job left running on it. | — |

### Recording

ADR 0021, *Backend replicas share no state in memory*, records the rules every future change has
to follow; `.claude/rules/backend.md` points to it.

## Issues

Landing order. #2076 first; the rest follow their native dependencies.

| # | Repository | Issue | Depends on |
|---|---|---|---|
| 1 | yontrack/yontrack | [#2076](https://github.com/yontrack/yontrack/issues/2076) KDSL acceptance stack with two backends behind a proxy | — |
| 2 | yontrack/yontrack | [#2080](https://github.com/yontrack/yontrack/issues/2080) Settings read once per request instead of a cache | #2076 |
| 3 | yontrack/yontrack | [#2081](https://github.com/yontrack/yontrack/issues/2081) Properties loaded in batches, without a cache | #2080 |
| 4 | yontrack/yontrack | [#2077](https://github.com/yontrack/yontrack/issues/2077) Quartz job scheduler, clustered through the database | #2076 |
| 5 | yontrack/yontrack | [#2078](https://github.com/yontrack/yontrack/issues/2078) Jobs that run on every replica | #2077 |
| 6 | yontrack/yontrack | [#2079](https://github.com/yontrack/yontrack/issues/2079) Job status shared by all replicas | #2077 |
| 7 | yontrack/yontrack | [#2083](https://github.com/yontrack/yontrack/issues/2083) Assisted changes and search renames ordered across replicas | #2076 |
| 8 | yontrack/yontrack | [#2085](https://github.com/yontrack/yontrack/issues/2085) Git clones fresh and resettable on every replica | #2079 |
| 9 | yontrack/yontrack | [#2086](https://github.com/yontrack/yontrack/issues/2086) Encryption key shared safely by all replicas | #2080, #2077 |
| 10 | yontrack/yontrack | [#2087](https://github.com/yontrack/yontrack/issues/2087) Startup tasks safe when several backends start together | #2076 |
| 11 | yontrack/yontrack | [#2088](https://github.com/yontrack/yontrack/issues/2088) Per-replica counters, flags and gauges | #2078, #2079 |
| 12 | yontrack/yontrack | [#2090](https://github.com/yontrack/yontrack/issues/2090) Operations doc: high availability | all of the above, chart#168 |
| 13 | yontrack/yontrack-chart | [#168](https://github.com/yontrack/yontrack-chart/issues/168) Several backend replicas with disruption budgets and topology spread | a backend release with #2076–#2088 |
| 14 | yontrack/yontrack-chart | [#169](https://github.com/yontrack/yontrack-chart/issues/169) CI: two replicas survive a node drain and a rolling upgrade | chart#168 |

Backend milestone 6.0, chart milestone `6.x`. `/run-initiative` runs the `yontrack/yontrack` issues
and names the chart ones as out of reach.
