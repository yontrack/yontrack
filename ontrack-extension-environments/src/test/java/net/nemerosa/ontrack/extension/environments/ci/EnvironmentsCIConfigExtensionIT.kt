package net.nemerosa.ontrack.extension.environments.ci

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EnvironmentsCIConfigExtensionIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var environmentService: EnvironmentService

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Test
    @AsAdminTest
    fun `Injection of environments based on the CI configuration`() {
        // Every name this test asserts on is unique to this run, so the test neither reads nor
        // destroys global environment state. See #1657.
        val environmentName = uid("env-")
        val configuredProjectName = uid("cfg-")

        val deployedProject = project()

        configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    project:
                      environments:
                        environments:
                          - name: $environmentName
                            description: Production environment for Yontrack itself
                            order: 200
                            tags:
                              - yontrack
                              - release
                        slots:
                          - project: ${deployedProject.name}
                            environments:
                              - name: $environmentName
                                admissionRules:
                                  - ruleId: promotion
                                    ruleConfig:
                                      promotion: GOLD
                                  - ruleId: branchPattern
                                    ruleConfig:
                                      includes:
                                        - main
                                workflows:
                                  - name: Creation
                                    trigger: CANDIDATE
                                    nodes:
                                      - id: start
                                        executorId: mock
                                        data:
                                            text: Start
                                      - id: end
                                        parents:
                                          - id: start
                                        executorId: mock
                                        data:
                                            text: End
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(
                extraEnv = mapOf("PROJECT_NAME" to configuredProjectName),
            )
        )

        assertNotNull(environmentService.findByName(environmentName), "Environment has been injected") {
            assertEquals("Production environment for Yontrack itself", it.description)
            assertEquals(200, it.order)
            assertEquals(listOf("yontrack", "release"), it.tags)
        }

        val slots = slotService.findSlotsByProject(deployedProject)
        assertEquals(
            1, slots.size,
            "Expected exactly one slot for project ${deployedProject.name}, got ${slots.map { it.environment.name }}"
        )
        val slot = slots.first()
        assertEquals(environmentName, slot.environment.name)

        assertEquals(2, slotService.getAdmissionRuleConfigs(slot).size)

        val workflows = slotWorkflowService.getSlotWorkflowsBySlot(slot)
        assertEquals(1, workflows.size, "Expected exactly one slot workflow, got ${workflows.map { it.workflow.name }}")
        val workflow = workflows.first()
        assertEquals("Creation", workflow.workflow.name)
        assertEquals(SlotPipelineStatus.CANDIDATE, workflow.trigger)
    }

    @Test
    @AsAdminTest
    fun `Slot referencing an unknown environment is skipped without failing the injection`() {
        val environmentName = uid("env-")
        val unknownEnvironmentName = uid("missing-env-")
        val configuredProjectName = uid("cfg-")

        val deployedProject = project()

        configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    project:
                      environments:
                        environments:
                          - name: $environmentName
                            order: 200
                        slots:
                          - project: ${deployedProject.name}
                            environments:
                              - name: $unknownEnvironmentName
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(
                extraEnv = mapOf("PROJECT_NAME" to configuredProjectName),
            )
        )

        // The rest of the injection still ran...
        assertNotNull(environmentService.findByName(environmentName), "Declared environment has been injected")

        // ... but the slot naming an environment that does not exist is skipped rather than created.
        val slots = slotService.findSlotsByProject(deployedProject)
        assertEquals(
            0, slots.size,
            "No slot created for an unresolvable environment, got ${slots.map { it.environment.name }}"
        )
    }

    @Test
    @AsAdminTest
    fun `Slot referencing an unknown project is skipped without failing the injection`() {
        val environmentName = uid("env-")
        val unknownProjectName = uid("missing-p-")
        val configuredProjectName = uid("cfg-")

        configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    project:
                      environments:
                        environments:
                          - name: $environmentName
                            order: 200
                        slots:
                          - project: $unknownProjectName
                            environments:
                              - name: $environmentName
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(
                extraEnv = mapOf("PROJECT_NAME" to configuredProjectName),
            )
        )

        // The unresolvable slot is skipped without aborting the injection: the environment declared
        // alongside it is still created.
        assertNotNull(environmentService.findByName(environmentName), "Declared environment has been injected")
    }

}
