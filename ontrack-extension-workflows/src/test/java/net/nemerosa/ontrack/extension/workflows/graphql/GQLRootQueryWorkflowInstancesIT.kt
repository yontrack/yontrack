package net.nemerosa.ontrack.extension.workflows.graphql

import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceFixtures
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.getRequiredTextField
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class GQLRootQueryWorkflowInstancesIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var workflowInstanceRepository: WorkflowInstanceRepository

    @Test
    fun `Getting a list of workflow instances using the GraphQL API`() {
        asAdmin {
            workflowInstanceRepository.clearAll()

            val instance = WorkflowInstanceFixtures.simpleLinear()
            workflowInstanceRepository.createInstance(instance)

            run(
                """{
                    workflowInstances {
                        pageItems {
                            id
                        }
                    }
                }"""
            ) { data ->
                val ids = data.path("workflowInstances")
                    .path("pageItems")
                    .values().map { it.getRequiredTextField("id") }
                assertEquals(
                    listOf(instance.id),
                    ids
                )
            }
        }
    }

}