---
paths:
  - "buildSrc/**"
  - "compose/**"
---

# Test stack wiring (`buildSrc`, `compose/`)

- A new service in `compose/docker-compose-it.yml` is not wired until its port is added to
  `ItStack.BASE_PORTS` **and** the property pointing at it is added to
  `ItStackInstance.systemProperties`, both in `buildSrc`. Add a case to `ItStackTest` with it.
- The `integrationTest` task passes `spring.datasource.url`, `spring.rabbitmq.port`,
  `spring.elasticsearch.uris` and `ontrack.config.vault.uri` to the tests; the
  `kdslAcceptanceTest` task passes the `ontrack.acceptance.*` properties, and an explicit `-D`
  still wins.
- KDSL acceptance stacks have four slots, not ten, and the `-ldap` and `-oidc` variants share the
  slot of the main one.
- `docs/adr/0012-parallel-integration-test-stacks.md` and
  `docs/adr/0013-parallel-kdsl-acceptance-stacks.md` explain the scheme.
