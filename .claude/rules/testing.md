---
paths:
  - "**/src/test/**"
  - "**/src/testFixtures/**"
  - "**/*Test.kt"
  - "**/*IT.kt"
  - "ontrack-kdsl-acceptance/**"
---

# Backend test rules

- **Always** use `Roles.*` constants (`net.nemerosa.ontrack.model.security.Roles`) for role names
  in tests — never string literals. Run code under a role with `asGlobalRole(Roles.GLOBAL_AUTOMATION) { ... }`.
- `ProjectEdit extends ProjectConfig` — so checking `ProjectConfig` in `canEdit()` covers both
  project owners (who have `ProjectEdit`) and automation users (who have `ProjectConfig` directly).
- Unit tests (`*Test.kt`) mock collaborators with `mockk`.
- Integration tests (`*IT.kt`) extend `AbstractDSLTestSupport` and run against real middleware,
  which `./gradlew integrationTest` brings up and tears down itself.
- A `RestTemplate` client is tested with `MockRestServiceServer` —
  `doc/dev-guide/backend/recipes/testing/mock-rest-template-clients.md`. For complex integration
  scenarios use `MockRestTemplateProvider` (see `JiraLinkNotificationChannelIT`).
