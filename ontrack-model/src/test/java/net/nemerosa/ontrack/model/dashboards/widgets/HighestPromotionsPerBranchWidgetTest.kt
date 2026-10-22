package net.nemerosa.ontrack.model.dashboards.widgets

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HighestPromotionsPerBranchWidgetTest {

    @Test
    fun `a configuration saved before the project and sort options loads with their defaults`() {
        val config = mapOf(
            "format" to "LADDER",
            "branches" to listOf(mapOf("project" to "service", "branch" to "main")),
        ).asJson().parse<HighestPromotionsPerBranchWidgetConfig>()
        assertEquals(
            HighestPromotionsPerBranchWidgetConfig(
                format = HighestPromotionsPerBranchWidgetFormat.LADDER,
                showProject = true,
                sort = HighestPromotionsPerBranchWidgetSort.LAST_ACTIVITY,
                branches = listOf(HighestPromotionsPerBranchWidgetConfigBranch(project = "service", branch = "main")),
            ),
            config,
        )
    }

    @Test
    fun `the project can be hidden and the branches sorted by name`() {
        val config = mapOf(
            "showProject" to false,
            "sort" to "NAME",
        ).asJson().parse<HighestPromotionsPerBranchWidgetConfig>()
        assertFalse(config.showProject)
        assertEquals(HighestPromotionsPerBranchWidgetSort.NAME, config.sort)
    }
}
