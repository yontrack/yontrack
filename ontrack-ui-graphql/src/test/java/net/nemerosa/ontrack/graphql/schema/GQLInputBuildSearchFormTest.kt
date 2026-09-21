package net.nemerosa.ontrack.graphql.schema

import graphql.schema.GraphQLInputObjectType
import net.nemerosa.ontrack.graphql.support.typeName
import net.nemerosa.ontrack.model.structure.BuildSearchForm
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GQLInputBuildSearchFormTest {

    @Test
    fun `Non nullable fields`() {
        val type = GQLInputBuildSearchForm().createInputType(mutableSetOf()) as GraphQLInputObjectType
        val fields = type.fields.associateBy { it.name }
        assertNotNull(fields["maximumCount"]) {
            assertEquals("Int", typeName(it.type))
        }
        assertNotNull(fields["buildExactMatch"]) {
            assertEquals("Boolean", typeName(it.type))
        }
    }

    @Test
    fun `Null argument`() {
        val form = GQLInputBuildSearchForm().convert(null)
        assertEquals(BuildSearchForm(), form)
    }

    @Test
    fun `Validation stamp name`() {
        val form = GQLInputBuildSearchForm().convert(mapOf("validationStampName" to "VS"))
        assertEquals(BuildSearchForm(validationStampName = "VS"), form)
    }

    @Test
    fun `Build exact match`() {
        val form = GQLInputBuildSearchForm().convert(mapOf("buildName" to "1.0.0-123", "buildExactMatch" to "true"))
        assertEquals(
            BuildSearchForm(
                buildName = "1.0.0-123",
                buildExactMatch = true,
            ), form
        )
    }

}
