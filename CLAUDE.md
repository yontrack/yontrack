# Yontrack (Ontrack) Developer Guide for Claude Code

Yontrack (formerly Ontrack) is a **continuous delivery monitoring platform**. It tracks projects,
branches, builds, promotions, and validations across the software delivery lifecycle.

## Tech Stack

- **Backend:** Kotlin + Java, Spring Boot 3.5+, Spring GraphQL, Spring Security
- **Frontend:** React, Next.js 13, Ant Design v5, graphql-request
- **Database:** PostgreSQL with Flyway migrations
- **Search:** Elasticsearch
- **Messaging:** RabbitMQ
- **Auth:** OIDC/JWT
- **Build:** Gradle (Kotlin DSL), JDK 21 required

---

## Rules

These rules apply unconditionally. Follow them in every change, without exception.

### Database
- **Never** modify existing Flyway migration files — always add a new one
- **Always** add `ON DELETE CASCADE` on FK references to entity tables
- Use `SERIAL PRIMARY KEY NOT NULL` for auto-increment primary keys

### Frontend
- **Never** access `localStorage` directly — always use the wrapper functions in `@components/storage/local`

### Property Types
- **Never** rename a `PropertyType` class after it is deployed — its fully qualified class name (FQCN) is its persistent storage ID

### Workflow
- When fixing a GitHub issue: **always** create a branch named `claude/<short-description>-pipeline` before making any changes (use `/fix-issue` skill)
- **Never** create a pull request unless explicitly asked

### Development Process (TDD)

Follow this order for every non-trivial change:

1. **Write tests first**, then implement:
   - Prefer unit tests (`*Test.kt`) for pure logic
   - Use integration tests (`*IT.kt`) for database/service interactions
   - Use KDSL acceptance tests (`ontrack-kdsl-acceptance/`) for API-level scenarios
   - Use UI tests (`ontrack-web-tests/`) for frontend flows

2. **Implement** the feature/fix to make the tests pass

3. **Run the tests** to verify:

   | Scope               | Gradle task              |
   |---------------------|--------------------------|
   | Unit tests          | `./gradlew test`         |
   | Integration tests   | `./gradlew integrationTest` |
   | KDSL API tests      | `./gradlew kdslAcceptanceTest` |
   | UI tests            | `./gradlew uiTest`       |

### Tests
- **Always** use `Roles.*` constants for role names in tests — never string literals

---

## Build & Run

```bash
# Start dev infrastructure (Postgres, Elasticsearch, RabbitMQ, Keycloak)
./gradlew devComposeUp

# Stop dev infrastructure
./gradlew devComposeDown

# Run all unit tests
./gradlew test

# Run integration tests (requires Docker)
./gradlew integrationTest

# Full build
./gradlew build

# Start backend (after devComposeUp) - use IntelliJ run config "Application (kdsl)"
# Backend runs on http://localhost:8080

# Start frontend - from ontrack-web-core/
npm run dev
# Frontend runs on http://localhost:3000
```

---

## Module Architecture

| Module                                           | Role                                                    |
|--------------------------------------------------|---------------------------------------------------------|
| `ontrack-model`                                  | Domain model, interfaces, events                        |
| `ontrack-repository` / `ontrack-repository-impl` | Database access layer                                   |
| `ontrack-service`                                | Business logic implementations                          |
| `ontrack-ui`                                     | REST controllers, Spring Boot app entry point           |
| `ontrack-ui-graphql`                             | GraphQL schema wiring                                   |
| `ontrack-extension-api`                          | Extension interfaces and base classes                   |
| `ontrack-extension-support`                      | Shared extension utilities                              |
| `ontrack-extension-general`                      | Core built-in features (labels, release property, etc.) |
| `ontrack-extension-{name}`                       | Each external integration (github, jenkins, jira, etc.) |
| `ontrack-database`                               | Flyway migrations                                       |
| `ontrack-web-core`                               | React/Next.js frontend                                  |
| `ontrack-test-utils`                             | Shared test fixtures                                    |
| `ontrack-it-utils`                               | Integration test infrastructure                         |

---

## Backend Development Patterns

### Naming Conventions

