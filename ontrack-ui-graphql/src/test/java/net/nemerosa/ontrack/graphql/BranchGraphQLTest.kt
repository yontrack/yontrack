package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.graphql.schema.CreateBranchInput
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class BranchGraphQLTest {

    @Test
    fun `projectId must be part of the options in CreateBranchInput`() {
        val inputs = GraphQLBeanConverter.asInputFields(CreateBranchInput::class, mutableSetOf())
        val projectId = inputs.find { it.name == "projectId" }
        assertNotNull(projectId, "projectId is defined")
    }

}