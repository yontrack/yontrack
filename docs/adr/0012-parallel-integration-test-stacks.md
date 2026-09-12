# Each checkout runs its own integration test stack

`./gradlew integrationTest` brings up `compose/docker-compose-it.yml` --
Postgres, Elasticsearch, RabbitMQ and Vault -- and every one of those services
used to publish a fixed host port under a Compose project called `it`. One
machine therefore had room for exactly one integration test run: a second
agent, in a second worktree, got "port is already allocated" from Compose, or
worse, silently attached to the first agent's containers and wrote into its
database.

The integration test stack is now an *instance* of its checkout, the way
`scripts/dev-stack.sh` already makes the development stack one (see
[ADR 0004](0004-parallel-dev-stack-instances.md)). A checkout is identified by
a slug derived from its directory and allocated a *slot* that offsets every
published port by `slot * 100`, and the Compose project is named after the
slug rather than being the constant `it`. The arithmetic lives in `ItStack` in
`buildSrc` and is covered by `ItStackTest`.

Slot 0 belongs to the main working copy, which therefore keeps the historical
ports -- and so does every CI runner, which is a fresh clone with nothing else
running on it. A linked worktree hashes into slot 1-9. Whichever slot is
chosen, it is probed first and bumped along until its ports are actually free,
which is also what keeps the integration test stack out of the way of a
*development* stack in the same checkout: the two families share their base
ports, so the one that starts second simply moves.

## Consequences

The tests have to be told where their stack is, because the ports they default
to are only right on slot 0. The `integrationTest` task passes
`spring.datasource.url`, `spring.rabbitmq.port`, `spring.elasticsearch.uris`
and `ontrack.config.vault.uri` as system properties, which is the full set of
knobs an integration test uses to reach the middleware. A new service in
`docker-compose-it.yml` needs its port added to `ItStack.BASE_PORTS` *and* the
property that points at it added to `ItStackInstance.systemProperties`;
otherwise the service is published on a per-instance port that nothing reads.

The resolved slot is recorded in `.yontrack-it/instance.env` and reused by the
next build, so a stack that is already up is never moved out from under
itself. That file is also how anything outside Gradle -- `psql`, an IDE run
configuration, an agent poking at the test database -- discovers the ports.

Base ports are chosen so that no two slots ever want the same port: two
services may share a base residue only if their ten-slot ranges do not
overlap. Elasticsearch's transport port (9300) is no longer published for that
reason -- 9300 is also Elasticsearch's HTTP port on slot 1 -- and nothing was
using it.

Running an integration test straight from an IDE, in a linked worktree, needs
the four system properties passing by hand; `.yontrack-it/instance.env`
carries them as ready-made `-D` lines. On the main working copy the defaults
still work, which is the common case.

## Considered options

**Letting Compose assign random host ports** and reading them back through the
plugin's `exposeAsSystemProperties` was rejected because the ports would then
change on every run: nothing outside that one Gradle invocation could find the
database, and a failed run would leave no record of where its stack had been.
Deterministic per-checkout ports are worth more than the guarantee of never
colliding.

**Sharing one middleware stack between checkouts**, with a database per
instance, was rejected for the same reason as in ADR 0004: `SearchIndexer`
index names and queue names are constants with no namespace, so two test runs
sharing one Elasticsearch would write into each other's indices.

**Reusing `scripts/dev-stack.sh`'s shell implementation** of the arithmetic
was rejected because the integration stack is driven by Gradle, which would
have had to shell out at configuration time to learn its own ports. The two
implementations are small, and each is unit-tested where it lives.
