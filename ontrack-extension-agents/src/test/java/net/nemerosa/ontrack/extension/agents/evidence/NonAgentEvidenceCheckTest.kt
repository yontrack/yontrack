package net.nemerosa.ontrack.extension.agents.evidence

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.agents.license.AgentsLicense
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.SignatureActor
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NonAgentEvidenceCheckTest {

    private lateinit var agentsLicense: AgentsLicense
    private lateinit var propertyService: PropertyService
    private lateinit var check: NonAgentEvidenceCheck

    private val stamp: ValidationStamp = mockk(relaxed = true)

    private val agent = SignatureActor(
        agent = "claude[agent]",
        displayName = "Claude",
        owner = "damien@yontrack.test",
    )

    @BeforeEach
    fun before() {
        agentsLicense = mockk()
        propertyService = mockk()
        check = NonAgentEvidenceCheck(agentsLicense, propertyService)
        every { agentsLicense.agentsEnabled } returns true
        every { stamp.name } returns "REVIEW"
    }

    private fun property(value: NonAgentEvidenceProperty?) {
        every { propertyService.getPropertyValue(stamp, NonAgentEvidencePropertyType::class.java) } returns value
    }

    @Test
    fun `An agent is refused on a restricted stamp`() {
        property(NonAgentEvidenceProperty())
        val ex = assertFailsWith<NonAgentEvidenceException> {
            check.checkAgentEvidence(stamp, agent)
        }
        assertEquals("evidence on REVIEW must come from a non-agent actor", ex.message)
    }

    @Test
    fun `An agent is accepted on a stamp without the property`() {
        property(null)
        check.checkAgentEvidence(stamp, agent)
    }

    @Test
    fun `An agent is accepted on a stamp whose property is switched off`() {
        property(NonAgentEvidenceProperty(enabled = false))
        check.checkAgentEvidence(stamp, agent)
    }

    @Test
    fun `Without the licence, the property is ignored`() {
        property(NonAgentEvidenceProperty())
        every { agentsLicense.agentsEnabled } returns false
        check.checkAgentEvidence(stamp, agent)
    }

    @Test
    fun `The property is enabled by default`() {
        assertEquals(
            NonAgentEvidenceProperty(enabled = true),
            emptyMap<String, Any>().asJson().parse<NonAgentEvidenceProperty>(),
        )
    }
}
