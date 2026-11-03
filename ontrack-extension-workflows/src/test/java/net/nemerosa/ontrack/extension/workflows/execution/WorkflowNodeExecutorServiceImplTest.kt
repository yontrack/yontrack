package net.nemerosa.ontrack.extension.workflows.execution

import tools.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidationException
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.exceptions.NotFoundException
import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.extension.ExtensionFeature
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
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
    private lateinit var rejecting: RejectingNodeExecutor

    @BeforeEach
    fun setUp() {
        val nesting = NestingNodeExecutor()
        leaf = LeafNodeExecutor()
        rejecting = RejectingNodeExecutor()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.getExtensions(WorkflowNodeExecutor::class.java) } returns listOf(nesting, leaf, rejecting)
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
        val ex = assertFailsWith<WorkflowNodeValidationException> {
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
        assertFailsWith<WorkflowNodeValidationException> {
            service.validateWorkflowNodes(nested(WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH + 1))
        }
        // The counter of the aborted validation must have been unwound
        service.validateWorkflowNodes(nested(WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH))
        assertEquals(1, leaf.validations)
    }

    @Test
    fun `The error of an executor names the node and the workflow it was rejected in`() {
        val rejection = WorkflowNodeExecutorConfigException("Text is required")
        rejecting.rejection = rejection
        val ex = assertFailsWith<WorkflowNodeValidationException> {
            service.validateWorkflowNodes(rejected("Some workflow"))
        }
        assertEquals(
            """Configuration of node "bad" in workflow "Some workflow" is not valid > Text is required""",
            ex.message
        )
        assertSame(rejection, ex.cause, "The error of the executor is kept as the cause")
    }

    @Test
    fun `The error of an executor is reported on the node which was rejected and not on its siblings`() {
        rejecting.rejection = WorkflowNodeExecutorConfigException("Text is required")
        val ex = assertFailsWith<WorkflowNodeValidationException> {
            service.validateWorkflowNodes(
                Workflow(
                    name = "Some workflow",
                    nodes = listOf(
                        WorkflowNode(
                            id = "good",
                            executorId = LeafNodeExecutor.ID,
                            data = mapOf("text" to "Some text").asJson(),
                        ),
                        WorkflowNode(
                            id = "bad",
                            executorId = RejectingNodeExecutor.ID,
                            data = mapOf("text" to "").asJson(),
                        ),
                    ),
                )
            )
        }
        assertTrue(""""bad"""" in (ex.message ?: ""), "Rejected node is named: ${ex.message}")
        assertFalse(""""good"""" in (ex.message ?: ""), "Valid node is not named: ${ex.message}")
    }

    @Test
    fun `A workflow without a name is left out of the error`() {
        rejecting.rejection = WorkflowNodeExecutorConfigException("Text is required")
        val ex = assertFailsWith<WorkflowNodeValidationException> {
            service.validateWorkflowNodes(rejected(""))
        }
        assertEquals(
            """Configuration of node "bad" is not valid > Text is required""",
            ex.message
        )
    }

    @Test
    fun `Something the data names which cannot be found is reported on the node`() {
        val rejection = TestNotFoundException("Channel not found: no-such-channel")
        rejecting.rejection = rejection
        val ex = assertFailsWith<WorkflowNodeValidationException> {
            service.validateWorkflowNodes(rejected("Some workflow"))
        }
        assertEquals(
            """Configuration of node "bad" in workflow "Some workflow" is not valid > Channel not found: no-such-channel""",
            ex.message
        )
        assertSame(rejection, ex.cause)
    }

    @Test
    fun `Data which cannot be parsed is reported on the node`() {
        val rejection = JsonParseException("There was a problem parsing the JSON at path 'text'")
        rejecting.rejection = rejection
        val ex = assertFailsWith<WorkflowNodeValidationException> {
            service.validateWorkflowNodes(rejected("Some workflow"))
        }
        assertEquals(
            """Configuration of node "bad" in workflow "Some workflow" is not valid > There was a problem parsing the JSON at path 'text'""",
            ex.message
        )
        assertSame(rejection, ex.cause)
    }

    @Test
    fun `A percent in the error of an executor is not read as a format specifier`() {
        rejecting.rejection = WorkflowNodeExecutorConfigException("Path nemerosa%2Fyontrack is not valid")
        val ex = assertFailsWith<WorkflowNodeValidationException> {
            service.validateWorkflowNodes(rejected("100% of it"))
        }
        assertEquals(
            """Configuration of node "bad" in workflow "100% of it" is not valid > Path nemerosa%2Fyontrack is not valid""",
            ex.message
        )
    }

    @Test
    fun `A failure which is not about the data of the node propagates untouched`() {
        val bug = IllegalStateException("This is a bug")
        rejecting.rejection = bug
        val ex = assertFailsWith<IllegalStateException> {
            service.validateWorkflowNodes(rejected("Some workflow"))
        }
        assertSame(bug, ex)
    }

    @Test
    fun `The depth error of a nested workflow names every node on the way down`() {
        val ex = assertFailsWith<WorkflowNodeValidationException> {
            service.validateWorkflowNodes(nested(WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH + 1))
        }
        val message = ex.message ?: ""
        assertTrue(
            message.startsWith("""Configuration of node "nesting" in workflow "Level 4" is not valid > Configuration of node "nesting" in workflow "Level 3" is not valid > """),
            "The path to the nested node can be read from the error: $message"
        )
        assertTrue(message.endsWith("Workflows cannot be nested more than ${WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH} levels deep"), message)
    }

    private fun rejected(workflowName: String) = Workflow(
        name = workflowName,
        nodes = listOf(
            WorkflowNode(
                id = "bad",
                executorId = RejectingNodeExecutor.ID,
                data = mapOf("text" to "").asJson(),
            )
        ),
    )

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

    private class TestNotFoundException(message: String) : NotFoundException(message)

    /**
     * Rejects its data with whatever the test sets.
     */
    private class RejectingNodeExecutor : WorkflowNodeExecutor, Extension {
        var rejection: Exception? = null
        override val feature: ExtensionFeature get() = mockk()
        override val id: String = ID
        override val displayName: String = "Rejecting"

        override fun validate(data: JsonNode) {
            rejection?.let { throw it }
        }

        override fun execute(
            workflowInstance: WorkflowInstance,
            workflowNodeId: String,
            workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit,
        ): WorkflowNodeExecutorResult = error("Not used in this test")

        companion object {
            const val ID = "rejecting"
        }
    }

}
