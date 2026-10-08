package net.nemerosa.ontrack.kdsl.acceptance.tests.agents

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.admin.agents
import net.nemerosa.ontrack.kdsl.spec.extension.scm.AgentMarkerPattern
import net.nemerosa.ontrack.kdsl.spec.extension.scm.AgentMarkersSettings
import net.nemerosa.ontrack.kdsl.spec.extension.scm.agentMarkers
import net.nemerosa.ontrack.kdsl.spec.settings.settings
import net.nemerosa.ontrack.kdsl.spec.withToken
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The CLI applies the custom agent markers when it parses the trailers of a range itself, with an
 * agent or CI token (#2044).
 */
class ACCAgentMarkersSettings : AbstractACCDSLTestSupport() {

    @Test
    fun `An agent token reads the agent markers an administrator saved`() {
        val markers = AgentMarkersSettings(
            builtInConventions = false,
            patterns = listOf(
                AgentMarkerPattern(name = "Acme Bot", type = "CO_AUTHOR_EMAIL", value = ".*-bot@acme\\.com"),
                AgentMarkerPattern(name = "Acme Agent", type = "TRAILER", value = "Acme-Agent-Run"),
            ),
        )
        // Reading needs a project to create builds on: the owner, an automation account, creates builds on all
        project {
            lateinit var token: String
            withUser(globalRole = "AUTOMATION") {
                token = ontrack.agents.register(slug = uid("acc-").lowercase()).generateToken("ci")
            }
            ontrack.settings.agentMarkers.with({ markers }) {
                assertEquals(markers, ontrack.withToken(token).settings.agentMarkers.read())
            }
        }
    }
}
