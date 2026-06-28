package net.nemerosa.ontrack.model.structure

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PromotionLevelConfigurationTest {

    private fun field(name: String, displayName: String = name, type: PromotionLevelFieldType = PromotionLevelFieldType.TEXT) =
        PromotionLevelField(
            id = 0,
            name = name,
            displayName = displayName,
            description = null,
            type = type,
            required = false,
            options = emptyList(),
            position = 0,
        )

    @Test
    fun `merge with non-overlapping fields produces a union`() {
        val base = PromotionLevelConfiguration(
            name = "BRONZE",
            fields = listOf(field("ticket")),
        )
        val other = PromotionLevelConfiguration(
            name = "BRONZE",
            fields = listOf(field("env")),
        )
        val merged = base.merge(other)
        assertEquals(2, merged.fields.size)
        assertEquals(setOf("ticket", "env"), merged.fields.map { it.name }.toSet())
    }

    @Test
    fun `merge with overlapping field names uses other value`() {
        val base = PromotionLevelConfiguration(
            name = "BRONZE",
            fields = listOf(field("ticket", displayName = "Ticket (base)")),
        )
        val other = PromotionLevelConfiguration(
            name = "BRONZE",
            fields = listOf(field("ticket", displayName = "Ticket (other)")),
        )
        val merged = base.merge(other)
        assertEquals(1, merged.fields.size)
        assertEquals("Ticket (other)", merged.fields.first().displayName)
    }
}
