package net.nemerosa.ontrack.extension.environments.ci

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.rules.core.VersionPatternSlotAdmissionRule
import net.nemerosa.ontrack.extension.environments.rules.core.VersionPatternSlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.parse
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
                          # Quoted, and it matters: `project()` names projects with digits only
                          # (`uid` is mmssSSS + a counter), so an unquoted name starting with 0
                          # and made only of octal digits is read as an octal number by
                          # SnakeYAML - 0557637467 arrives as "96419639". The project then does
                          # not resolve, defineSlots skips the slot without failing, and this
                          # test sees zero slots. Roughly 3% of runs, whenever the minute is
                          # 00-09 and every other digit happens to be 0-7.
                          - project: "${deployedProject.name}"
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
            env = EnvFixtures.generic(configuredProjectName)
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
    fun `A workflow removed from the CI configuration is removed from the slot`() {
        val environmentName = uid("env-")
        val configuredProjectName = uid("cfg-")
        val deployedProject = project()

        // Same shape as the self.dev slot in `.yontrack/ci.yaml` before and after #2074: the
        // CANDIDATE workflow goes, the RUNNING one stays.
        val candidateWorkflow = """
            - name: Starting the deployment
              trigger: CANDIDATE
              nodes:
                - id: deploying
                  executorId: mock
                  data:
                    text: Deploying
        """.trimIndent()
        val runningWorkflow = """
            - name: Deploying
              trigger: RUNNING
              nodes:
                - id: deploy
                  executorId: mock
                  data:
                    text: Deploy
        """.trimIndent()

        // The column of the entries under `workflows:` in the template below
        val workflowsIndent = " ".repeat(18)

        fun slotWorkflows(slot: Slot) =
            slotWorkflowService.getSlotWorkflowsBySlot(slot).associate { it.workflow.name to it.trigger }

        fun configure(vararg workflows: String) = configTestSupport.configureProject(
            yaml = """
                |version: v1
                |configuration:
                |  defaults:
                |    project:
                |      environments:
                |        environments:
                |          - name: $environmentName
                |            order: 200
                |        slots:
                |          - project: "${deployedProject.name}"
                |            environments:
                |              - name: $environmentName
                |                workflows:
                |${workflows.joinToString("\n").prependIndent(workflowsIndent)}
            """.trimMargin(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        configure(candidateWorkflow, runningWorkflow)
        val slot = slotService.findSlotsByProject(deployedProject).single()
        assertEquals(
            mapOf(
                "Starting the deployment" to SlotPipelineStatus.CANDIDATE,
                "Deploying" to SlotPipelineStatus.RUNNING,
            ),
            slotWorkflows(slot)
        )

        configure(runningWorkflow)
        assertEquals(
            mapOf(
                "Deploying" to SlotPipelineStatus.RUNNING,
            ),
            slotWorkflows(slot),
            "Only the RUNNING workflow is left on the slot"
        )
    }

    @Test
    @AsAdminTest
    fun `A slot admits agents through the CI configuration`() {
        val environmentName = uid("env-")
        val configuredProjectName = uid("cfg-")
        val deployedProject = project()

        fun configure(agentsAdmitted: Boolean?) = configTestSupport.configureProject(
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
                          - project: "${deployedProject.name}"
                            environments:
                              - name: $environmentName
                                ${agentsAdmitted?.let { "agentsAdmitted: $it" } ?: ""}
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        configure(agentsAdmitted = true)
        assertEquals(true, slotService.findSlotsByProject(deployedProject).single().agentsAdmitted)

        configure(agentsAdmitted = null)
        assertEquals(false, slotService.findSlotsByProject(deployedProject).single().agentsAdmitted, "False by default")
    }

    @Test
    @AsAdminTest
    fun `A slot declared through the CI configuration admits builds on their version`() {
        val environmentName = uid("env-")
        val configuredProjectName = uid("cfg-")
        val deployedProject = project()

        // Same shape as the self.dev slot in `.yontrack/ci.yaml`
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
                          - project: "${deployedProject.name}"
                            environments:
                              - name: $environmentName
                                admissionRules:
                                  - name: "versionPattern"
                                    ruleId: "versionPattern"
                                    ruleConfig:
                                      includes:
                                        - "\\d+\\.\\d+\\.\\d+(-beta\\.\\d+)?"
                                      excludes:
                                        - ".*-alpha\\..*"
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
        )

        val slot = slotService.findSlotsByProject(deployedProject).single()
        val rule = slotService.getAdmissionRuleConfigs(slot).single()
        assertEquals(VersionPatternSlotAdmissionRule.ID, rule.ruleId)
        assertEquals(
            VersionPatternSlotAdmissionRuleConfig(
                includes = listOf("\\d+\\.\\d+\\.\\d+(-beta\\.\\d+)?"),
                excludes = listOf(".*-alpha\\..*"),
            ),
            rule.ruleConfig.parse<VersionPatternSlotAdmissionRuleConfig>()
        )

        deployedProject.branch("main") {
            val ga = build("6.0.0")
            val alpha = build("6.0.0-alpha.3")
            assertEquals(true, slotService.isBuildEligible(slot, ga))
            assertEquals(false, slotService.isBuildEligible(slot, alpha))
        }
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
                          - project: "${deployedProject.name}"
                            environments:
                              - name: $unknownEnvironmentName
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName)
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
            env = EnvFixtures.generic(configuredProjectName)
        )

        // The unresolvable slot is skipped without aborting the injection: the environment declared
        // alongside it is still created.
        assertNotNull(environmentService.findByName(environmentName), "Declared environment has been injected")
    }

}
