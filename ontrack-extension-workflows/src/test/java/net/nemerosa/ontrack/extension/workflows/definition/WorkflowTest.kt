package net.nemerosa.ontrack.extension.workflows.definition

import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WorkflowTest {

    @Test
    fun `One single start node`() {
        val workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
        assertEquals(
            listOf("start"),
            workflow.getNextNodes(null)
        )
    }

    @Test
    fun `Two start nodes`() {
        val workflow = WorkflowFixtures.twoParallelAndJoin()
        assertEquals(
            listOf("start-a", "start-b"),
            workflow.getNextNodes(null)
        )
    }

}