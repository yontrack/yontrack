# Each checkout runs its own complete development stack

Several agents work on Yontrack at once, each in its own worktree, and each
needs a running Yontrack to iterate against — particularly on the frontend.
`scripts/dev-stack.sh` therefore treats a checkout as an *instance*: the main
working copy takes slot 0 and keeps the ports everyone already has in their
bookmarks, and each linked worktree hashes into a slot from 1 to 9 that offsets
every published port by `slot * 100`. Nothing is shared between instances: each
gets its own Compose project, its own Postgres, Elasticsearch, RabbitMQ and
Keycloak, its own backend and its own frontend.

The stack is driven by a shell script rather than by Gradle tasks, and the
Gradle tasks delegate to it. Tearing a stack down has to work when the build
does not — a Kotlin file that does not compile, or a broken `build.gradle.kts`,
is exactly the situation in which an agent most needs to kill its stack — and
Gradle is a task runner rather than a process supervisor: `bootRun` blocks and
owns the build, so supervising detached, separately-killable tiers would mean
reimplementing pidfiles and process groups inside the daemon that is itself one
of the things to kill.

## Considered options

**Sharing the middleware between instances** — one Postgres, Elasticsearch and
RabbitMQ, with per-instance databases and name prefixes — would have cost far
less memory, and was rejected because Yontrack cannot currently be configured
into a namespace. `SearchIndexer.indexName` is a constant on each indexer with
no global prefix property, and queue names are likewise fixed, so two backends
sharing one Elasticsearch would silently write into each other's indices. The
option becomes available if a prefix property is ever introduced.

**A single shared Keycloak** was rejected despite holding no per-instance
state, because it would live outside every instance's lifecycle: something has
to start it and nothing owns stopping it. One Compose project per instance
keeps `down` total.

**Keeping Keycloak's Postgres** was rejected in favour of an ephemeral
Keycloak. Keycloak only imports a realm into an empty database, so a persisted
one means edits to the committed development realm silently never take effect.
Re-importing on every start makes the realm authoritative, at the cost of
losing users created by hand in the admin console.

## Consequences

The development realm's client now accepts `http://localhost:*` redirect URIs,
because the UI's port varies per instance. This is acceptable only because the
realm is for local development; it must never be the model for a deployed one.

Elasticsearch is given a named volume, so that `down` without `--clean` leaves
the search index consistent with the database it preserves. Without it the
index would be destroyed while the database survived.

Two or three instances is the practical ceiling on a developer machine — each
one pays for its own JVM, Node process and four containers. Kibana and InfluxDB
are behind Compose profiles rather than started by default for that reason.

The slot and port arithmetic lives in shell functions covered by
`scripts/dev-stack-test.sh`; the orchestration around them is verified by hand,
by bringing up two instances at once.
