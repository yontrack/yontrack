package net.nemerosa.ontrack.extension.hook

import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HookGraphQLTest {

    @Test
    fun `Checking the HookResponse GraphQL type`() {
        val type = GraphQLBeanConverter.asObjectType(HookResponse::class, GQLTypeCache())
        assertEquals(
            setOf("type", "infoLink"),
            type.fieldDefinitions.map { it.name }.toSet(),
        )
    }

}
