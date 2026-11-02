package net.nemerosa.ontrack.extension.config.schema

import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.JsonBooleanType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyAlias
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.PropertyType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PropertiesJsonSchemaTypeProviderTest {

    private fun propertyType(name: String): PropertyType<*> = mockk {
        every { typeName } returns name
        every { supportedEntityTypes } returns ProjectEntityType.entries.toSet()
        every { createConfigJsonType(any()) } returns JsonBooleanType(name) as JsonType
    }

    private val alias = mockk<PropertyAlias> {
        every { alias } returns "first"
        every { type } returns "net.nemerosa.First"
        every { createJsonType(any()) } returns JsonBooleanType("Alias")
    }

    private val provider = PropertiesJsonSchemaTypeProvider(
        propertyService = mockk<PropertyService> {
            every { propertyTypes } returns listOf(propertyType("net.nemerosa.First"), propertyType("net.nemerosa.Second"))
        },
        propertyAliases = listOf(alias),
    )

    private fun errors(entityType: ProjectEntityType, value: Map<String, Any>) =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(provider.createType(entityType.name, mockk()).asJson())
            .validate(value.asJson())

    @Test
    fun `Several properties are accepted at project, branch and build level`() {
        listOf(ProjectEntityType.PROJECT, ProjectEntityType.BRANCH, ProjectEntityType.BUILD).forEach { entityType ->
            assertEquals(
                emptyList(),
                errors(entityType, mapOf("first" to true, "net.nemerosa.Second" to false)),
                "Several properties on $entityType",
            )
        }
    }

}
