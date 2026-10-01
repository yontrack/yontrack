---
paths:
  - "**/*.kt"
  - "**/*.java"
---

# Backend rules (Kotlin / Java)

## Rules

- **Never** rename a `PropertyType` class after it is deployed — its fully qualified class name
  (FQCN) is its persistent storage ID.
- Every new feature lives in an extension: an `AbstractExtensionFeature` `@Component` plus
  `AbstractExtension` components implementing extension points. Scaffold one with `/new-extension`,
  a property type with `/add-property-type`, a dashboard widget with `/add-widget`, a downloadable
  JSON schema with `/add-json-schema`.
- Services are an interface plus an `*ServiceImpl` with constructor injection; they check
  permissions through `SecurityService` and post `EventFactory` events for cross-cutting concerns.

## Naming

| What                    | Pattern                                 | Example                                 |
|-------------------------|-----------------------------------------|-----------------------------------------|
| Package root            | `net.nemerosa.ontrack.extension.{name}` | `net.nemerosa.ontrack.extension.github` |
| GraphQL type class      | `GQLType*`                              | `GQLTypeProject`                        |
| GraphQL root query      | `GQLRootQuery*`                         | `GQLRootQueryBuilds`                    |
| GraphQL mutations class | `*Mutations`                            | `ProjectMutations`                      |
| Service / impl          | `*Service` / `*ServiceImpl`             | `StructureService`                      |
| Unit / integration test | `*Test.kt` / `*IT.kt`                   | `GitHubIngestionIT.kt`                  |

## Where to look

| Need                                              | Start from                                                                    |
|---------------------------------------------------|-------------------------------------------------------------------------------|
| Extension points                                  | `PropertyType<T>`, `EventListener`, `DecorationExtension`, `EntityInformationExtension`, `SearchDocumentIndexer`, `ProjectEntityUserMenuItemExtension`, `UserMenuItemExtension`, `UserMenuGroupExtension` |
| Search                                            | `SearchDocumentIndexer` — `doc/dev-guide/search-indexer.md`                   |
| A property's GraphQL `set*/delete*Property`       | `PropertyMutationProvider`                                                    |
| Configuration of an external integration          | extend `AbstractConfigurationService<T>`, implement `validate()`              |
| A new event type                                  | `SimpleEventType` with an `eventContext(...)`; listen with `EventListener`    |
| GraphQL schema and resolvers                      | SDL in `src/main/resources/graphql/`, resolvers as Spring GraphQL `@Controller` |
| Security checks                                   | `SecurityService.checkGlobalFunction`, `checkProjectFunction`, `isProjectFunctionGranted`, `asAdmin` |
| Entity authorizations for the UI                  | `AuthorizationContributor`, `GQLInterfaceAuthorizableService`                 |
| Timing and counting                               | `meterRegistry.time` / `meterRegistry.measure` — `doc/dev-guide/backend/recipes/metrics.md` |
| A build by display name or name                   | `BuildDisplayNameService.findBuildByDisplayName` — `recipes/lookup-build-by-name.md` |
| Branches in semantic order                        | `BranchOrderingService.getSemVerBranchOrdering` — `recipes/getting-last-branch.md` |
| Other recipes                                     | `doc/dev-guide/backend/recipes/README.md`                                     |
