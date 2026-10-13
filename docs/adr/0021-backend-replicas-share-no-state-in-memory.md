# Backend replicas share no state in memory

Since `initiative: ha` (6.0), Yontrack runs **N backend replicas permanently**, active-active: every
replica serves HTTP, runs cluster jobs and consumes the queues. The backend has **one code path**
for 1 and N replicas — no switch, no "HA mode" — so these rules hold for every change, whether or
not the installation runs several replicas. `initiative: no-downtime`
(`docs/grilling/2026-10-no-downtime/`) had made a short overlap of two backends *tolerable*; this
ADR makes N replicas the normal case. The decisions behind it are in `docs/grilling/2026-10-ha/`.

## Decisions

**No cross-request in-memory cache of database data.** A value read from the database is held, at
most, by the **read memo of the unit of work** — opened by the HTTP filter for a request and around
each queue message, holding settings by type and decoded properties by (type, entity), updated by
the writes of the same unit. Jobs open none: a run can last hours. Lists avoid N+1 queries by
**prefetching** into the memo (one `IN (...)` query per list), not by caching. The Caffeine
`settings` and `properties` caches (1 and 5 minutes, #2058) are gone: a revoked permission or a
rotated secret was still honoured for that long on the other replicas.

*Rejected:* cache invalidation across replicas (Postgres `LISTEN/NOTIFY`, a version column) — more
machinery, and a missed notification is a stale permission.

**Jobs come from providers and run through Quartz.** Every job is registered by a `JobProvider` or
a `JobOrchestratorSupplier`, which every replica evaluates, so that any replica can resolve a job
from its `JobKey`; nothing else calls `JobScheduler.schedule`. Cluster jobs run in Quartz with a
clustered JDBC store: one run per period in the cluster, pause stored in the database, no
concurrent run of the same job. Their status lives in the `JOBS` table and in Quartz's executing
fires, so every replica shows the same; stop is a flag the running replica polls.

**Per-replica state only where it is per pod by nature** — a gauge registered in this pod's meter
registry, the reachability of a store from this pod, a Git clone on this pod's volume. It is
refreshed by a `REPLICA`-scoped job (a local scheduler, not Quartz, not on the jobs page) or
checked lazily on use. A state that is the same for the cluster is stored in the database
(`STORAGE`), even when one job computes it.

**Per-pod resources are invalidated by a generation, not a broadcast.** A Git clone is reset by
bumping its repository's *reset generation* in the database; a replica whose clone is older
re-clones it on next use. The encryption key carries a *key version* in `STORAGE`, checked through
the read memo. Nothing pushes a message to every replica.

**Ordered work goes through a queue, under a lock.** Work posted after a commit and processed later
(assisted changes, search renames, auto-versioning) is a RabbitMQ `QueueProcessor` message, acked
after processing, processed under a blocking advisory lock per key. An in-memory lane or coroutine
scope is per replica and is lost when the process stops.

**Startup writes are serialised.** All the `StartupService`s run under one session advisory lock,
after Flyway; what they write must stay idempotent.

## Consequences

- Recorded before it is built: until the issues of `initiative: ha` land, the code still has the
  caches, the in-house scheduler and the in-memory lanes this ADR rules out. The rules apply to new
  code now.

- A new `@Cacheable`, a singleton `Map` of business data, an in-memory work queue, or a
  `jobScheduler.schedule(...)` call outside a provider is a defect, whatever the number of replicas
  the author had in mind. `.claude/rules/backend.md` says it in one line.
- The two-backend KDSL stack (#2076, `kdslHaTest`) is where a cross-replica behaviour is tested; a
  fix for one writes its test there first.
- Gauges with database-wide values are reported by every replica and are aggregated with `max`;
  `application_instance` is the pod name.
- Advisory locks have no shared helper yet; each takes a lock class of its own, listed next to the
  existing ones (`JOBR` goes away with Quartz; `AVQU`, `AVMG`, `TRAI`, `EVID` stay).
