package net.nemerosa.ontrack.extension.environments.service.graph

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.environments.EnvironmentTestFixtures
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotTestFixtures
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.model.structure.ProjectFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectSlotGraphServiceImplTest {

    private val project = ProjectFixtures.testProject()

    private fun slot(name: String, order: Int, id: String = name) = SlotTestFixtures.testSlot(
        id = id,
        env = EnvironmentTestFixtures.testEnvironment(name = name, order = order),
        project = project,
    )

    private fun graph(vararg slots: Slot): SlotGraph {
        val slotService = mockk<SlotService>()
        // The set is handed over in a deliberately unhelpful order
        every { slotService.findSlotsByProject(project, Slot.DEFAULT_QUALIFIER) } returns linkedSetOf(*slots)
        return ProjectSlotGraphServiceImpl(slotService).slotGraph(project)
    }

    @Test
    fun `Slots sharing the same environment order are sorted by environment name`() {
        val production = slot("production", 40)
        val demo = slot("demo", 30)
        val staging = slot("staging", 20)
        val acceptance = slot("acceptance", 30)
        val dev = slot("dev", 10)

        val nodes = graph(production, demo, staging, acceptance, dev).slotNodes

        assertEquals(
            listOf("dev", "staging", "acceptance", "demo", "production"),
            nodes.map { it.slot.id },
        )
        assertEquals(
            listOf(
                emptyList(),
                listOf("dev"),
                listOf("staging"),
                listOf("staging"),
                listOf("acceptance", "demo"),
            ),
            nodes.map { node -> node.parents.map { it.id } },
        )
    }

    @Test
    fun `Slots sharing the same environment order and name are sorted by slot ID`() {
        // Not possible within one project and qualifier, but the order must not depend on the set
        val b = slot("env", 10, id = "b")
        val a = slot("env", 10, id = "a")
        val next = slot("next", 20)

        val nodes = graph(next, b, a).slotNodes

        assertEquals(listOf("a", "b", "next"), nodes.map { it.slot.id })
        assertEquals(listOf("a", "b"), nodes.last().parents.map { it.id })
    }

}
