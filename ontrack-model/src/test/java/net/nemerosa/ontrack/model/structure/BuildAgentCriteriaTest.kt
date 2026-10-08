package net.nemerosa.ontrack.model.structure

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuildAgentCriteriaTest {

    @Test
    fun `No assisted criterion when blank`() {
        assertNull(BuildAgentCriteria.parseAssisted(null))
        assertNull(BuildAgentCriteria.parseAssisted(""))
        assertNull(BuildAgentCriteria.parseAssisted("  "))
    }

    @Test
    fun `Assisted criterion is case-insensitive`() {
        assertEquals(BuildAssistedCriterion.YES, BuildAgentCriteria.parseAssisted("YES"))
        assertEquals(BuildAssistedCriterion.NO, BuildAgentCriteria.parseAssisted("no"))
        assertEquals(BuildAssistedCriterion.UNKNOWN, BuildAgentCriteria.parseAssisted(" Unknown "))
    }

    @Test
    fun `Invalid assisted criterion`() {
        val ex = assertThrows<BuildAgentCriterionException> {
            BuildAgentCriteria.parseAssisted("maybe")
        }
        assertEquals("""Assisted must be YES, NO or UNKNOWN, not "maybe".""", ex.message)
    }

    @Test
    fun `No actor criterion when blank`() {
        assertNull(BuildAgentCriteria.parseActor(null))
        assertNull(BuildAgentCriteria.parseActor(""))
        assertNull(BuildAgentCriteria.parseActor("  "))
    }

    @Test
    fun `Actor criterion for the persons and for the agents is case-insensitive`() {
        assertEquals(BuildActorCriterion.Human, BuildAgentCriteria.parseActor("HUMAN"))
        assertEquals(BuildActorCriterion.Human, BuildAgentCriteria.parseActor("human"))
        assertEquals(BuildActorCriterion.Agent, BuildAgentCriteria.parseActor("AGENT"))
        assertEquals(BuildActorCriterion.Agent, BuildAgentCriteria.parseActor(" Agent "))
    }

    @Test
    fun `Actor criterion for one agent`() {
        assertEquals(
            BuildActorCriterion.OneAgent("claude[agent]"),
            BuildAgentCriteria.parseActor("claude[agent]")
        )
        assertEquals(
            BuildActorCriterion.OneAgent("claude-2[agent]"),
            BuildAgentCriteria.parseActor(" Claude-2[AGENT] ")
        )
    }

    @Test
    fun `Invalid actor criterion`() {
        listOf("damien@yontrack.test", "[agent]", "not a slug[agent]", "robot").forEach { value ->
            val ex = assertThrows<BuildAgentCriterionException> {
                BuildAgentCriteria.parseActor(value)
            }
            assertEquals(
                """Actor must be HUMAN, AGENT or the identifier of an agent, <slug>[agent], not "$value".""",
                ex.message
            )
        }
    }
}
