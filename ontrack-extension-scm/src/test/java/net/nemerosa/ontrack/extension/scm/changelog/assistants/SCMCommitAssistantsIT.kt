package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class SCMCommitAssistantsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    private val saveSettingsMutation = """
        mutation SaveSettings(${'$'}values: JSON!) {
            saveSettings(input: {id: "agent-markers", values: ${'$'}values}) {
                errors {
                    message
                }
            }
        }
    """.trimIndent()

    @Test
    fun `Default settings`() {
        withCleanSettings<AgentMarkersSettings> {
            assertEquals(
                AgentMarkersSettings(builtInConventions = true, patterns = emptyList()),
                cachedSettingsService.getCachedSettings(AgentMarkersSettings::class.java)
            )
        }
    }

    @Test
    fun `Settings round trip`() {
        withCleanSettings<AgentMarkersSettings> {
            val settings = AgentMarkersSettings(
                builtInConventions = false,
                patterns = listOf(
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.CO_AUTHOR_EMAIL, ".*-bot@acme\\.com"),
                    AgentMarkerPattern("Acme Agent", AgentMarkerPatternType.TRAILER, "Acme-Agent-Run"),
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.AUTHOR_EMAIL, "bot@acme\\.com"),
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.LOGIN, "acme-bot[bot]"),
                )
            )
            asAdmin {
                run(saveSettingsMutation, mapOf("values" to settings.asJson())) { data ->
                    assertNoUserError(data, "saveSettings")
                }
            }
            // Read from the store, not from the cache
            cachedSettingsService.invalidate(AgentMarkersSettings::class.java)
            assertEquals(settings, cachedSettingsService.getCachedSettings(AgentMarkersSettings::class.java))
        }
    }

    @Test
    fun `Invalid regular expressions are rejected when the settings are saved`() {
        withCleanSettings<AgentMarkersSettings> {
            asAdmin {
                runWithMatchingError(
                    saveSettingsMutation,
                    mapOf(
                        "values" to AgentMarkersSettings(
                            patterns = listOf(
                                AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.AUTHOR_EMAIL, "bot["),
                            )
                        ).asJson()
                    ),
                    errorMessage = "Pattern #1 (Acme Bot): invalid regular expression `bot[`",
                )
            }
            assertEquals(
                AgentMarkersSettings(),
                cachedSettingsService.getCachedSettings(AgentMarkersSettings::class.java)
            )
        }
    }

    @Test
    fun `Assistants in the GraphQL change log`() {
        withCleanSettings<AgentMarkersSettings> {
            asAdmin {
                // The mock SCM signs every commit as `unknown`
                settingsManagerService.saveSettings(
                    AgentMarkersSettings(
                        patterns = listOf(
                            AgentMarkerPattern("Mock Agent", AgentMarkerPatternType.LOGIN, "unknown"),
                        )
                    )
                )
                mockSCMTester.withMockSCMRepository {
                    project {
                        branch {
                            configureMockSCMBranch()
                            val from = build {
                                withRepositoryCommit("Before the change log")
                            }
                            build {
                                withRepositoryCommit(
                                    """
                                        Some fix

                                        Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                                        Claude-Session: https://claude.ai/code/session_123
                                    """.trimIndent(),
                                    property = false,
                                )
                                withRepositoryCommit(
                                    """
                                        Some feature

                                        Co-Authored-By: Jane Doe <jane@example.com>
                                        Assisted-by: Codex:gpt-5
                                    """.trimIndent()
                                )
                                run(
                                    """
                                        {
                                            scmChangeLog(from: ${from.id}, to: ${this.id}) {
                                                commits {
                                                    commit {
                                                        assistants {
                                                            name
                                                            markers
                                                            sessionLink
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    """
                                ) { data ->
                                    assertEquals(
                                        listOf(
                                            listOf(
                                                mapOf(
                                                    "name" to "Codex",
                                                    "markers" to listOf("ASSISTED_BY"),
                                                    "sessionLink" to null,
                                                ),
                                                mapOf(
                                                    "name" to "Mock Agent",
                                                    "markers" to listOf("AUTHOR"),
                                                    "sessionLink" to null,
                                                ),
                                            ),
                                            listOf(
                                                mapOf(
                                                    "name" to "Claude Code",
                                                    "markers" to listOf("CO_AUTHOR", "SESSION_TRAILER"),
                                                    "sessionLink" to "https://claude.ai/code/session_123",
                                                ),
                                                mapOf(
                                                    "name" to "Mock Agent",
                                                    "markers" to listOf("AUTHOR"),
                                                    "sessionLink" to null,
                                                ),
                                            ),
                                        ).asJson(),
                                        data.path("scmChangeLog").path("commits").values().map { it.path("commit").path("assistants") }.asJson()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
