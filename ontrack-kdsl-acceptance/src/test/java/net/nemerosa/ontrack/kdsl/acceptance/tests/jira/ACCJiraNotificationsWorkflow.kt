package net.nemerosa.ontrack.kdsl.acceptance.tests.jira

import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.workflows.AbstractACCDSLWorkflowsTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.workflows.WorkflowTestSupport
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.jira.JiraConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.jira.jira
import net.nemerosa.ontrack.kdsl.spec.extension.jira.mock.mock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Testing Jira notifications in a workflow.
 */
class ACCJiraNotificationsWorkflow : AbstractACCDSLWorkflowsTestSupport() {

    @Test
    fun `Ticket creation and linking into a single workflow`() {

        val name = uid("jira-")
        ontrack.configurations.jira.create(
            JiraConfiguration(
                name = name,
                url = "https://jira",
            )
        )

        project {
            branch {
                val pl = promotion()

                val workflowName = uid("w-")
                val workflow = """
                    name: $workflowName
                    nodes:
                        - id: creation
                          executorId: notification
                          data:
                            channel: jira-creation
                            channelConfig:
                                configName: $name
                                useExisting: false
                                projectName: TEST
                                issueType: Defect
                                titleTemplate: "Promotion in ${'$'}{project}"
                            template: "Promotion in ${'$'}{project}"
                        - id: promotion
                          executorId: notification
                          data:
                            channel: jira-creation
                            channelConfig:
                                configName: $name
                                useExisting: false
                                projectName: TEST
                                issueType: Defect
                                titleTemplate: "Promotion in ${'$'}{project} for ${'$'}{promotionLevel}"
                            template: "Promotion in ${'$'}{project} for ${'$'}{promotionLevel}"
                        - id: link
                          parents:
                            - id: creation
                            - id: promotion
                          executorId: notification
                          data:
                             channel: jira-link
                             channelConfig:
                                configName: $name
                                sourceQuery: key = ${'$'}{workflow.creation?path=result.ticketKey}
                                targetQuery: key = ${'$'}{workflow.promotion?path=result.ticketKey}
                                linkName: Relates
                """.trimIndent()

                pl.subscribe(
                    name = "Workflow",
                    channel = "workflow",
                    channelConfig = mapOf(
                        "workflow" to WorkflowTestSupport.yamlWorkflowToJson(workflow)
                    ),
                    keywords = null,
                    events = listOf(
                        "new_promotion_run",
                    ),
                )

                build {
                    // Triggering the workflow through the promotion
                    promote(pl.name)

                    // Waiting until the workflow is finished (using the audit)
                    val instance = waitUntilWorkflowByNameFinished(name = workflowName)

                    // Gets the workflow output to get the ticket keys
                    val keyCreation = instance.getExecutionOutput("creation")
                        ?.path("result")?.path("ticketKey")?.asText()
                        ?: fail("Could not find ticket key in output")
                    val keyPromotion = instance.getExecutionOutput("promotion")
                        ?.path("result")?.path("ticketKey")?.asText()
                        ?: fail("Could not find ticket key in output")

                    // Getting the tickets
                    val creation = ontrack.jira.mock.getIssueByKey(keyCreation)
                    val promotion = ontrack.jira.mock.getIssueByKey(keyPromotion)

                    // Checks the link
                    assertEquals(
                        listOf(promotion.key),
                        creation.links.map { it.key },
                    )
                }
            }
        }
    }

}