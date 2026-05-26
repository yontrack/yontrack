---
name: add-json-schema
description: Add a new downloadable JSON schema to the Resources page — Kotlin provider class only, auto-discovered by Spring. Use when asked to expose a JSON schema for a configuration or data class.
user-invocable: true
---

# /add-json-schema — Add a Yontrack JSON Schema Provider

Arguments passed: `$ARGUMENTS`

Parse `$ARGUMENTS` for:
1. **Schema key** — kebab-case identifier (e.g. `auto-versioning`), used in the download URL
2. **Root Kotlin class** — the data class to generate the schema from (e.g. `AutoVersioningConfig`)
3. **Module / package** — the extension module where the provider class should live (e.g. `ontrack-extension-auto-versioning`, package `net.nemerosa.ontrack.extension.av.config`)

If any of these are missing, ask the user before proceeding.

---

## Step 1 — Create the provider class

In the target module and package, create `{Name}JsonSchemaProvider.kt`:

```kotlin
package {package}

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.AbstractJsonSchemaProvider
import net.nemerosa.ontrack.model.json.schema.JsonSchemaBuilderService
import net.nemerosa.ontrack.model.support.EnvService
import org.springframework.stereotype.Component

@Component
class {Name}JsonSchemaProvider(
    envService: EnvService,
    private val jsonSchemaBuilderService: JsonSchemaBuilderService,
) : AbstractJsonSchemaProvider(envService) {

    override val key: String = "{schema-key}"
    override val title: String = "{Human-readable title}"
    override val description: String = "JSON schema for {description}"

    override fun createJsonSchema(): JsonNode =
        jsonSchemaBuilderService.createSchema(
            ref = "{schema-key}",
            id = id,
            title = title,
            description = description,
            root = {RootClass}::class,
        ).asJson()
}
```

**Reference implementation:** `CIConfigJsonSchemaProvider.kt` in `ontrack-extension-config/src/main/java/net/nemerosa/ontrack/extension/config/schema/`.

---

## Step 2 — Verify root class annotations

Open the root data class and ensure fields have `@APIDescription` annotations so the generated schema includes meaningful descriptions:

```kotlin
import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Description of this configuration.")
data class {RootClass}(
    @APIDescription("What this field represents")
    val field1: String,
    // ...
)
```

Also annotate any nested data classes the same way.

---

## Step 3 — No further wiring needed

Spring auto-discovers all `@Component` classes that extend `AbstractJsonSchemaProvider`. The following are handled automatically:

- **REST download endpoint:** `GET /rest/ref/schema/json/{schema-key}` — served by the existing `JsonSchemaController`
- **GraphQL metadata:** the schema appears in `query { jsonSchemaDefinitions { key title description } }` automatically
- **Resources page:** the frontend `JsonSchemasTable` queries `jsonSchemaDefinitions` and renders a download link for every registered provider — no frontend change required

---

## Final checklist

- [ ] `{Name}JsonSchemaProvider.kt` created with `@Component`, correct `key`, `title`, `description`
- [ ] `createJsonSchema()` delegates to `jsonSchemaBuilderService.createSchema(root = {RootClass}::class)`
- [ ] Root data class (and nested classes) have `@APIDescription` on the class and its fields
- [ ] Schema appears on the Resources page at `/core/ref/resources`