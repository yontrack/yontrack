package net.nemerosa.ontrack.kdsl.acceptance.tests.scm

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.spec.events
import net.nemerosa.ontrack.kdsl.spec.extension.scm.AssistedChange
import net.nemerosa.ontrack.kdsl.spec.extension.scm.AssistedChangeBasis
import net.nemerosa.ontrack.kdsl.spec.extension.scm.assistedChange
import net.nemerosa.ontrack.kdsl.spec.extension.scm.withMockScmRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Assisted change of the builds (#2028): set by the CI, or computed by Yontrack from the change log,
 * and the `build_assisted` event.
 */
class ACCAssistedChange : AbstractACCDSLTestSupport() {

    @Test
    fun `CI sets the assisted change of a build, and the build_assisted event is posted once`() {
        project {
            branch {
                build {
                    assistedChange = AssistedChange(
                        basis = AssistedChangeBasis.SET_BY_CI,
                        assistants = listOf("Codex", "Claude Code", "Codex"),
                        assistedCommits = 2,
                        totalCommits = 3,
                        sessionLinks = listOf("https://claude.ai/code/session_1"),
                    )
                    // Read back, normalised
                    assertEquals(
                        AssistedChange(
                            basis = AssistedChangeBasis.SET_BY_CI,
                            assistants = listOf("Claude Code", "Codex"),
                            assistedCommits = 2,
                            totalCommits = 3,
                            sessionLinks = listOf("https://claude.ai/code/session_1"),
                        ),
                        assistedChange
                    )
                    // Setting it again does not post the event again
                    assistedChange = assistedChange?.copy(assistants = listOf("Devin"))
                }
            }
            val events = ontrack.events(project = name, eventTypes = listOf("build_assisted"))
            assertEquals(1, events.items.size, "One build_assisted event")
            val event = events.items.single()
            assertEquals("Claude Code, Codex", event.values["assistants"])
            assertEquals("2", event.values["assistedCommits"])
            assertEquals("3", event.values["totalCommits"])
            assertEquals("https://claude.ai/code/session_1", event.values["sessionLinks"])
        }
    }

    @Test
    fun `Yontrack computes the assisted change of a build from the change log`() {
        withMockScmRepository(ontrack, prefix = "acc-assisted-change") {
            project {
                branch {
                    configuredForMockRepository()
                    val from = build { withRepositoryCommit("Before") }
                    val to = build {
                        withRepositoryCommit("Some feature", property = false)
                        withRepositoryCommit(
                            """
                                Some fix

                                Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                            """.trimIndent()
                        )
                    }
                    waitUntil(timeout = 30_000L, interval = 500L, task = "Assisted change computed") {
                        to.assistedChange?.basis == AssistedChangeBasis.COMPUTED
                    }
                    assertEquals(
                        AssistedChange(
                            basis = AssistedChangeBasis.COMPUTED,
                            assistants = listOf("Claude Code"),
                            assistedCommits = 1,
                            totalCommits = 2,
                            previousBuildId = from.id.toInt(),
                        ),
                        to.assistedChange
                    )
                    waitUntil(timeout = 30_000L, interval = 500L, task = "build_assisted event") {
                        ontrack.events(project = project.name, eventTypes = listOf("build_assisted")).items.size == 1
                    }
                }
            }
        }
    }
}
