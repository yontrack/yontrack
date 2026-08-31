package net.nemerosa.ontrack.extension.workflows.ci

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.extension.workflows.AbstractWorkflowTestSupport
import net.nemerosa.ontrack.extension.workflows.notifications.WorkflowNotificationChannel
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorkflowsBranchCIConfigExtensionIT : AbstractWorkflowTestSupport() {

    /**
     * One name per test instance (JUnit builds a new one per method), so no two tests - and no two
     * modules of the same CI shard - configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")


    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Autowired
    private lateinit var eventSubscriptionService: EventSubscriptionService

    @Autowired
    private lateinit var workflowNotificationChannel: WorkflowNotificationChannel

    @Test
    @AsAdminTest
    fun `Injecting branch workflows for the promotions`() {
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      promotions:
                        BRONZE: {}
                        RELEASE: {}
                      workflows:
                        BRONZE:
                          - name: On Bronze
                            nodes:
                              - id: start
                                executorId: mock
                                data:
                                  text: Start bronze
                  custom:
                    configs:
                      - conditions:
                          - name: branch
                            config: "release.*"
                        branch:
                          workflows:
                            BRONZE:
                              - name: On Bronze for release
                                nodes:
                                  - id: start
                                    executorId: mock
                                    data:
                                      text: Start bronze for release
                            RELEASE:
                              - name: On Release
                                nodes:
                                  - id: start
                                    executorId: mock
                                    data:
                                      text: Start release
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "release/5.0"),
        )

        // Getting the promotions
        val bronze = structureService.findPromotionLevelByName(branch.project.name, branch.name, "BRONZE").get()
        val release = structureService.findPromotionLevelByName(branch.project.name, branch.name, "RELEASE").get()

        // Bronze workflow
        assertNotNull(
            eventSubscriptionService.findSubscriptionByName(bronze, "On Bronze"),
            "Bronze subscription found"
        ) { sub ->
            assertEquals("workflow", sub.channel)
            assertNotNull(
                workflowNotificationChannel.validate(sub.channelConfig).config?.workflow,
                "Bronze workflow is set"
            ) { workflow ->
                assertEquals("On Bronze", workflow.name)
                assertEquals(1, workflow.nodes.size)
                val node = workflow.nodes.first()
                assertEquals("start", node.id)
                assertEquals("mock", node.executorId)
                assertEquals("Start bronze", node.data["text"].asText())
            }
        }

        // Bronze workflow for release
        assertNotNull(
            eventSubscriptionService.findSubscriptionByName(bronze, "On Bronze for release"),
            "Bronze for release subscription found"
        ) { sub ->
            assertEquals("workflow", sub.channel)
            assertNotNull(
                workflowNotificationChannel.validate(sub.channelConfig).config?.workflow,
                "Bronze workflow for release is set"
            ) { workflow ->
                assertEquals("On Bronze for release", workflow.name)
                assertEquals(1, workflow.nodes.size)
                val node = workflow.nodes.first()
                assertEquals("start", node.id)
                assertEquals("mock", node.executorId)
                assertEquals("Start bronze for release", node.data["text"].asText())
            }
        }

        // Release workflow
        assertNotNull(
            eventSubscriptionService.findSubscriptionByName(release, "On Release"),
            "Release subscription found"
        ) { sub ->
            assertEquals("workflow", sub.channel)
            assertNotNull(
                workflowNotificationChannel.validate(sub.channelConfig).config?.workflow,
                "Release workflow is set"
            ) { workflow ->
                assertEquals("On Release", workflow.name)
                assertEquals(1, workflow.nodes.size)
                val node = workflow.nodes.first()
                assertEquals("start", node.id)
                assertEquals("mock", node.executorId)
                assertEquals("Start release", node.data["text"].asText())
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Authoritative list of workflows per promotion`() {

        // First version
        val branch = configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      promotions:
                        BRONZE: {}
                      workflows:
                        BRONZE:
                          - name: On Bronze
                            nodes:
                              - id: start
                                executorId: mock
                                data:
                                  text: Start bronze
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "release/5.0"),
        )

        // We check that we have the subscription
        val bronze = structureService.findPromotionLevelByName(branch.project.name, branch.name, "BRONZE").get()
        assertNotNull(
            eventSubscriptionService.findSubscriptionByName(bronze, "On Bronze"),
            "Bronze subscription found"
        ) { sub ->
            assertEquals("workflow", sub.channel)
            assertNotNull(
                workflowNotificationChannel.validate(sub.channelConfig).config?.workflow,
                "Bronze workflow is set"
            ) { workflow ->
                assertEquals("On Bronze", workflow.name)
            }
        }

        // Second version
        configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      promotions:
                        BRONZE: {}
                      workflows:
                        BRONZE:
                          - name: On Bronze Second Version
                            nodes:
                              - id: start
                                executorId: mock
                                data:
                                  text: Start bronze
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "release/5.0"),
        )

        // We check that we have the NEW subscription
        assertNotNull(
            eventSubscriptionService.findSubscriptionByName(bronze, "On Bronze Second Version"),
            "New Bronze subscription found"
        ) { sub ->
            assertEquals("workflow", sub.channel)
            assertNotNull(
                workflowNotificationChannel.validate(sub.channelConfig).config?.workflow,
                "Bronze workflow is set"
            ) { workflow ->
                assertEquals("On Bronze Second Version", workflow.name)
            }
        }

        // ... and that the old one has been removed
        assertNull(
            eventSubscriptionService.findSubscriptionByName(bronze, "On Bronze"),
            "Old Bronze subscription gone"
        )

    }

}