| What                    | Pattern                                 | Example                                 |
|-------------------------|-----------------------------------------|-----------------------------------------|
| Package root            | `net.nemerosa.ontrack.extension.{name}` | `net.nemerosa.ontrack.extension.github` |
| GraphQL type class      | `GQLType*`                              | `GQLTypeProject`                        |
| GraphQL root query      | `GQLRootQuery*`                         | `GQLRootQueryBuilds`                    |
| GraphQL mutations class | `*Mutations`                            | `ProjectMutations`                      |
| Service interface       | `*Service`                              | `StructureService`                      |
| Service implementation  | `*ServiceImpl`                          | `StructureServiceImpl`                  |
| Unit test               | `*Test.kt`                              | `ReleasePropertyTest.kt`                |
| Integration test        | `*IT.kt`                                | `GitHubIngestionIT.kt`                  |

### Extension System

Every new feature lives in an extension. Extensions are Spring `@Component`s registered automatically.

**1. Define the feature descriptor:**
```kotlin
@Component
class MyExtensionFeature : AbstractExtensionFeature(
    id = "my-feature",
    name = "My Feature",
    description = "What it does",
)
```

**2. Implement the extension:**
```kotlin
@Component
class MyExtension(
    extensionFeature: MyExtensionFeature,
    private val someService: SomeService,
) : AbstractExtension(extensionFeature), SomeExtensionPoint {
    // ...
}
```

Key extension points:
- `PropertyType<T>` — attach custom data to project entities
- `EventListener` — react to domain events
- `DecorationExtension` — add visual decorations to entities
- `EntityInformationExtension` — add info panels in the UI
- `SearchIndexer` — add custom search capabilities
- `ProjectEntityUserMenuItemExtension` — add items to entity action menus
- `UserMenuItemExtension` — add items to the global user menu
- `UserMenuGroupExtension` — add groups to the user menu

### Property Types

Properties attach typed data to project entities (project, branch, build, etc.).

```kotlin
@Component
class MyPropertyType(
    extensionFeature: MyExtensionFeature,
) : AbstractPropertyType<MyProperty>(extensionFeature) {

    override val name: String = "My Property"
    override val description: String = "Description shown in docs"
    override val supportedEntityTypes: Set<ProjectEntityType> =
        EnumSet.of(ProjectEntityType.BUILD, ProjectEntityType.BRANCH)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectEdit::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): MyProperty = node.parse()
    override fun fromStorage(node: JsonNode): MyProperty = node.parse()
    override fun replaceValue(value: MyProperty, replacementFunction: (String) -> String) = value
    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(MyProperty::class)

    // Optional lifecycle hooks:
    override fun onPropertyChanged(entity: ProjectEntity, value: MyProperty) { /* ... */ }
    override fun onPropertyDeleted(entity: ProjectEntity, oldValue: MyProperty) { /* ... */ }
}

data class MyProperty(val name: String)
```

**Adding a GraphQL mutation for a property:**
```kotlin
@Component
class MyPropertyMutationProvider : PropertyMutationProvider<MyProperty> {
    override val propertyType: KClass<out PropertyType<MyProperty>> = MyPropertyType::class
    override val mutationNameFragment: String = "My"  // generates setMyProperty / deleteMyProperty
    override val inputFields: List<GraphQLInputObjectField> = listOf(
        stringInputField(MyProperty::name),
    )
    override fun readInput(entity: ProjectEntity, input: MutationInput) =
        MyProperty(name = input.getRequiredString(MyProperty::name))
}
```

**Frontend components for a property** go in:
`ontrack-web-core/components/framework/properties/net.nemerosa.ontrack.extension.{module}.{MyPropertyType}/`
- `Icon.js` — icon component (required)
- `Display.js` — display component receiving `{property}` prop (required)
- `Form.js` — edit form receiving `{prefix, property, entity, form}` props (required)
- `FormPrepare.js` — prepare values before GraphQL call (optional)

### Service Layer

Use constructor injection. Services implement an interface.

```kotlin
interface MyService {
    fun doSomething(input: MyInput): MyResult
}

@Service
class MyServiceImpl(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val eventPostService: EventPostService,
) : MyService {
    override fun doSomething(input: MyInput): MyResult {
        securityService.checkGlobalFunction(GlobalSettings::class.java)  // security check
        // ... business logic
        eventPostService.post(eventFactory.someEvent(...))  // post events for cross-cutting concerns
        return result
    }
}
```

