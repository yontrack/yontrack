# Zero-downtime upgrades

Deployed with its [Helm chart](https://github.com/yontrack/yontrack-chart), Yontrack can be upgraded
without downtime, within a major version. This page says what is guaranteed, how it works, what it
requires, and what to expect while an upgrade rolls out.

## What is guaranteed

* **Within a major version** — 6.0.x to 6.0.y, or 6.0 to 6.1 — an upgrade completes with no failed
  request and no lost queued work: the previous version serves the requests until the new one is
  ready, and a message the previous version cannot complete before it stops is processed again by
  the new one. Under ArgoCD, the backend is rolled out before the UI; with `helm upgrade`, a new UI
  pod may briefly reach a previous backend pod (see [Requirements](#requirements)).
* **A major upgrade** — 5.5 to 6.0, for example — restarts Yontrack: see
  [Migration to V6](../appendix/migration-to-v6.md).
* **A rollback** to the previous minor version is best effort. It works as long as the
  [contract migrations](#expand-and-contract-migrations) of the upgrade have not run: once they
  have, what the previous version used may be gone. They run right after the rollout, so a rollback
  is only safe while the upgrade is still rolling out.

## How it works

### Rolling update

With `ontrack.zeroDowntimeUpgrades: true`, the chart deploys the backend and the UI as Kubernetes
`Deployment`s, rolled out one new pod at a time: a new pod is started next to the old one, the old
pod keeps serving the requests until the new one is ready, and is then withdrawn from the service
before it stops. A stopping backend completes the requests and the queue messages in hand before it
exits. For a short while, two versions of Yontrack run side by side, on the same database and the
same message broker: everything below makes this overlap safe.

### Expand and contract migrations

Yontrack migrates its database in two phases:

* the **expand** migrations only add to the schema — tables, columns, indexes — in a way the
  previous version keeps working with. They run when the new backend starts, while the previous one
  still serves;
* the **contract** migrations drop or delete what only the previous version used. They would break
  it, and run once it is gone.

With `ontrack.zeroDowntimeUpgrades: true`, the chart sets `ontrack.config.database.contract-migrations`
to `DEFERRED`: the backend runs the expand migrations only, and leaves the contract ones pending.
Once the rollout is done, a post-upgrade hook of the chart, a `Job` named
`<release>-yontrack-chart-contract-migrations`, starts the backend image with
`ontrack.config.database.run-contract-and-exit` set to `true`: it runs the pending contract
migrations, in version order, and exits.

The contract migrations of several releases accumulate when releases are skipped: upgrading from
6.0.1 to 6.0.4 runs the expand migrations of the three releases at startup, then their contract
migrations in the hook.

### One runner per background job

While two backends run side by side, both schedule the same background jobs. Each run of a job
first takes a Postgres advisory lock on the job: one backend runs it, and the other one skips that
run. The connections holding these locks come from a pool of their own, next to the main pool of the
backend: see [Database connections](#database-connections).

### Queue messages acknowledged after processing

The notifications, the workflows and the auto-versioning orders go through RabbitMQ queues. A
message is acknowledged only once it has been processed: when a backend stops with a message in
hand, the broker delivers it again, to the other backend. A stopping backend takes no new message,
and gives the one in hand up to `ontrack.config.rabbitmq.listener-shutdown-timeout` (30 seconds by
default) to complete.

The GitHub ingestion and the TFC hooks process the events of a repository, or of a workspace, in
order. Their queues have a single active consumer: the broker delivers their messages to one
backend at a time, and hands over to the other one when it goes away, so the events stay in order
whatever the number of backends.

### Caches during the overlap

Each backend caches the settings and the properties of the entities in memory. A change made
through one backend is seen by the other one once its cache expires: after one minute for the
settings, five minutes for the properties.

The search documents are rebuilt at startup when a new version changes their format. This rebuild
waits for `ontrack.config.search.index.startup-delay` (two minutes by default), past the time when
the previous backend still writes documents in their previous format, and only one of the backends
started side by side runs it. See [Search index](search-index.md).

## Requirements

The [chart README](https://github.com/yontrack/yontrack-chart#zero-downtime-upgrades) has the
details. In short:

* **Chart 6.0.0-beta.0 or later**, with:

    ```yaml
    ontrack:
      zeroDowntimeUpgrades: true
    ```

    This is the one value to change. The default, `false`, restarts the backend on every upgrade,
    as earlier charts do.

* **No encryption keys on the data volume** — `ontrack.config.key_store` must be `jdbc` (the
  default of the chart) or `secret`, never `file`: the chart refuses to render with `file`.
* **Generic ephemeral volumes** — each backend pod gets its own data volume, created and deleted
  with it, which only holds clones of the Git repositories. This needs Kubernetes 1.23 or later
  (OpenShift 4.11 or later). Without them, set `ontrack.persistence.ephemeral.enabled: false` to use
  an `emptyDir` limited to `ontrack.persistence.size` instead.
* **`helm upgrade --wait`**, outside ArgoCD, so that Helm runs the contract migrations hook once the
  rollout is done. Without `--wait`, the hook runs as soon as the manifests are applied, while the
  previous version may still be running, and breaks it. Under ArgoCD, there is nothing to do: the
  hook is a `PostSync` hook, and sync waves roll the backend out before the UI. With `helm upgrade`,
  the backend and the UI roll out together: an old UI calling a new backend is supported, but a new
  UI pod may briefly reach an old backend pod.

    ```bash
    helm upgrade --install yontrack oci://registry-1.docker.io/yontrack/yontrack-chart --wait --timeout 15m ...
    ```

* **Do not use `--atomic`** (`--rollback-on-failure`): see
  [A failed hook](#a-failed-hook).

### Database connections

During a rollout, two backends are connected to the database. Each one has its main connection
pool, plus a pool for the locks of the background jobs, sized on `ontrack.config.jobs.pool-size`,
whose connections are only opened while jobs run. Check that the `max_connections` of Postgres
leaves room for the pools of both backends, next to its other clients.

### Switching an existing installation

Switching to chart 6.x replaces the `StatefulSet` of the backend by a `Deployment`: Yontrack restarts
once, whatever the value of `ontrack.zeroDowntimeUpgrades`. With `ontrack.zeroDowntimeUpgrades: true`,
the former data volume, the `<release>-yontrack-chart` PVC, is no longer used, and is left in place
for you to delete:

```bash
kubectl delete pvc <release>-yontrack-chart
```

## What to expect during a rollout

* **Users with a page open keep the old UI** in their browser until they reload it. An old UI
  working with a new backend is supported.
* **The Git repositories are cloned again** on first use after the rollout, since each new backend
  pod starts with an empty data volume: expect some extra load on the Git servers.
* **A notification can be sent twice** if a backend stops between sending it and recording that it
  was sent: the message is delivered again, and processed again.
* **The settings may lag by up to one minute** on the other backend, after a change: see
  [Caches during the overlap](#caches-during-the-overlap).
* **The search may be incomplete for a while** after an upgrade that changes the format of the
  search documents, until their rebuild, which starts two minutes after the new backend.

## Operating the contract phase

### What ran

The contract migrations which ran are recorded in the `schema_version_contract` table, as the
expand ones are in `schema_version`:

```sql
select version, description, installed_on, success
from schema_version_contract
order by installed_rank;
```

A contract migration of a release which is not in this table is still pending: the backend logs the
pending contract migrations at startup.

### A failed hook

When a contract migration fails, it is rolled back and stays pending. Yontrack keeps running, on the
expanded schema, which the new version supports: the application is up. `helm upgrade` reports the
failure and marks the release as failed. The logs of the `Job` are kept until the next upgrade:

```bash
kubectl logs job/<release>-yontrack-chart-contract-migrations
```

Do not roll back to the previous release: the contract migrations which ran before the failing one
have already dropped what it uses.

### Running it again

Fix the cause of the failure — read in the logs of the `Job` — then run the upgrade again, with the
same chart version and values, or sync again under ArgoCD: the hook runs the pending contract
migrations again. Otherwise, the next upgrade runs them.

### Docker Compose and single-node installations

Outside the chart, `ontrack.config.database.contract-migrations` keeps its default, `STARTUP`: the
backend runs the expand migrations, then the contract ones, at startup. There is no hook to run,
and nothing to do: an upgrade restarts Yontrack, as it always did.

## Limits

* **Not high availability** — the backend runs one replica in steady state. A second one only runs
  during a rollout. The loss of the node running the backend is an outage, until Kubernetes starts
  the backend again.
* **Docker Compose restarts** — an upgrade with Docker Compose stops the backend and starts the
  new one.
* **Major upgrades restart** — an upgrade from one major version to the next, 5.5 to 6.0 for
  example, restarts Yontrack, with all its migrations at startup.
