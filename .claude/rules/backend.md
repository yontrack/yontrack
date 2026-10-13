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
- Several backend replicas run at once, always: **never** cache database data across requests
  (`@Cacheable`, a singleton map), or queue work in memory. A job comes from a `JobProvider` or a
  `JobOrchestratorSupplier` only - `JobScheduler` has no `schedule` (#2077): a job to run on demand
  is supplied with `Schedule.NONE` and fired with `fireImmediately`. A job refreshing what is per
  pod by nature (a gauge of this pod, the reachability of a store from it) is registered with
  `JobScope.REPLICA`, runs on every replica and is listed in `ReplicaJobsIT`; a result the same for
  the cluster is stored in the database by a cluster job (#2078). Whether a job runs, where, its
  last run and last error: `JobScheduler.getJobStatus(key)`, the same on every replica (#2079) -
  never a flag of your own. Read ADR 0021
  (`docs/adr/0021-backend-replicas-share-no-state-in-memory.md`) before adding any state that
  outlives a request.

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
