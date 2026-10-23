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
import kotlin.test.fail

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

    /**
     * Slots are synced forward: a slot the CI configuration does not declare is deleted. Only a
     * configuration which mentions the extension at all goes through that sync, and that is what
     * lets one branch alone own the slots - on Yontrack itself, `main` declares them and
     * release/5.5 declares nothing, so a 5.5 build neither brings back stale slots nor deletes
     * the ones main added (#2119).
     */
    @Test
    @AsAdminTest
    fun `A branch whose configuration declares no environments leaves the slots alone`() {
        val environmentName = uid("env-")
        val configuredProjectName = uid("cfg-")

        val deployedProject = project()

        configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      validations:
                        BUILD: {}
                  custom:
                    configs:
                      - conditions:
                          - name: branch
                            config: '^main${'$'}'
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
                                      - ruleId: branchPattern
                                        ruleConfig:
                                          includes:
                                            - main
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "main"),
        )
        val slot = slotService.findSlotsByProject(deployedProject).singleOrNull()
            ?: fail("Expected the slot declared on main")
        val rules = slotService.getAdmissionRuleConfigs(slot)

        configTestSupport.configureBranch(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    branch:
                      validations:
                        BUILD: {}
            """.trimIndent(),
            ci = "generic",
            scm = "mock",
            env = EnvFixtures.generic(configuredProjectName, scmBranch = "release/5.5"),
        )

        val slots = slotService.findSlotsByProject(deployedProject)
        assertEquals(listOf(slot.id), slots.map { it.id }, "The slot declared on main is still there")
        assertEquals(
            rules.map { it.ruleId to it.ruleConfig },
            slotService.getAdmissionRuleConfigs(slots.first()).map { it.ruleId to it.ruleConfig },
            "The slot's admission rules are unchanged"
        )
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
