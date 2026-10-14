# High availability

Deployed with its [Helm chart](https://github.com/yontrack/yontrack-chart), Yontrack can run several
backend replicas permanently, so that a node drain, the eviction of a pod or the loss of a pod is no
longer an outage. This page says what is guaranteed, what it requires, and how to operate it.

It builds on [zero-downtime upgrades](zero-downtime-upgrades.md), which it requires: everything
which makes the short overlap of two backends safe during a rollout also applies to several
backends in steady state.

## What it guarantees

* **A planned disruption** — a node drain, an eviction, a rolling upgrade — causes no failed
  request.
* **The unplanned loss of a pod** — a crash, an out-of-memory kill, the failure of its node — fails
  the requests in flight on it. The service carries on, queued work is redelivered, and the jobs
  running there are taken over at their next fire or marked failed.

"High availability" does not mean "no failed request, even on a crash": the requests a pod was
serving when it was lost fail, and their clients must retry them.

## How it works

Every replica is active: it serves HTTP requests, runs the background jobs and consumes the queues.
There is no leader and no passive replica, and the backend behaves the same with one replica as
with several. The replicas share their state through the database and the message broker, never in
memory:

* **Background jobs** — Quartz, clustered through the database, runs each job once per period in
  the cluster (see [Jobs](#jobs));
* **Queues** — a message is acknowledged once processed, and delivered again to another replica when
  the one processing it is lost. The GitHub ingestion and the TFC hooks have a single active
  consumer (see [GitHub ingestion](#github-ingestion)). The assisted changes and the renames in the
  search index go through queues, `assisted-change` and `search-rename`, processed one at a time per
  build, or per entity, across the replicas;
* **Settings and properties** — never cached: each replica reads them once per request, or per
  message of a queue, so that a change made through one replica applies to the next request served
  by any other one;
* **Encryption key** — generated once, by one replica, and stored in the key store. When it changes,
  every replica reloads it;
* **Startup** — the startup tasks of the replicas, the configuration as code among them, run one
  replica at a time, even when several start together.

## Requirements

* **The two values of the chart** — a chart supporting several replicas (see the
  [chart README](https://github.com/yontrack/yontrack-chart#high-availability)), Yontrack
  6.0.0-beta.8 or later, and:

    ```yaml
    ontrack:
      zeroDowntimeUpgrades: true
      replicas: 2
    ```

    `ontrack.replicas` sets the number of backend pods, 2 or more for high availability. The chart
    refuses to render more than one replica without `zeroDowntimeUpgrades: true`, which
    `ontrack.replicas` does not switch on by itself: several replicas need the rolling update and the
    data volume per pod of [zero-downtime upgrades](zero-downtime-upgrades.md#requirements), whose
    requirements all apply.

* **A key store other than `file`** — `ontrack.config.key_store` must be `jdbc` (the default of the
  chart) or `secret`: with `file`, each replica would keep a key of its own on its disk, and a
  secret encrypted by one replica could not be decrypted by the others. The chart refuses the
  combination. Outside the chart, a backend using the `file` key store logs a warning at startup
  when it sees another live replica sharing its database.
* **Room for the connections** — each replica counts in the connection budget of the database: with
  the defaults, two replicas need up to 130 connections, three need 160. The
  [chart README](https://github.com/yontrack/yontrack-chart#database-connections) gives the budget,
  and checks it against `postgresql.maxConnections` for the bundled database. See also
  [Database connections](zero-downtime-upgrades.md#database-connections).
* **Kubernetes features** — with several replicas, the chart adds a `PodDisruptionBudget`, which lets
  one backend pod at a time be evicted, and spreads the pods across the nodes
  (`topologySpreadConstraints`, set by `ontrack.topologySpread`). The spread is soft by default:
  the pods can only be spread over several nodes if the cluster has them. The UI gets the same
  when `ontrack.ui.replicas` is above 1.

### What the bundled services still limit

The middleware the chart can deploy still runs as single instances: the bundled Postgres, RabbitMQ,
the Postgres of the local Keycloak, and the bundled MinIO. A drain of their node, or the loss of
their pod, still interrupts Yontrack, whatever its number of replicas.

Where it must not, point Yontrack at external or managed services: a managed database, an external
S3-compatible storage, an external OIDC provider. The
[chart README](https://github.com/yontrack/yontrack-chart#high-availability) says how. RabbitMQ is
always deployed by the chart.

### A single replica

A single replica with `ontrack.zeroDowntimeUpgrades: true` stays supported: upgrades have no
downtime, but a node drain, or the loss of the pod, causes a short outage, until the replacement pod
is ready.

## Operating it

### Jobs

* **One run per period in the cluster** — a periodic job runs once per period, on one of the
  replicas, never on two at the same time. Periodic jobs run at a fixed rate: their runs start one
  period apart, whatever their duration, rather than one period after the end of the previous run.
  A run missed while no replica could run it fires once, as soon as one can, then the job resumes
  its schedule.
* **Where a job runs** — the *Jobs* page shows the jobs of the whole cluster: which ones are running,
  and on which replica (*on &lt;pod name&gt;*), their progress, last run and last error. It is the
  same on every replica.
* **Stop and "run now" from any replica** — stopping a job interrupts it on the replica running it,
  within a few seconds. "Run now" fires the job once in the cluster, on any replica, or says that it
  is already running. A job stopped by its timeout is stopped by the replica running it.
* **"Pause all" is persistent** — pausing all the jobs, or one job, applies to every replica, and
  survives restarts, until the jobs are resumed. The deprecated `ontrack.config.jobs.paused-at-startup`
  property pauses all the jobs, of the whole cluster, when a replica starts with it: use the
  "Pause all" action instead.
* **A lost replica** — when a replica is lost, the run of a job in progress on it is cleared from the
  status of the job after about 20 seconds, once the other replicas see it gone. The job runs again
  at its next fire.
* **Per-replica jobs are not on the *Jobs* page** — a few jobs refresh what is particular to each
  replica: the gauges of the audit trail evidence, the probe of the evidence storage, the gauges of
  the GitHub App rate limits. They run on every replica, outside of Quartz, and have no history,
  no pause, and no "run now". Their failures are counted, per replica, by
  `ontrack_job_replica_error`.

`ontrack.config.jobs.pool-size` (10 by default) is the number of jobs a replica can run at the same
time: a cluster of N replicas can run up to N times that many.

### Load balancing

The Kubernetes `Service` in front of the backends balances the **connections**, not the requests: a
client which keeps its connection open — an HTTP client reusing its connections, a CI tool calling
the API in a loop — sends all its requests to the same pod, until the connection is closed. This is
expected, and the client is moved to another pod when its pod goes away. Do not expect an even
spread of the requests of a single client over the replicas.

### Git clones

Each replica has clones of the Git repositories of its own, on its own data volume: plan for N times
the disk of one replica, set by `ontrack.persistence.size`, and for each replica cloning the
repositories again after a rollout, which loads the Git servers N times.

A replica reads its clone of a repository after fetching it, when its last fetch is older than
`ontrack.config.extension.git.remote.freshness` (one minute by default): the indexation job, which
runs on one replica, does not refresh the clones of the others, and a replica never serves a clone
older than this window.

Resetting the clone of a project, through its Git synchronisation, applies to every replica: each
one clones the repository again on its next use. The status of the synchronisation is the one of
the indexation job of the project, the same on every replica.

### Metrics

Every metric is tagged with `application_instance`, the replica reporting it: its host name, the
name of its pod in Kubernetes (`ontrack` outside of a container).

Two kinds of metrics have to be aggregated differently over the replicas:

* **Gauges read from the database** — every replica reports the same value: aggregate them with
  `max`, not `sum`, which would multiply them by the number of replicas:
    * the job counts: `ontrack_job_count_total`, `ontrack_job_running_total`,
      `ontrack_job_disabled_total`, `ontrack_job_paused_total`, `ontrack_job_error_total`,
      `ontrack_job_timeout_total`, `ontrack_job_invalid_total`;
    * the entity counts: `ontrack_entity_<entity>_total`, for `project`, `branch`, `build`,
      `promotionLevel`, `promotionRun`, `validationStamp`, `validationRun`, `validationRunStatus`,
      `property` and `event`;
    * the pending messages of the queues: `ontrack_extension_queue_pending`;
    * the auto-versioning states: `ontrack_extension_auto_versioning_audit_state`;
    * the audit trail evidence: `ontrack_audit_trail_evidences`, `ontrack_audit_trail_evidence_bytes`;
    * the connector statuses: `ontrack_connector_count`, `ontrack_connector_up`,
      `ontrack_connector_down`;
    * the SCM catalog: `ontrack_extension_scm_catalog_total`, `_entries`, `_linked`, `_unlinked`,
      `_orphan`;
    * the GitHub App rate limits, read by every replica from GitHub:
      `ontrack_extension_github_ratelimit_core_limit`, `_core_remaining`, `_core_used`,
      `_graphql_limit`, `_graphql_remaining`, `_graphql_used`.

    For example:

    ```
    max(ontrack_entity_build_total)
    max by (state) (ontrack_extension_auto_versioning_audit_state)
    ```

* **Timers, counters and the other gauges** — each replica reports what it did itself: aggregate
  them with `sum`. For example, the duration of the jobs, `ontrack_job_duration`, is reported by the
  replica which ran them, and the processing of the queues by the replica which processed the
  messages.

The list of all the metrics is in the [reference](../generated/metrics/index.md).

### GitHub ingestion

The GitHub ingestion and the TFC hooks process the events of a repository, or of a workspace, in
order. Their queues have a single active consumer: the broker delivers their messages to one
replica at a time, and hands over to another one when it goes away. Their processing does not
scale with the number of replicas, but the events stay in order whatever this number.
