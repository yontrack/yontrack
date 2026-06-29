package net.nemerosa.ontrack.service.templating

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.PromotionLevelField
import net.nemerosa.ontrack.model.structure.PromotionLevelFieldType
import net.nemerosa.ontrack.model.structure.fieldValue
import net.nemerosa.ontrack.model.templating.TemplatingService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class PromotionRunFieldTemplatingSourceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var templatingService: TemplatingService

    @Test
    @AsAdminTest
    fun `Accessing a promotion run field value`() {
        project {
            branch {
                val pl = promotionLevel(
                    fields = listOf(
                        PromotionLevelField(
                            name = "report",
                            displayName = "Report",
                            description = "Link to the report",
                            type = PromotionLevelFieldType.LINK,
                            required = true,
                        ),
                    )
                )
                build {
                    val run = promote(
                        promotionLevel = pl,
                        fieldValues = listOf(fieldValue("report", "https://self.dev.yontrack.com/report/1234567890")),
                    )
                    assertEquals(
                        "Link to report: https://self.dev.yontrack.com/report/1234567890",
                        templatingService.render(
                            template = $$"Link to report: ${promotionRun.field?name=report}",
                            context = mapOf("promotionRun" to run),
                            renderer = PlainEventRenderer.INSTANCE,
                        )
                    )
                }
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Accessing an undefined promotion run field value returns empty string`() {
        project {
            branch {
                val pl = promotionLevel()
                build {
                    val run = promote(promotionLevel = pl)
                    assertEquals(
                        "Link to report: ",
                        templatingService.render(
                            template = $$"Link to report: ${promotionRun.field?name=report}",
                            context = mapOf("promotionRun" to run),
                            renderer = PlainEventRenderer.INSTANCE,
                        )
                    )
                }
            }
        }
    }

}