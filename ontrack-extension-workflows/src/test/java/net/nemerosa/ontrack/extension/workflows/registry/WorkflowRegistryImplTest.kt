package net.nemerosa.ontrack.extension.workflows.registry

import com.fasterxml.jackson.databind.JsonNode
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidationException
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutor
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorConfigException
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorResult
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorServiceImpl
import net.nemerosa.ontrack.it.MockSecurityService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.extension.ExtensionFeature
import net.nemerosa.ontrack.model.support.StorageService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowRegistryImplTest {

    private lateinit var storageService: StorageService
    private lateinit var workflowRegistry: WorkflowRegistry

    @BeforeEach
    fun setUp() {
        storageService = mockk(relaxed = true)
        val securityService = MockSecurityService()
        val extensionManager = mockk<ExtensionManager>()
        every { extensionManager.getExtensions(WorkflowNodeExecutor::class.java) } returns listOf(TestNodeExecutor())
        workflowRegistry = WorkflowRegistryImpl(
            storageService,
            securityService,
            WorkflowNodeExecutorServiceImpl(extensionManager),
        )
    }

    /**
     * Stands for the executors whose validation is stricter than the parsing of their data: the `text`
     * of a `mock` node is required.
     */
    private class TestNodeExecutor : WorkflowNodeExecutor, Extension {
        override val feature: ExtensionFeature get() = mockk()
        override val id: String = "mock"
        override val displayName: String = "Mock"

        override fun validate(data: JsonNode) {
            if (data.path("text").asText().isNullOrBlank()) {
                throw WorkflowNodeExecutorConfigException("Text is required for mock node executor")
            }
        }

        override fun execute(
            workflowInstance: WorkflowInstance,
            workflowNodeId: String,
            workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit,
        ): WorkflowNodeExecutorResult = error("Not used in this test")
    }

    @Test
    fun `Validation on saving a Yaml workflow`() {
        val yaml = """
            name: Sample
            nodes:
                - id: start
                  executorId: mock
                  data:
                    text: Start
                  parents:
                    - id: end
                - id: end
                  executorId: mock
                  data:
                    text: End
                  parents:
                    - id: start
        """.trimIndent()
        assertFailsWith<WorkflowValidationException>(
            message = "Validation of the workflow returned the following errors:\n" +
                    "* The workflow contains at least one cycle."
        ) {
            workflowRegistry.saveYamlWorkflow(yaml)
        }
    }

    @Test
    fun `Parsing error on validation`() {
        val json = mapOf(
            // Missing name
            "nodes" to listOf(
                mapOf(
                    "id" to "start",
                    "executorId" to "mock",
                    "data" to mapOf(
                        "text" to "Test"
                    ),
                    "parents" to emptyList<JsonNode>()
                )
            )
        ).asJson()
        val validation = workflowRegistry.validateJsonWorkflow(json)
        assertTrue(validation.error)
        assertEquals("""
            There was a problem parsing the JSON at path 'name': Instantiation of [simple type, class net.nemerosa.ontrack.extension.workflows.definition.Workflow] value failed for JSON property name due to missing (therefore NULL) value for creator parameter name which is a non-nullable type
        """.trimIndent(), validation.errors.firstOrNull())
    }

    @Test
    fun `Name is required`() {
        val json = mapOf(
            "name" to " ",
            "nodes" to listOf(
                mapOf(
                    "id" to "start",
                    "executorId" to "mock",
                    "data" to mapOf(
                        "text" to "Test"
                    ),
                    "parents" to emptyList<JsonNode>()
                )
            )
        ).asJson()
        val validation = workflowRegistry.validateJsonWorkflow(json)
        assertTrue(validation.error)
        assertEquals("Workflow name is required", validation.errors.firstOrNull())
    }

    @Test
    fun `At least one node is required`() {
        val json = mapOf(
            "name" to "Some name",
            "nodes" to emptyList<JsonNode>()
        ).asJson()
        val validation = workflowRegistry.validateJsonWorkflow(json)
        assertTrue(validation.error)
        assertEquals(validation.errors.firstOrNull(), "At least one node is required.")
    }

    @Test
    fun `No cycles`() {
        val json = mapOf(
            "name" to "Cycles",
            "nodes" to listOf(
                mapOf(
                    "id" to "start",
                    "executorId" to "mock",
                    "data" to mapOf(
                        "text" to "Start"
                    ),
                    "parents" to listOf(
                        mapOf("id" to "end")
                    )
                ),
                mapOf(
                    "id" to "middle",
                    "executorId" to "mock",
                    "data" to mapOf(
                        "text" to "Middle"
                    ),
                    "parents" to listOf(
                        mapOf("id" to "start")
                    )
                ),
                mapOf(
                    "id" to "end",
                    "executorId" to "mock",
                    "data" to mapOf(
                        "text" to "End"
                    ),
                    "parents" to listOf(
                        mapOf("id" to "middle")
                    )
                ),
            )
        ).asJson()
        val validation = workflowRegistry.validateJsonWorkflow(json)
        assertTrue(validation.error)
        assertEquals("The workflow contains at least one cycle", validation.errors.firstOrNull())
    }

    @Test
    fun `No error`() {
        val json = mapOf(
            "name" to "No error",
            "nodes" to listOf(
                mapOf(
                    "id" to "start",
                    "executorId" to "mock",
                    "data" to mapOf(
                        "text" to "Start"
                    ),
                    "parents" to emptyList<JsonNode>()
                ),
                mapOf(
                    "id" to "middle",
                    "executorId" to "mock",
                    "data" to mapOf(
                        "text" to "Middle"
                    ),
                    "parents" to listOf(
                        mapOf("id" to "start")
                    )
                ),
                mapOf(
                    "id" to "end",
                    "executorId" to "mock",
                    "data" to mapOf(
                        "text" to "End"
                    ),
                    "parents" to listOf(
                        mapOf("id" to "middle")
                    )
                ),
            )
        ).asJson()
        val validation = workflowRegistry.validateJsonWorkflow(json)
        assertFalse(validation.error)
        assertTrue(validation.errors.isEmpty())
    }


    @Test
    fun `Node data is validated on validation`() {
        val json = mapOf(
            "name" to "Bad node data",
            "nodes" to listOf(
                mapOf(
                    "id" to "start",
                    "executorId" to "mock",
                    "data" to mapOf(
                        "text" to ""
                    ),
                    "parents" to emptyList<JsonNode>()
                )
            )
        ).asJson()
        val validation = workflowRegistry.validateJsonWorkflow(json)
        assertTrue(validation.error)
        assertEquals("Text is required for mock node executor", validation.errors.firstOrNull())
    }

    @Test
    fun `An unknown executor is reported on validation`() {
        val json = mapOf(
            "name" to "Unknown executor",
            "nodes" to listOf(
                mapOf(
                    "id" to "start",
                    "executorId" to "no-such-executor",
                    "data" to mapOf(
                        "text" to "Start"
                    ),
                    "parents" to emptyList<JsonNode>()
                )
            )
        ).asJson()
        val validation = workflowRegistry.validateJsonWorkflow(json)
        assertTrue(validation.error)
        assertTrue(
            """"no-such-executor" not found""" in (validation.errors.firstOrNull() ?: ""),
            "Unknown executor is reported: ${validation.errors}"
        )
    }

    @Test
    fun `Node data is validated on saving`() {
        val yaml = """
            name: Bad node data
            nodes:
                - id: start
                  executorId: mock
                  data:
                    text: ""
        """.trimIndent()
        assertFailsWith<WorkflowNodeExecutorConfigException> {
            workflowRegistry.saveYamlWorkflow(yaml)
        }
    }

}
