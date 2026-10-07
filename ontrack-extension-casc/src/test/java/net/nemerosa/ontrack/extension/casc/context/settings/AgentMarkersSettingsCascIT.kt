package net.nemerosa.ontrack.extension.casc.context.settings

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AgentMarkerPattern
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AgentMarkerPatternType
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AgentMarkersSettings
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.exceptions.InputException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@AsAdminTest
class AgentMarkersSettingsCascIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var agentMarkersSettingsCasc: AgentMarkersSettingsCasc

    @Test
    fun `Agent markers settings are in the CasC schema`() {
        assertValidYaml(
            """
                ontrack:
                    config:
                        settings:
                            agent-markers:
                                builtInConventions: true
                                patterns:
                                    - name: Acme Bot
                                      type: LOGIN
                                      value: acme-bot[bot]
            """.trimIndent()
        )
    }

    @Test
    fun `Agent markers settings`() {
        withSettings<AgentMarkersSettings> {
            casc(
                """
                    ontrack:
                        config:
                            settings:
                                agent-markers:
                                    builtInConventions: false
                                    patterns:
                                        - name: Acme Bot
                                          type: CO_AUTHOR_EMAIL
                                          value: .*-bot@acme\.com
                                        - name: Acme Agent
                                          type: TRAILER
                                          value: Acme-Agent-Run
                                        - name: Acme Bot
                                          type: LOGIN
                                          value: acme-bot[bot]
                """.trimIndent()
            )
            val settings = cachedSettingsService.getCachedSettings(AgentMarkersSettings::class.java)
            assertEquals(
                AgentMarkersSettings(
                    builtInConventions = false,
                    patterns = listOf(
                        AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.CO_AUTHOR_EMAIL, ".*-bot@acme\\.com"),
                        AgentMarkerPattern("Acme Agent", AgentMarkerPatternType.TRAILER, "Acme-Agent-Run"),
                        AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.LOGIN, "acme-bot[bot]"),
                    )
                ),
                settings
            )
        }
    }

    @Test
    fun `Agent markers settings are rendered`() {
        withSettings<AgentMarkersSettings> {
            casc(
                """
                    ontrack:
                        config:
                            settings:
                                agent-markers:
                                    patterns:
                                        - name: Acme Bot
                                          type: AUTHOR_EMAIL
                                          value: bot@acme\.com
                """.trimIndent()
            )
            val json = asAdmin { agentMarkersSettingsCasc.render() }
            assertEquals(
                mapOf(
                    "builtInConventions" to true,
                    "patterns" to listOf(
                        mapOf(
                            "name" to "Acme Bot",
                            "type" to "AUTHOR_EMAIL",
                            "value" to "bot@acme\\.com",
                        )
                    )
                ).asJson(),
                json
            )
        }
    }

    @Test
    fun `An invalid regular expression is rejected`() {
        withSettings<AgentMarkersSettings> {
            assertFailsWith<InputException> {
                casc(
                    """
                        ontrack:
                            config:
                                settings:
                                    agent-markers:
                                        patterns:
                                            - name: Acme Bot
                                              type: AUTHOR_EMAIL
                                              value: "bot["
                    """.trimIndent()
                )
            }
        }
    }

}
