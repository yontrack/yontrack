---
name: new-extension
description: Scaffold a new Yontrack extension end-to-end — module, feature descriptor, service, GraphQL, migration, tests, and UI. Use when asked to create a new extension or add a new feature module.
user-invocable: true
---

# /new-extension — Scaffold a New Yontrack Extension

Arguments passed: `$ARGUMENTS`

Parse `$ARGUMENTS` for the extension name (e.g. `my-feature`). If not provided, ask the user for:
1. Extension name (kebab-case, e.g. `my-feature`)
2. Short description (one sentence)
3. Whether it needs a new database table

Then execute the following steps in order, confirming with the user before creating files.

---

## Step 1 — Create the Gradle module

Create `ontrack-extension-{name}/build.gradle.kts`:

```kotlin
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring")
}

description = "{Short description}"

dependencies {
    api(project(":ontrack-extension-api"))
    implementation(project(":ontrack-extension-support"))
    // add other dependencies as needed
    testImplementation(project(":ontrack-it-utils"))
}
```

Register the module in `settings.gradle.kts` by adding:
```
include("ontrack-extension-{name}")
```
Find the existing `include(...)` lines and insert alphabetically.

---

## Step 2 — Define the feature descriptor

Create `src/main/java/net/nemerosa/ontrack/extension/{name}/{Name}ExtensionFeature.kt`:

```kotlin
package net.nemerosa.ontrack.extension.{name}

import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import org.springframework.stereotype.Component

@Component
class {Name}ExtensionFeature : AbstractExtensionFeature(
    id = "{name}",
    name = "{Human Name}",
    description = "{Short description}",
)
```

---

## Step 3 — Implement the core extension class

Create `{Name}Extension.kt` in the same package extending `AbstractExtension` and implementing the relevant extension point interface (e.g. `PropertyType`, `EventListener`, `DecorationExtension`).

---

## Step 4 — Add service interface and implementation

Create `{Name}Service.kt` (interface) and `{Name}ServiceImpl.kt` annotated with `@Service`. Use constructor injection. Add security checks at the top of each method using `securityService`.

---

## Step 5 — Add GraphQL SDL and resolver

Create `src/main/resources/graphql/{name}.graphqls` with the schema definition.

Create `{Name}GraphQLController.kt` annotated with `@Controller` using `@QueryMapping` / `@MutationMapping`.

---

## Step 6 — Add Flyway migration (if new tables needed)

Find the highest existing migration number in `ontrack-database/src/main/resources/db/migration/`.

Create `V{N+1}__{short_description}.sql`. Rules:
- Use `SERIAL PRIMARY KEY NOT NULL` for auto-increment PKs
- Always add `ON DELETE CASCADE` on FK references to entity tables
- Add indexes for FK columns

---

## Step 7 — Write unit tests

Create `src/test/java/net/nemerosa/ontrack/extension/{name}/{Name}Test.kt` using MockK for mocks.

---

## Step 8 — Write integration tests

Create `src/test/java/net/nemerosa/ontrack/extension/{name}/{Name}IT.kt` extending `AbstractDSLTestSupport`.

---

## Step 9 — Add frontend UI components (if property types or pages are involved)

For each property type, create the directory:
`ontrack-web-core/components/framework/properties/net.nemerosa.ontrack.extension.{name}.{PropertyType}/`

Required files: `Icon.js`, `Display.js`, `Form.js`. Optional: `FormPrepare.js`.

For new pages, add a `UserMenuItemExtension` on the backend and register the icon in `UserMenu.js > itemIcons`.

---

## Step 10 — Final checklist

Confirm all of the following exist before finishing:
- [ ] `ontrack-extension-{name}/build.gradle.kts`
- [ ] `settings.gradle.kts` updated
- [ ] `{Name}ExtensionFeature.kt`
- [ ] Core extension class(es)
- [ ] Service interface + `@Service` implementation
- [ ] `.graphqls` SDL + `@Controller` resolver
- [ ] Flyway migration (if needed)
- [ ] Unit tests (`*Test.kt`)
- [ ] Integration tests (`*IT.kt`)
- [ ] Frontend UI components (if needed)