**Configuration services** (for external integrations) extend `AbstractConfigurationService<T>`:
```kotlin
@Service
class MyConfigServiceImpl(
    configurationRepository: ConfigurationRepository,
    securityService: SecurityService,
    encryptionService: EncryptionService,
    eventPostService: EventPostService,
    eventFactory: EventFactory,
    ontrackConfigProperties: OntrackConfigProperties,
    private val myClient: MyClient,
) : AbstractConfigurationService<MyConfiguration>(
    MyConfiguration::class.java,
    configurationRepository, securityService, encryptionService,
    eventPostService, eventFactory, ontrackConfigProperties
), MyConfigService {
    override val type: String = "my-integration"
    override fun validate(configuration: MyConfiguration): ConnectionResult =
        try { myClient.ping(); ConnectionResult.ok() }
        catch (e: Exception) { ConnectionResult.error(e) }
}
```

### Event System

**Define event types** in a companion object or singleton:
```kotlin
val MY_EVENT: EventType = SimpleEventType(
    id = "my_event",
    template = "Something happened on \${build} in \${project}.",
    description = "When something specific occurs.",
    context = eventContext(
        eventBuild("The build"),
        eventProject("The project"),
    ),
)
```

**Post events:**
```kotlin
eventPostService.post(
    eventFactory.myEvent(build = build)
)
```

**Listen to events:**
```kotlin
@Component
class MyEventListener(
    private val myService: MyService,
) : EventListener {
    override fun onEvent(event: Event) {
        if (event.eventType == EventFactory.NEW_BUILD) {
            val build = event.getEntity<Build>(ProjectEntityType.BUILD)
            myService.handleNewBuild(build)
        }
    }
}
```

### GraphQL

**Schema** — add SDL files in `src/main/resources/graphql/`:
```graphql
type MyType {
    id: ID!
    name: String!
}

extend type Query {
    myQuery(name: String!): MyType
}

extend type Mutation {
    doMyThing(input: DoMyThingInput!): DoMyThingPayload!
}

input DoMyThingInput {
    name: String!
}

type DoMyThingPayload {
    errors: [UserError!]!
}
```

**Resolver** — Spring GraphQL `@Controller`:
```kotlin
@Controller
class MyGraphQLController(private val myService: MyService) {

    @QueryMapping
    fun myQuery(@Argument name: String): MyType? =
        myService.findByName(name)

    @MutationMapping
    fun doMyThing(@Argument input: DoMyThingInput): DoMyThingPayload {
        myService.doThing(input)
        return DoMyThingPayload(errors = emptyList())
    }
}
```

### Database Migrations

Use **Flyway** SQL files in `ontrack-database/src/main/resources/db/migration/`.

Filename pattern: `V{N}__short_description.sql` where `N` is the next sequential number.

```sql
-- V75__my_new_feature.sql
CREATE TABLE MY_TABLE (
    ID          SERIAL PRIMARY KEY NOT NULL,
    NAME        VARCHAR(100)       NOT NULL,
    ENTITY_ID   INTEGER            NOT NULL REFERENCES ENTITIES (ID) ON DELETE CASCADE
);
CREATE INDEX MY_TABLE_ENTITY_IDX ON MY_TABLE (ENTITY_ID);
```

### Security

```kotlin
// Check global function (admin-level)
securityService.checkGlobalFunction(GlobalSettings::class.java)

// Check project-level function
securityService.checkProjectFunction(entity, ProjectEdit::class.java)

// Check without throwing
if (securityService.isProjectFunctionGranted(entity, PromotionRunCreate::class.java)) { }

// Run as admin
securityService.asAdmin {
    // ... privileged operation
}
```

### Metrics

```kotlin
// Measure execution time
val result = meterRegistry.time(
    "metric.name",
    "tag1" to "value1",
) { /* code returning result */ }

// Measure time + count successes/errors
val result = meterRegistry.measure(
    started = MyMetrics.STARTED,
    success = MyMetrics.SUCCESS,
    error = MyMetrics.ERROR,
    time = MyMetrics.TIME,
    tags = mapOf("tag" to "value")
) { /* code */ }
```

### Coroutines

Primarily used in job/async contexts:
```kotlin
runBlocking {
    val jobs = items.map { item ->
        launch { processItem(item) }
    }
    withTimeout(TimeUnit.HOURS.toMillis(1)) {
        jobs.joinAll()
    }
}

// Convert CompletableFuture to coroutine
val result = myFuture.await()
```

### Utility Recipes

**Look up a build by display name or name:**
```kotlin
val build: Build? = buildDisplayNameService.findBuildByDisplayName(project, name, onlyDisplayName = false)
```

**Order branches semantically:**
```kotlin
val ordering = branchOrderingService.getSemVerBranchOrdering(branchNamePolicy = BranchNamePolicy.NAME_ONLY)
val orderedBranches = branches.sortedWith(ordering)
```

