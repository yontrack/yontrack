package net.nemerosa.ontrack.model.structure

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `merge - autoRevoke is unspecified when no layer sets it`() {
        val merged = PromotionLevelConfiguration(name = "BRONZE")
            .merge(PromotionLevelConfiguration(name = "BRONZE"))
        assertNull(merged.autoRevoke)
    }

    @Test
    fun `merge - a later layer can turn autoRevoke on`() {
        val merged = PromotionLevelConfiguration(name = "BRONZE")
            .merge(PromotionLevelConfiguration(name = "BRONZE", autoRevoke = true))
        assertEquals(true, merged.autoRevoke)
    }

    @Test
    fun `merge - a later layer can turn autoRevoke off`() {
        val merged = PromotionLevelConfiguration(name = "BRONZE", autoRevoke = true)
            .merge(PromotionLevelConfiguration(name = "BRONZE", autoRevoke = false))
        assertEquals(false, merged.autoRevoke)
    }

    @Test
    fun `merge - a layer which does not mention autoRevoke keeps the initial value`() {
        val merged = PromotionLevelConfiguration(name = "BRONZE", autoRevoke = true)
            .merge(PromotionLevelConfiguration(name = "BRONZE"))
        assertEquals(true, merged.autoRevoke)
    }
}
