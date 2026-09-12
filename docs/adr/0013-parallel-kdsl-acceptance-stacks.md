# Each checkout runs its own KDSL acceptance stack

`./gradlew kdslAcceptanceTest` brings up `compose/docker-compose-kdsl.yml` --
a full Yontrack, the Next.js UI, Postgres, Elasticsearch, RabbitMQ, Keycloak
and InfluxDB -- under a Compose project called `kdsl`, with every host port
fixed. Like the integration test stack before
[ADR 0012](0012-parallel-integration-test-stacks.md), that left room for
exactly one acceptance run per machine: a second agent in a second worktree
either got "port is already allocated" or silently attached to the first
agent's containers.

The acceptance stack is now an *instance* of its checkout. `KdslStack` in
`buildSrc` derives a slug and a slot from the checkout path, offsets every
published port by `slot * 100`, and names each Compose project after the slug.
Slot 0 belongs to the main working copy, so it keeps the historical ports --
and so does every CI runner, a fresh clone with nothing else on it.

The slot arithmetic itself is no longer duplicated: `StackSlots` holds it, and
`ItStack` and `KdslStack` are the two families defined on top of it. ADR 0012
justified a second implementation beside `scripts/dev-stack.sh`; a third would
not have been justifiable.

## Consequences

**The three variants share one slot.** `kdslAcceptanceTest`, `kdslLdap` and
`kdslOidc` are already sequenced so that they are never up at the same time --
`kdslLdapComposeUp` waits for `kdslAcceptanceTestComposeDown`, and
`kdslOidcComposeUp` for `kdslLdapComposeDown` -- so one set of ports serves all
three, and a checkout has one set of acceptance ports to remember rather than
three. They still get three distinct Compose project names, because a project
is what `down` acts on.

**Four slots, not ten.** Yontrack's management port spans 8800-9100 over four
slots and Elasticsearch starts at 9200, so a fifth slot would put one
checkout's management port on another's Elasticsearch. Those bases are the
defaults `ACCProperties` and the compose files carry, so they are not free to
move. It is not a real constraint: an acceptance stack is a Yontrack with a
2 GB heap plus six other containers, and four at once is already past what a
developer machine will carry.

**Three properties, not four.** The `kdslAcceptanceTest` task passes
`ontrack.acceptance.connection.url`, `ontrack.acceptance.connection.mgt.url`
and `ontrack.acceptance.influxdb.url`. `connection.internal.url` is
deliberately *not* passed: it is the URL Yontrack uses to reach itself from
inside its own container, where the host port means nothing and 8080 is always
right. A property given explicitly on the command line still wins, so a run
against an instance started elsewhere keeps working.

**The Keycloak and UI ports are consumed inside the compose files.**
`KC_HOSTNAME` and the JWT issuer URI both have to name the *published*
Keycloak port -- a token is only valid if the issuer the backend expects is
the issuer Keycloak stamps -- and `NEXTAUTH_URL` and `NEXTAUTH_ACCOUNT_URL`
likewise. All four interpolate the same variable as the port publication.

**The UI URL in notification payloads is not affected.** Several acceptance
tests assert on `http://localhost:3000/...` links inside notifications. Those
come from `ontrack.config.ui.url`, which the compose files never set, so they
stay at the backend's default whatever port the UI container is published on.
Setting `ONTRACK_CONFIG_UI_URL` from the slot would break them, and would be
wrong anyway: it is the address a human is told to visit, not one the tests
reach.

**Elasticsearch's transport port (9300) is no longer published**, as in
ADR 0012 and for the same reason: 9300 is Elasticsearch's own HTTP port one
slot up. Nothing was using it.

**A privileged port is probed by connecting, not by binding.** The LDAP
variant publishes 389 and 636, and an unprivileged process cannot bind below
1024 on Linux at all -- so a bind probe reads those as busy and every slot as
taken. That is exactly what a CI runner is, and it turned the first version of
this change red on `main`. Ports below 1024 are therefore probed by opening a
connection instead: something answers, or nothing does. macOS binds privileged
ports happily, which is why the local verification passed.

The resolved slot is recorded in `.yontrack-kdsl/instance.env` and reused by
the next build, so a stack that is already up is never moved out from under
itself. That file is also how anything outside Gradle discovers the ports,
including an agent that wants to open the acceptance instance in a browser.
