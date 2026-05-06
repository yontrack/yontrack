---
name: add-property-type
description: Add a complete Yontrack property type — Kotlin PropertyType class, GraphQL mutation provider, and all required frontend UI components (Icon, Display, Form, FormPrepare). Use when asked to create a new property type.
user-invocable: true
---

# /add-property-type — Add a Yontrack Property Type

Arguments passed: `$ARGUMENTS`

Parse `$ARGUMENTS` for the property name (PascalCase, e.g. `MyFeature`). If not provided, ask the user for:
1. Property name (PascalCase)
2. Which entity types it applies to (Project, Branch, Build, PromotionLevel, ValidationStamp, PromotionRun, ValidationRun)
3. The data fields it holds
4. Whether it needs a GraphQL mutation

---

## Step 1 — Define the data class

In the target extension package, create the data class:

```kotlin
data class {Name}Property(
    val field1: String,
    // ...
)
```

---

## Step 2 — Implement the PropertyType

Create `{Name}PropertyType.kt`:

```kotlin
@Component
class {Name}PropertyType(
    extensionFeature: {Extension}Feature,
) : AbstractPropertyType<{Name}Property>(extensionFeature) {

    override val name: String = "{Human name}"
    override val description: String = "{Description shown in docs}"
    override val supportedEntityTypes: Set<ProjectEntityType> =
        EnumSet.of(ProjectEntityType.{TYPE})

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): {Name}Property = node.parse()
    override fun fromStorage(node: JsonNode): {Name}Property = node.parse()
    override fun replaceValue(value: {Name}Property, replacementFunction: (String) -> String) = value
    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType({Name}Property::class)
}
```

**Important:** Never rename this class after it is deployed — its fully qualified class name is its persistent storage ID.

---

## Step 3 — Add a GraphQL mutation provider (if needed)

Create `{Name}PropertyMutationProvider.kt`:

```kotlin
@Component
class {Name}PropertyMutationProvider : PropertyMutationProvider<{Name}Property> {
    override val propertyType: KClass<out PropertyType<{Name}Property>> = {Name}PropertyType::class
    override val mutationNameFragment: String = "{Name}"
    override val inputFields: List<GraphQLInputObjectField> = listOf(
        stringInputField({Name}Property::field1),
    )
    override fun readInput(entity: ProjectEntity, input: MutationInput) =
        {Name}Property(field1 = input.getRequiredString({Name}Property::field1))
}
```

This generates `set{Name}Property` and `delete{Name}Property` mutations automatically.

---

## Step 4 — Create frontend UI components

Create the directory:
```
ontrack-web-core/components/framework/properties/
  net.nemerosa.ontrack.extension.{module}.{Name}PropertyType/
```

### `Icon.js` (required)
An icon component representing the property visually.

### `Display.js` (required)
Receives `{property}` prop. Renders the property value in read-only mode.

### `Form.js` (required)
Receives `{prefix, property, entity, form}` props. Renders the Ant Design Form.Item fields for editing.

### `FormPrepare.js` (optional)
Transforms form values before they are sent in the GraphQL mutation. Only needed when the form shape differs from the mutation input shape.

---

## Step 5 — Final checklist

- [ ] Data class defined
- [ ] `{Name}PropertyType.kt` created with correct `supportedEntityTypes` and `canEdit`
- [ ] `{Name}PropertyMutationProvider.kt` created (if mutations needed)
- [ ] Frontend `Icon.js` created
- [ ] Frontend `Display.js` created
- [ ] Frontend `Form.js` created
- [ ] Frontend `FormPrepare.js` created (if needed)
- [ ] Unit test for the property type