---

## Frontend Development Patterns

### GraphQL Calls

Always use the `useGraphQLClient` hook:

```javascript
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider"

// In a component:
const client = useGraphQLClient()

// In useEffect:
useEffect(() => {
    if (client) {
        client.request(query, variables).then(data => { /* ... */ })
    }
}, [client, dep1, dep2])

// In an async handler (mutation):
const onAction = async () => {
    const data = await client.request(gql`mutation { ... }`)
    if (processGraphQLErrors(data, 'mutationName')) {
        // success
    }
}
```

### Permissions

**Entity-level permissions:**
```javascript
import {isAuthorized} from "@components/common/authorizations"

// After fetching entity with authorizations { name action authorized }:
if (isAuthorized(build, 'build', 'promote')) { /* ... */ }
```

**Global permissions:**
```javascript
const user = useContext(UserContext)
if (user.authorizations.project?.create) { /* ... */ }
```

On the server side, implement `AuthorizationContributor` and register it via `GQLInterfaceAuthorizableService`.

### Form Dialogs

```javascript
// MyDialog.js
export const useMyDialog = ({onSuccess}) => {
    return useFormDialog({
        init: (form, context) => {
            form.setFieldValue('name', context.name)
        },
        prepareValues: (values) => ({ ...values }),
        query: gql`mutation DoThing($name: String!) {
            doThing(input: {name: $name}) { errors { message } }
        }`,
        userNode: 'doThing',
    })
}

export default function MyDialog({myDialog}) {
    return (
        <FormDialog dialog={myDialog}>
            {/* Form.Item fields */}
        </FormDialog>
    )
}

// Client component:
const myDialog = useMyDialog({onSuccess: () => reload()})
// ...
<MyDialog myDialog={myDialog}/>
// ...
myDialog.start({name: 'initial value'})  // open the dialog
```

### User Preferences (server-side persistent)

```javascript
const preferences = usePreferences()
const value = preferences.myField

preferences.setPreferences({ myField: newValue })
```

### Local Preferences (browser localStorage)

Use wrapper functions in `@components/storage/local`. Add a dedicated `get`/`set` function pair for each new preference token.

### Page Events (cross-component communication)

```javascript
// Fire an event:
const eventsContext = useContext(EventsContext)
eventsContext.fireEvent("my.event.name", { id: 123 })

// Subscribe to an event (trigger refresh):
const refreshCount = useEventForRefresh("my.event.name")
useEffect(() => { /* reload data */ }, [client, refreshCount])

// Subscribe with values:
eventsContext.subscribeToEvent("my.event.name", (values) => { /* ... */ })
```

### Reference Data

```javascript
const refData = useRefData()
const statuses = refData.validationRunStatuses
```

### User Menu Items (global menu)

```kotlin
@Component
class MyUserMenuItemExtension(
    extensionFeature: MyExtensionFeature,
) : UserMenuItemExtension, AbstractExtension(extensionFeature) {
    override fun getItems(): List<UserMenuItem> = listOf(
        UserMenuItem(
            groupId = CoreUserMenuGroups.CONFIGURATIONS,
            extension = extensionFeature,
            id = "my-page",
            name = "My Page",
        )
    )
}
```
This generates the path `/extension/{featureId}/my-page`. Add its icon in `UserMenu.js > itemIcons`.

### Page Tools (entity action menus)

Implement `ProjectEntityUserMenuItemExtension` on the server side.
Use the `userMenuActions` GraphQL field with `gqlUserMenuActionFragment` on the frontend.

### Auto-Versioning Post-Processing UI

Each `PostProcessing<T>` backend extension needs a matching frontend `Display.js`:

- **Path:** `ontrack-web-core/components/framework/auto-versioning-post-processing/{id}/Display.js`
  where `{id}` is the value of `PostProcessing.id` (e.g. `github`, `jenkins`)
- **Props:** all fields of the config data class spread individually (the full config JSON is spread as props)
- **Only `Display.js` is required** — no Icon.js, Form.js, or FormPrepare.js
- Use an Ant Design `Descriptions` table for read-only display; include a `<Typography.Text code>{id}</Typography.Text>` type header

```javascript
export default function Display({
    dockerImage, dockerCommand, commitMessage,
    mySpecificField,
    parameters = [],
}) {
    const items = [
        { key: 'mySpecificField', label: "My field", children: <Typography.Text code>{mySpecificField}</Typography.Text>, span: 12 },
        // ... one item per config field
    ]
    return (
        <Space direction="vertical">
            <Typography.Text code>my-id</Typography.Text>
            <Descriptions items={items} span={12}/>
        </Space>
    )
}
```

