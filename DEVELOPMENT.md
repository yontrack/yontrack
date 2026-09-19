# Development quick start

To start developing with Yontrack, follow these instructions.

## Prerequisites

You need:

* JDK 21
* Docker (Desktop)

## Getting the code

Get a clean working copy of the Yntrack GitHub repository:

```bash
git clone git@github.com:yontrack/yontrack.git yontrack
cd yontrack
```

## Running the development stack

A single command brings up everything Yontrack needs -- the middleware in
Docker, the Spring Boot backend and the Next.js frontend -- and waits until all
three answer:

```bash
scripts/dev-stack.sh up
```

It prints the URLs it allocated. On a fresh checkout it also installs the npm
dependencies, so `git clone` (or `git worktree add`) followed by this one
command is enough to get running.

```bash
scripts/dev-stack.sh down             # stop everything, keep the data
scripts/dev-stack.sh down --clean     # stop everything and drop the volumes
scripts/dev-stack.sh restart backend  # after a Kotlin change
scripts/dev-stack.sh status           # what is up, and where
scripts/dev-stack.sh ls               # every stack running on this machine
scripts/dev-stack.sh logs backend -f  # follow a tier's log
```

`./gradlew devStackUp` and `./gradlew devStackDown` delegate to the same
script for anyone who prefers the Gradle entry point.

### Several stacks at once

Each checkout runs its own stack, so two worktrees can be developed against at
the same time without colliding. The main working copy takes *slot 0* and keeps
the historical ports; each linked worktree hashes into a slot from 1 to 9, which
offsets every port by `slot * 100`:

| Service        | Slot 0 | Slot 1 |
|----------------|--------|--------|
| UI             | 3000   | 3100   |
| Backend        | 8080   | 8180   |
| Management     | 8800   | 8900   |
| Keycloak       | 8008   | 8108   |
| Postgres       | 5432   | 5532   |
| Elasticsearch  | 9200   | 9300   |
| RabbitMQ       | 5672   | 5772   |

The resolved ports are written to `.yontrack-dev/instance.env` in the checkout,
along with the logs of each tier. Set `YONTRACK_DEV_NAME` to name an instance
explicitly instead of deriving the name from the directory.

Two services are off by default and can be opted into with a Compose profile:
Kibana (`--profile kibana`) and InfluxDB (`--profile influxdb`).

> Keycloak has no database: the development realm in
> `compose/keycloak/import/dev/` is re-imported on every start, so changes to it
> take effect immediately, and anything created by hand in the admin console is
> lost on a restart. Log in with `admin`/`admin`.

## Running from IntelliJ IDEA

The run configurations under `.run/` are not in version control, so you will
need to create them yourself:

* a Spring Boot configuration on `net.nemerosa.ontrack.boot.Application` with
  the `dev` profile active, using the same environment variables as
  `ds_backend_up` in `scripts/dev-stack.sh`;
* an NPM configuration running `dev` in the `ontrack-web-core` directory.

The middleware still has to be running -- start it with `scripts/dev-stack.sh
up` and then stop the tiers you would rather run yourself from the IDE:

```bash
scripts/dev-stack.sh up
# ... then take over the backend in the IDE:
kill $(cat .yontrack-dev/backend.pid)
```

> The backend is available on http://localhost:8080 but should not be used
> directly. The Spring Boot actuator runs at http://localhost:8800/manage, and
> the application itself on http://localhost:3000.

## Dependency management

Versions come from BOMs declared as Gradle platforms, not from the
`io.spring.dependency-management` plugin, which was dropped in #1753. The root
build script declares, on every dependency bucket of every source set of every
Java project:

* `org.springframework.boot:spring-boot-dependencies`, the Spring Boot BOM, at
  the version of the `org.springframework.boot` plugin;
* `org.jetbrains.kotlin:kotlin-bom` and
  `org.jetbrains.kotlinx:kotlinx-coroutines-bom`, at the versions this build
  wants. The Spring Boot BOM imports both at older versions; conflict
  resolution keeps the higher ones. They stand in for the
  `extra["kotlin.version"]` and `extra["kotlin-coroutines.version"]` BOM
  property overrides the plugin used to honour -- a Gradle platform reads a
  published POM, whose properties are already substituted, so there is nothing
  to override.

Modules no BOM covers get a plain constraint in the same place, in the
`versionConstraints` list.

The two mechanisms are not equivalent, and the difference matters when reading
a lockfile diff: the plugin *overrode* the version of every module it managed,
wherever it sat in the graph, while a platform only takes part in conflict
resolution -- and, unlike the plugin, it honours the `<exclusions>` a BOM
declares. `platform`, not `enforcedPlatform`: an enforced one would force
`org.jetbrains.kotlin:*` down to the Spring Boot BOM's Kotlin, with no
constraint able to lift it again.

## Dependency locking

Every Gradle configuration of every project is locked, in `STRICT` mode. The
resolved versions are committed in lockfiles:

* `gradle.lockfile` and `buildscript-gradle.lockfile` (the plugin classpath) at
  the root and in every project;
* the same two in `buildSrc`, which is a build of its own.

A dependency whose resolved version no longer matches its lockfile fails the
build, and so does a configuration with no lock state at all: a new module has
to commit its lockfiles along with its build file.

