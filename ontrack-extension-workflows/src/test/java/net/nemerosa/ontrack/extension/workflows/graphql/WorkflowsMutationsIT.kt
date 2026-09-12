package net.nemerosa.ontrack.extension.workflows.graphql

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.workflows.AbstractWorkflowTestSupport
import net.nemerosa.ontrack.extension.workflows.acl.WorkflowRegistration
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowRegistry
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.json.getRequiredBooleanField
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class WorkflowsMutationsIT : AbstractWorkflowTestSupport() {

    @Autowired
    private lateinit var workflowRegistry: WorkflowRegistry

    @Test
    fun `Saving a YAML workflow`() {
        val workflow = """
            name: Test
            nodes:
              - id: start
                description: Starting node
                executorId: mock
                data:
                    text: Start
              - id: end
                parents:
                  - id: start
                executorId: mock
                data:
                    text: End
        """.trimIndent()
        asAdmin {
            run(
                """
                mutation SavingWorkflow(${'$'}workflow: String!) {
                    saveYamlWorkflow(input: {
                        workflow: ${'$'}workflow,
                    }) {
                        workflowId
                        errors {
                            message
                        }
                    }
                }
            """, mapOf("workflow" to workflow)
            ) { data ->
                checkGraphQLUserErrors(data, "saveYamlWorkflow") { node ->
                    val id = node.path("workflowId").asText()
                    // Getting the workflow back
                    val w = workflowRegistry.findWorkflow(id)
                        ?: fail("Cannot find workflow")
                    // Checking the descriptions
                    assertEquals(
                        "Starting node",
                        w.workflow.getNode("start").description
                    )
                    assertEquals(
                        null,
                        w.workflow.getNode("end").description
                    )
                }
            }
        }
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

        asAdmin {
            run(
                """
                    mutation Validate(${'$'}workflow: JSON!) {
                        validateJsonWorkflow(input: {
                            workflow: ${'$'}workflow,
                        }) {
                            errors {
                                message
                            }
                            validation {
                                error
                                errors
                            }
                        }
                    }
                """,
                mapOf("workflow" to json)
            ) { data ->
                checkGraphQLUserErrors(data, "validateJsonWorkflow") { node ->
                    val validation = node.path("validation")
                    assertTrue(validation.getRequiredBooleanField("error"))
                    assertEquals(
                        "The workflow contains at least one cycle",
                        validation.path("errors").path(0).asText()
                    )
                }
            }
        }
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

        asAdmin {
            run(
                """
                    mutation Validate(${'$'}workflow: JSON!) {
                        validateJsonWorkflow(input: {
                            workflow: ${'$'}workflow,
                        }) {
                            errors {
                                message
                            }
                            validation {
                                error
                                errors
                            }
                        }
                    }
                """,
                mapOf("workflow" to json)
            ) { data ->
                checkGraphQLUserErrors(data, "validateJsonWorkflow") { node ->
                    val validation = node.path("validation")
                    assertFalse(validation.getRequiredBooleanField("error"))
                    assertTrue(
                        validation.path("errors").isEmpty,
                        "No error message"
                    )
                }
            }
        }
    }


    @Test
    fun `Launching a workflow declares its own check and is denied to a regular user`() {
        val workflowId = asAdmin {
            workflowRegistry.saveYamlWorkflow(
                """
                    name: ${uid("w-")}
                    nodes:
                      - id: start
                        executorId: mock
                        data:
                            text: Start
                """.trimIndent()
            )
        }
        asUser {
            runWithError(
                """
                    mutation {
                        launchWorkflow(input: {workflowId: "$workflowId", context: []}) {
                            workflowInstanceId
                            errors {
                                message
                            }
                        }
                    }
                """,
                errorMessage = "Global function 'WorkflowRegistration' is not granted.",
            )
        }
    }

    @Test
    fun `Launching a workflow is granted to a user holding the workflow registration right`() {
        val workflowId = asAdmin {
            workflowRegistry.saveYamlWorkflow(
                """
                    name: ${uid("w-")}
                    nodes:
                      - id: start
                        executorId: mock
                        data:
                            text: Start
                """.trimIndent()
            )
        }
        asUserWith<WorkflowRegistration> {
            run(
                """
                    mutation {
                        launchWorkflow(input: {workflowId: "$workflowId", context: []}) {
                            workflowInstanceId
                            errors {
                                message
                            }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "launchWorkflow") { node ->
                    assertTrue(
                        node.path("workflowInstanceId").asText().isNotBlank(),
                        "Workflow instance has been created"
                    )
                }
            }
        }
    }

}
