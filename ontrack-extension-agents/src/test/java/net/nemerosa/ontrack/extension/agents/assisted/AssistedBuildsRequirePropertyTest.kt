package net.nemerosa.ontrack.extension.agents.assisted

import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AssistedBuildsRequirePropertyTest {

    @Test
    fun `Names are trimmed, without blanks nor duplicates, in their order`() {
        assertEquals(
            AssistedBuildsRequireProperty(listOf("SCAN", "REVIEW")),
            AssistedBuildsRequireProperty.normalised(
                AssistedBuildsRequireProperty(listOf(" SCAN", "REVIEW ", "", "SCAN"))
            )
        )
    }

    @Test
    fun `JSON round trip`() {
        val value = AssistedBuildsRequireProperty(listOf("REVIEW", "SCAN"))
        val json = value.asJson()
        assertEquals(listOf("REVIEW", "SCAN"), json.path("validationStamps").values().map { it.asString() })
    }
}
