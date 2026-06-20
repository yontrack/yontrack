package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.PromotionLevelField
import net.nemerosa.ontrack.model.structure.PromotionLevelFieldType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromotionLevelFieldsDecoratorIT : AbstractGeneralExtensionTestSupport() {

    @Autowired
    private lateinit var decorator: PromotionLevelFieldsDecorator

    @Test
    fun `No decoration when promotion level has no fields`() {
        project {
            branch {
                val pl = promotionLevel()
                val decorations = decorator.getDecorations(pl)
                assertTrue(decorations.isEmpty(), "Expected no decorations when no fields are defined")
            }
        }
    }

    @Test
    fun `Decoration present when promotion level has fields`() {
        project {
            branch {
                val pl = promotionLevel()
                structureService.setPromotionLevelFields(
                    pl.id,
                    listOf(
                        PromotionLevelField(
                            id = 0,
                            name = "ticket",
                            displayName = "Ticket",
                            description = null,
                            type = PromotionLevelFieldType.TEXT,
                            required = false,
                            options = emptyList(),
                            position = 0,
                        )
                    )
                )
                val decorations = decorator.getDecorations(pl)
                assertEquals(1, decorations.size)
                assertEquals(true, decorations[0].data)
            }
        }
    }
}
