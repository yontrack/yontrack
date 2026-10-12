package net.nemerosa.ontrack.extension.environments.rules.core

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionPatternSlotAdmissionRuleTest {

    private val rule = VersionPatternSlotAdmissionRule(
        buildDisplayNameService = mockk(),
        propertyService = mockk(),
        slotRepository = mockk {
            every { isValidRegex(any()) } returns true
        },
    )

    @Test
    fun `Valid patterns are accepted`() {
        rule.checkConfig(
            VersionPatternSlotAdmissionRuleConfig(
                includes = listOf("\\d+\\.\\d+\\.\\d+(-beta\\.\\d+)?"),
                excludes = listOf(".*-alpha\\..*"),
            ).asJson()
        )
    }

    @Test
    fun `An empty configuration is accepted`() {
        rule.checkConfig("{}".parseAsJson())
    }

    @Test
    fun `An invalid include is refused`() {
        val ex = assertThrows<SlotAdmissionRuleConfigException> {
            rule.checkConfig(VersionPatternSlotAdmissionRuleConfig(includes = listOf("6.(")).asJson())
        }
        assertTrue("6.(" in (ex.message ?: ""), ex.message)
    }

    @Test
    fun `An invalid exclude is refused`() {
        val ex = assertThrows<SlotAdmissionRuleConfigException> {
            rule.checkConfig(VersionPatternSlotAdmissionRuleConfig(excludes = listOf("[alpha")).asJson())
        }
        assertTrue("[alpha" in (ex.message ?: ""), ex.message)
    }

    @Test
    fun `A configuration which cannot be parsed is refused`() {
        assertThrows<SlotAdmissionRuleConfigException> {
            rule.checkConfig("""{"includes": {"pattern": 1}}""".parseAsJson())
        }
    }

    @Test
    fun `Version matching follows the includes and the excludes`() {
        val config = VersionPatternSlotAdmissionRuleConfig(
            includes = listOf("\\d+\\.\\d+\\.\\d+(-beta\\.\\d+)?"),
        )
        assertEquals(
            listOf(true, true, true, false, false, false),
            listOf("6.0.0", "6.0.1", "6.0.0-beta.6", "6.0.0-alpha.3", "6.0.1-rc.700", "12").map {
                rule.isVersionEligible(it, config)
            }
        )
    }

    @Test
    fun `The eligibility query anchors the patterns`() {
        val anchored = VersionPatternSlotAdmissionRule.anchor("\\d+\\.\\d+\\.\\d+")
        assertEquals("^(?:\\d+\\.\\d+\\.\\d+)$", anchored)
    }

}
