package net.nemerosa.ontrack.extension.environments.casc

import net.nemerosa.ontrack.extension.environments.EnvironmentTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * `agentsAdmitted` on the slots of the environments as code - the injection which the CasC and the
 * `environments` extension of the CI configuration share, and the rendering of the CasC.
 */
@AsAdminTest
class SlotAgentsAdmittedCascIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var environmentsInjection: EnvironmentsInjection

    @Autowired
    private lateinit var environmentsCascContext: EnvironmentsCascContext

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Test
    fun `Setting, keeping and rendering agentsAdmitted for a slot`() {
        val environment = environmentTestSupport.withEnvironment {}
        val project = project()

        fun define(agentsAdmitted: Boolean) = environmentsInjection.defineSlots(
            listOf(
                SlotCasc(
                    project = project.name,
                    environments = listOf(
                        SlotEnvironmentCasc(name = environment.name, agentsAdmitted = agentsAdmitted)
                    ),
                )
            )
        )

        define(agentsAdmitted = true)
        assertEquals(true, slotService.findSlotsByProject(project).single().agentsAdmitted, "Created admitting agents")

        val rendered = environmentsCascContext.render()
            .path("slots")
            .first { it.path("project").asText() == project.name }
            .path("environments")
            .first { it.path("name").asText() == environment.name }
        assertEquals(true, rendered.path("agentsAdmitted").asBoolean(), "Rendered")

        define(agentsAdmitted = false)
        assertEquals(false, slotService.findSlotsByProject(project).single().agentsAdmitted, "Updated")
    }
}
