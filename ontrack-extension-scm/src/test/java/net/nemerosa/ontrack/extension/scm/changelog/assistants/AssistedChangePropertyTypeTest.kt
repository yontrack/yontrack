package net.nemerosa.ontrack.extension.scm.changelog.assistants

import io.mockk.mockk
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.exceptions.PropertyValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistedChangePropertyTypeTest {

    private val type = AssistedChangePropertyType(
        extensionFeature = SCMExtensionFeature(),
        eventPostService = mockk(),
        eventFactory = mockk(),
        eventQueryService = mockk(),
    )

    private val computed = AssistedChangeProperty(
        basis = AssistedChangeBasis.COMPUTED,
        unknownReason = null,
        assistants = listOf("Claude Code", "Codex"),
        assistedCommits = 2,
        totalCommits = 5,
        sessionLinks = listOf("https://claude.ai/code/session_1"),
        previousBuildId = 10,
    )

    @Test
    fun `JSON of the property`() {
        assertEquals(
            mapOf(
                "basis" to "COMPUTED",
                "unknownReason" to null,
                "assistants" to listOf("Claude Code", "Codex"),
                "assistedCommits" to 2,
                "totalCommits" to 5,
                "sessionLinks" to listOf("https://claude.ai/code/session_1"),
                "previousBuildId" to 10,
            ).asJson(),
            type.forStorage(computed)
        )
    }

    @Test
    fun `Storage round trip`() {
        assertEquals(computed, type.fromStorage(type.forStorage(computed)))
    }

    @Test
    fun `Unknown value round trip`() {
        val unknown = AssistedChangeProperty.unknown("no SCM")
        assertEquals(AssistedChangeBasis.UNKNOWN, unknown.basis)
        assertEquals("no SCM", unknown.unknownReason)
        assertEquals(unknown, type.fromStorage(type.forStorage(unknown)))
    }

    @Test
    fun `Assisted when there is at least one assistant`() {
        assertTrue(computed.assisted)
        assertFalse(computed.copy(assistants = emptyList(), assistedCommits = 0).assisted)
        assertFalse(AssistedChangeProperty.unknown("no SCM").assisted)
    }

    @Test
    fun `The assisted flag is not stored`() {
        assertFalse(type.forStorage(computed).has("assisted"))
    }

    @Test
    fun `Client input set by CI with defaults`() {
        val value = type.fromClient(
            """{"basis":"SET_BY_CI","assistants":["Codex","Claude Code"],"assistedCommits":1,"totalCommits":3}""".parseAsJson()
        )
        assertEquals(
            AssistedChangeProperty(
                basis = AssistedChangeBasis.SET_BY_CI,
                unknownReason = null,
                assistants = listOf("Claude Code", "Codex"),
                assistedCommits = 1,
                totalCommits = 3,
                sessionLinks = emptyList(),
                previousBuildId = null,
            ),
            value
        )
    }

    @Test
    fun `Client input without a basis is set by CI`() {
        val value = type.fromClient("""{"assistants":["Claude Code"],"assistedCommits":1,"totalCommits":1}""".parseAsJson())
        assertEquals(AssistedChangeBasis.SET_BY_CI, value.basis)
    }

    @Test
    fun `Assistants are trimmed, distinct and sorted, blank ones dropped`() {
        val value = AssistedChangeProperty.validated(
            computed.copy(assistants = listOf(" Codex", "Claude Code", "", "Codex ", "Copilot"))
        )
        assertEquals(listOf("Claude Code", "Codex", "Copilot"), value.assistants)
    }

    @Test
    fun `Session links are distinct and capped at 20`() {
        val links = (1..30).map { "https://claude.ai/code/session_$it" }
        val value = AssistedChangeProperty.validated(
            computed.copy(sessionLinks = links + links.take(5) + "  ")
        )
        assertEquals(links.take(20), value.sessionLinks)
    }

    @Test
    fun `The reason is dropped when the value is known`() {
        assertNull(AssistedChangeProperty.validated(computed.copy(unknownReason = "Some reason")).unknownReason)
    }

    @Test
    fun `Negative commit counts are rejected`() {
        assertThrows<PropertyValidationException> {
            type.fromClient("""{"basis":"SET_BY_CI","assistedCommits":-1,"totalCommits":3}""".parseAsJson())
        }
        assertThrows<PropertyValidationException> {
            type.fromClient("""{"basis":"SET_BY_CI","assistedCommits":0,"totalCommits":-3}""".parseAsJson())
        }
    }

    @Test
    fun `More assisted commits than commits are rejected`() {
        assertThrows<PropertyValidationException> {
            type.fromClient(
                """{"basis":"SET_BY_CI","assistants":["Claude Code"],"assistedCommits":4,"totalCommits":3}""".parseAsJson()
            )
        }
    }

    @Test
    fun `An unknown value with assistants is rejected`() {
        assertThrows<PropertyValidationException> {
            type.fromClient(
                """{"basis":"UNKNOWN","unknownReason":"no SCM","assistants":["Claude Code"]}""".parseAsJson()
            )
        }
    }

    @Test
    fun `An unknown basis is rejected`() {
        assertThrows<PropertyValidationException> {
            type.fromClient("""{"basis":"GUESSED"}""".parseAsJson())
        }
    }
}