After changing a dependency or a plugin version, or adding a module, rewrite
the lockfiles and commit them with the change:

```bash
./gradlew resolveAndLockAll --write-locks
```

`resolveAndLockAll` is registered in every project and resolves every
resolvable configuration. `./gradlew dependencies --write-locks` is not
enough: it only covers the root project.

That one command also rewrites the `buildSrc` lockfiles, since `buildSrc` is
built with the same `--write-locks` before anything else. Its test classpath is
only resolved by its own task, though, so after changing a `buildSrc`
dependency run this as well:

```bash
./gradlew -p buildSrc resolveAndLockAll --write-locks
```

Dependabot keeps the lockfiles up to date in its Gradle pull requests. Whether
its updates cover the `buildscript` and `buildSrc` lockfiles as well as the
per-module ones is not verified yet (#1752). If a Dependabot pull request
fails the build on a lock state mismatch, check it out and run the two
commands above.

The setup lives in `DependencyLocking` in `buildSrc` and in the `buildscript`
blocks of the root and `buildSrc` build scripts. Configurations that cannot be
locked are listed, with a reason, in `DependencyLocking.EXCLUDED_CONFIGURATIONS`
- none so far.

## Running the integration tests

```bash
./gradlew integrationTest
```

The task brings up `compose/docker-compose-it.yml` -- Postgres,
Elasticsearch, RabbitMQ and Vault -- and tears it down again afterwards.

Like the development stack, that middleware is an *instance* of the checkout,
so two worktrees can run their integration tests at the same time. The main
working copy takes slot 0 and keeps the historical ports; a linked worktree
hashes into a slot from 1 to 9, which offsets every port by `slot * 100`:

| Service        | Slot 0 | Slot 1 |
|----------------|--------|--------|
| Postgres       | 5432   | 5532   |
| Elasticsearch  | 9200   | 9300   |
| RabbitMQ       | 5672   | 5772   |
| RabbitMQ admin | 15672  | 15772  |
| Vault          | 8200   | 8300   |

A slot whose ports are taken is bumped along until a free one is found, which
is also what lets the integration tests run while a development stack is up in
the same checkout. The resolved ports are written to
`.yontrack-it/instance.env`, and the `integrationTest` task passes them to the
tests as system properties, so `./gradlew integrationTest` needs no
configuration of any kind.

Running a single integration test **from the IDE** is the one case that does:
on the main working copy the defaults baked into the tests are right, but in a
linked worktree the four `-D` options at the bottom of
`.yontrack-it/instance.env` have to go into the run configuration.

The arithmetic lives in `ItStack` in `buildSrc`, is covered by `ItStackTest`
(`./gradlew -p buildSrc test`), and is explained in
`docs/adr/0012-parallel-integration-test-stacks.md`.

## Running the KDSL acceptance tests

```bash
./gradlew :ontrack-kdsl-acceptance:kdslAcceptanceTest
```

The task builds the Yontrack and UI images, brings up
`compose/docker-compose-kdsl.yml` -- a full Yontrack plus its UI, Postgres,
Elasticsearch, RabbitMQ, Keycloak and InfluxDB -- and tears it down afterwards.

That stack is an instance of the checkout too. The main working copy takes
slot 0 and keeps the historical ports; a linked worktree hashes into a slot
from 1 to 3, which offsets every port by `slot * 100`:

| Service        | Slot 0 | Slot 1 |
|----------------|--------|--------|
| Yontrack       | 8080   | 8180   |
| Management     | 8800   | 8900   |
| UI             | 3000   | 3100   |
| Keycloak       | 8008   | 8108   |
| InfluxDB       | 8086   | 8186   |
| Postgres       | 5432   | 5532   |
| Elasticsearch  | 9200   | 9300   |
| RabbitMQ       | 5672   | 5772   |
| JaCoCo agent   | 6300   | 6400   |

The JaCoCo agent port is only *published* under `-Pcoverage`, which adds
`compose/docker-compose-coverage.yml` on top of the base file and puts the
agent into the backend container (#1819); it is reserved per slot all the
same, so that an ordinary run and a coverage run in two worktrees cannot
collide. The released image is never touched.

Four slots rather than the integration stack's ten: the management port's
range runs into Elasticsearch's beyond that, and an acceptance stack is heavy
enough that four at once is already more than a laptop will carry.

The resolved ports land in `.yontrack-kdsl/instance.env`, and the task passes
them to the suite as `ontrack.acceptance.*` system properties -- so running
the tests needs no configuration. Passing one of those properties explicitly
still wins, which is how you point the suite at an instance you started
yourself.

The `-ldap` and `-oidc` variants share the same slot: they are sequenced never
to be up at the same time.

A slot is claimed by the `kdslStackSlot` task, which every acceptance task
runs first -- never while Gradle is configuring the build. A `./gradlew` that
is not going to start an acceptance stack therefore probes nothing, and cannot
fail on a slot it was not going to use. `integrationTestComposeUp` is fronted
by `itStackSlot` in the same way.

The arithmetic lives in `KdslStack` in `buildSrc`, is covered by
`KdslStackTest`, and is explained in
`docs/adr/0013-parallel-kdsl-acceptance-stacks.md`.
