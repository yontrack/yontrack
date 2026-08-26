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

    /**
     * `mergeList` calls `merge` as `later.merge(earlier)` - see `BranchConfiguration.merge` - so the
     * receiver below is always the layer which gets the last word.
     */
    private fun merged(later: Boolean?, earlier: Boolean?) =
        PromotionLevelConfiguration(name = "BRONZE", autoRevoke = later)
            .merge(PromotionLevelConfiguration(name = "BRONZE", autoRevoke = earlier))
            .autoRevoke

    @Test
    fun `merge - autoRevoke is unspecified when no layer sets it`() {
        assertNull(merged(later = null, earlier = null))
    }

    @Test
    fun `merge - a later layer can turn autoRevoke on`() {
        assertEquals(true, merged(later = true, earlier = null))
        assertEquals(true, merged(later = true, earlier = false))
    }

    @Test
    fun `merge - a later layer can turn autoRevoke off`() {
        assertEquals(false, merged(later = false, earlier = true))
    }

    @Test
    fun `merge - a later layer which does not mention autoRevoke keeps the earlier value`() {
        assertEquals(true, merged(later = null, earlier = true))
        assertEquals(false, merged(later = null, earlier = false))
    }
}
