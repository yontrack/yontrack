package net.nemerosa.ontrack.extension.workflows.execution

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidationException
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.extension.ExtensionFeature
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Depth guard of the node validation.
 *
 * The nesting executor below stands for the real hop a nested workflow takes: `notification` node >
 * `workflow` channel > [WorkflowNodeExecutorService.validateWorkflowNodes] again. Using a local executor
 * keeps this test in the module which owns the guard; the real hop, through the notification channel, is
 * covered by `WorkflowNotificationChannelValidationIT`.
 */
class WorkflowNodeExecutorServiceImplTest {

    private lateinit var service: WorkflowNodeExecutorServiceImpl
    private lateinit var leaf: LeafNodeExecutor

    @BeforeEach
    fun setUp() {
        val nesting = NestingNodeExecutor()
        leaf = LeafNodeExecutor()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.getExtensions(WorkflowNodeExecutor::class.java) } returns listOf(nesting, leaf)
        service = WorkflowNodeExecutorServiceImpl(extensionManager)
        nesting.service = service
    }

    @Test
    fun `A workflow which nests no other workflow is validated`() {
        service.validateWorkflowNodes(nested(1))
        assertEquals(1, leaf.validations)
    }

    @Test
    fun `A legitimate shallow nesting of workflows is validated`() {
        service.validateWorkflowNodes(nested(2))
        assertEquals(1, leaf.validations, "The nested workflow has been validated")
    }

    @Test
    fun `Nesting exactly at the maximum depth is still validated`() {
        service.validateWorkflowNodes(nested(WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH))
        assertEquals(1, leaf.validations)
    }

    @Test
    fun `Nesting workflows beyond the maximum depth is a validation error`() {
        val ex = assertFailsWith<WorkflowValidationException> {
            service.validateWorkflowNodes(nested(WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH + 1))
        }
        assertTrue(
            "nested" in (ex.message ?: ""),
            "The error names the nesting as the problem: ${ex.message}"
        )
        assertEquals(
            0, leaf.validations,
            "The validation stopped at the limit instead of running the nested leaves"
        )
    }

    @Test
    fun `The depth is counted per validation and not accumulated over several of them`() {
        // A first validation which goes as deep as it is allowed to
        service.validateWorkflowNodes(nested(WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH))
        // A second one, on the same thread, must not inherit any of its depth
        service.validateWorkflowNodes(nested(WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH))
        assertEquals(2, leaf.validations)
    }

    @Test
    fun `A failed validation does not leave the depth counter behind`() {
        assertFailsWith<WorkflowValidationException> {
            service.validateWorkflowNodes(nested(WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH + 1))
        }
        // The counter of the aborted validation must have been unwound
        service.validateWorkflowNodes(nested(WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH))
        assertEquals(1, leaf.validations)
    }

    /**
     * Builds a workflow nesting [levels] workflows into each other, the innermost one holding the single
     * leaf node.
     */
    private fun nested(levels: Int): Workflow =
        if (levels <= 1) {
            Workflow(
                name = "Level 1",
                nodes = listOf(
                    WorkflowNode(
                        id = "leaf",
                        executorId = LeafNodeExecutor.ID,
                        data = mapOf("text" to "Some text").asJson(),
                    )
                ),
            )
        } else {
            Workflow(
                name = "Level $levels",
                nodes = listOf(
                    WorkflowNode(
                        id = "nesting",
                        executorId = NestingNodeExecutor.ID,
                        data = mapOf("workflow" to nested(levels - 1)).asJson(),
                    )
                ),
            )
        }

    /**
     * Stands for the `notification` executor pointing at the `workflow` channel: its own data carries a
     * complete workflow, which it validates by calling the service back.
     */
    private class NestingNodeExecutor : WorkflowNodeExecutor, Extension {
        lateinit var service: WorkflowNodeExecutorService
        override val feature: ExtensionFeature get() = mockk()
        override val id: String = ID
        override val displayName: String = "Nesting"

        override fun validate(data: JsonNode) {
            service.validateWorkflowNodes(data.path("workflow").parse<Workflow>())
        }

        override fun execute(
            workflowInstance: WorkflowInstance,
            workflowNodeId: String,
            workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit,
        ): WorkflowNodeExecutorResult = error("Not used in this test")

        companion object {
            const val ID = "nesting"
        }
    }

    /**
     * Counts how many leaf validations a single call fans out into.
     */
    private class LeafNodeExecutor : WorkflowNodeExecutor, Extension {
        var validations: Int = 0
        override val feature: ExtensionFeature get() = mockk()
        override val id: String = ID
        override val displayName: String = "Leaf"

        override fun validate(data: JsonNode) {
            validations++
        }

        override fun execute(
            workflowInstance: WorkflowInstance,
            workflowNodeId: String,
            workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit,
        ): WorkflowNodeExecutorResult = error("Not used in this test")

        companion object {
            const val ID = "leaf"
        }
    }
}