---

## Testing Patterns

### Unit Tests (`*Test.kt`)

```kotlin
class MyServiceTest {
    private val myRepo = mockk<MyRepository>()
    private val service = MyServiceImpl(myRepo)

    @Test
    fun `something works`() {
        every { myRepo.find(any()) } returns listOf(...)
        val result = service.doSomething(input)
        assertEquals(expected, result)
        verify(exactly = 1) { myRepo.find(any()) }
    }
}
```

Run with: `./gradlew test`

### Integration Tests (`*IT.kt`)

Integration tests extend a base IT class and run against real infrastructure (Postgres, etc.) started
via Docker Compose.

```kotlin
@SpringBootTest
class MyFeatureIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var myService: MyService

    @Test
    fun `end-to-end flow works`() {
        val project = doCreateProject()
        val branch = doCreateBranch(project)
        // ... full flow using real DB
    }
}
```

Run with: `./gradlew integrationTest` (starts/stops Docker Compose automatically)

### Mocking REST Template Clients

```kotlin
@Test
fun `client returns correct data`() {
    val restTemplate = RestTemplate()
    val server = MockRestServiceServer.bindTo(restTemplate).build()
    val client = MyClientImpl(restTemplate)

    server.expect(once(), requestTo("/api/endpoint"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("""{"key": "value"}""", MediaType.APPLICATION_JSON))

    val result = client.getData()
    assertEquals("value", result.key)
    server.verify()
}
```

For complex integration test scenarios, use `MockRestTemplateProvider` (see `JiraLinkNotificationChannelIT`).

### Testing Authorizations

Use `asGlobalRole` with a `Roles` constant to test behavior under a specific global role:

```kotlin
import net.nemerosa.ontrack.model.security.Roles

asGlobalRole(Roles.GLOBAL_AUTOMATION) {
    // code runs with AUTOMATION role permissions
}
```

Role constants are in `net.nemerosa.ontrack.model.security.Roles` (e.g. `GLOBAL_AUTOMATION`, `GLOBAL_CREATOR`, `GLOBAL_CONTROLLER`).

Note: `ProjectEdit extends ProjectConfig` — so checking `ProjectConfig` in `canEdit()` covers both project owners (who have `ProjectEdit`) and automation users (who have `ProjectConfig` directly).

---

## Key File Locations

| What                       | Where                                                                         |
|----------------------------|-------------------------------------------------------------------------------|
| Domain model entities      | `ontrack-model/src/main/java/net/nemerosa/ontrack/model/structure/`           |
| Event types & factory      | `ontrack-model/src/main/java/net/nemerosa/ontrack/model/events/`              |
| Extension base classes     | `ontrack-extension-api/src/main/java/net/nemerosa/ontrack/extension/support/` |
| Service implementations    | `ontrack-service/src/main/java/net/nemerosa/ontrack/service/`                 |
| REST controllers           | `ontrack-ui/src/main/java/net/nemerosa/ontrack/boot/ui/`                      |
| Flyway migrations          | `ontrack-database/src/main/resources/db/migration/`                           |
| Spring Boot config         | `ontrack-ui/src/main/resources/config/application.yml`                        |
| GraphQL schema (generated) | `ontrack-web-core/ontrack.graphql`                                            |
| Frontend GraphQL hook      | `ontrack-web-core/components/providers/ConnectionContextProvider.js`          |
| Frontend event bus         | `ontrack-web-core/components/common/EventsContext.js`                         |
| Frontend local storage     | `ontrack-web-core/components/storage/local.js`                                |
| Frontend ref data          | `ontrack-web-core/components/providers/RefDataProvider.js`                    |
| Property UI components     | `ontrack-web-core/components/framework/properties/{fqcn}/`                    |
| Post-processing UI         | `ontrack-web-core/components/framework/auto-versioning-post-processing/{id}/` |
| Dev guide docs             | `doc/dev-guide/`                                                              |

---

## Skills

Use these skills for common multi-step workflows:

- `/new-extension` — scaffold a new extension end-to-end (module, feature, service, GraphQL, migration, tests, UI)
- `/add-property-type` — add a complete property type (Kotlin class, mutation provider, frontend UI components)
- `/fix-issue` — pick a GitHub issue, create the correctly-named branch, implement the fix
