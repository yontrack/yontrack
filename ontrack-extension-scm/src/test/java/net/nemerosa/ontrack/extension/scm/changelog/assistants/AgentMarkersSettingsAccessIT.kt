package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.BuildCreate
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.execution.ErrorType
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals

/**
 * The agent markers are read by whoever creates builds, agents included, so that the CLI applies
 * them when it parses the trailers of a range itself (#2044). Saving them stays an administration.
 */
class AgentMarkersSettingsAccessIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private val settings = AgentMarkersSettings(
        builtInConventions = false,
        patterns = listOf(
            AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.CO_AUTHOR_EMAIL, ".*-bot@acme\\.com"),
            AgentMarkerPattern("Acme Agent", AgentMarkerPatternType.TRAILER, "Acme-Agent-Run"),
        )
    )

    private fun readQuery(id: String) = """
        {
            settings {
                settingsById(id: "$id") {
                    id
                    values
                }
            }
        }
    """

    private val saveSettingsMutation = """
        mutation SaveSettings(${'$'}values: JSON!) {
            saveSettings(input: {id: "agent-markers", values: ${'$'}values}) {
                errors {
                    message
                }
            }
        }
    """

    private fun readAgentMarkers(): JsonNode =
        run(readQuery("agent-markers")).path("settings").path("settingsById")

    private fun assertReadsSavedSettings() {
        val entry = readAgentMarkers()
        assertEquals("agent-markers", entry.path("id").asString())
        assertEquals(
            mapOf(
                "builtInConventions" to false,
                "patterns" to listOf(
                    mapOf("name" to "Acme Bot", "type" to "CO_AUTHOR_EMAIL", "value" to ".*-bot@acme\\.com"),
                    mapOf("name" to "Acme Agent", "type" to "TRAILER", "value" to "Acme-Agent-Run"),
                ),
            ).asJson(),
            entry.path("values"),
        )
    }

    private fun assertReadRefused(id: String = "agent-markers") {
        runWithMatchingError(readQuery(id), errorClassification = ErrorType.FORBIDDEN)
    }

    private fun assertSaveRefused() {
        runWithMatchingError(
            saveSettingsMutation,
            mapOf("values" to settings.asJson()),
            errorClassification = ErrorType.FORBIDDEN,
        )
    }

    private fun withSavedSettings(code: (project: Project) -> Unit) {
        withCleanSettings<AgentMarkersSettings> {
            // As admin, to create the accounts; each call then runs as the account under test
            asAdmin {
                settingsManagerService.saveSettings(settings)
                code(project())
            }
        }
    }

    private fun asBuildCreator(project: Project, code: () -> Unit) {
        asUser().withView(project).withProjectFunction(project, BuildCreate::class.java).call(code)
    }

    private fun agentOfProjectOwner(project: Project): AgentTestSupport.TestAgent {
        val owner = doCreateAccountWithProjectRole(project, Roles.PROJECT_OWNER)
        return agentTestSupport.registerAgent(owner = owner)
    }

    @Test
    fun `An administrator reads the agent markers`() {
        withSavedSettings {
            asAdmin {
                assertReadsSavedSettings()
            }
        }
    }

    @Test
    fun `An account which can create builds on one project reads the agent markers`() {
        withSavedSettings { project ->
            asBuildCreator(project) {
                assertReadsSavedSettings()
            }
        }
    }

    @Test
    fun `An automation account reads the agent markers`() {
        withSavedSettings {
            asGlobalRole(Roles.GLOBAL_AUTOMATION) {
                assertReadsSavedSettings()
            }
        }
    }

    @Test
    fun `An agent token reads the agent markers`() {
        withSavedSettings { project ->
            val agent = agentOfProjectOwner(project)
            agentTestSupport.withToken(agent.token) {
                assertReadsSavedSettings()
            }
        }
    }

    @Test
    fun `An account which cannot create builds anywhere cannot read the agent markers`() {
        withSavedSettings { project ->
            asUserWithView(project).call {
                assertReadRefused()
            }
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertReadRefused()
            }
        }
    }

    @Test
    fun `An agent whose owner cannot create builds anywhere cannot read the agent markers`() {
        withSavedSettings {
            val owner = doCreateAccountWithGlobalRole(Roles.GLOBAL_READ_ONLY)
            val agent = agentTestSupport.registerAgent(owner = owner)
            agentTestSupport.withToken(agent.token) {
                assertReadRefused()
            }
        }
    }

    @Test
    fun `Neither a build creator nor an agent can save the agent markers`() {
        withSavedSettings { project ->
            asBuildCreator(project) {
                assertSaveRefused()
            }
            val agent = agentTestSupport.registerAgent(owner = doCreateAccountWithGlobalRole(Roles.GLOBAL_ADMINISTRATOR))
            agentTestSupport.withToken(agent.token) {
                assertSaveRefused()
            }
            assertEquals(settings, cachedSettingsService.getCachedSettings(AgentMarkersSettings::class.java))
        }
    }

    @Test
    fun `Another settings entry stays reserved to the administrators`() {
        withSavedSettings { project ->
            asBuildCreator(project) {
                assertReadRefused(id = "system-message")
            }
            val agent = agentOfProjectOwner(project)
            agentTestSupport.withToken(agent.token) {
                assertReadRefused(id = "system-message")
            }
        }
    }
}
