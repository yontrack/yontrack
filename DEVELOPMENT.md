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
