package net.nemerosa.ontrack.model.structure

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProjectEntityTypeTest {

    @Test
    fun `Var names`() {
        assertEquals("project", ProjectEntityType.PROJECT.varName)
        assertEquals("branch", ProjectEntityType.BRANCH.varName)
        assertEquals("build", ProjectEntityType.BUILD.varName)
        assertEquals("validationStamp", ProjectEntityType.VALIDATION_STAMP.varName)
        assertEquals("validationRun", ProjectEntityType.VALIDATION_RUN.varName)
        assertEquals("promotionLevel", ProjectEntityType.PROMOTION_LEVEL.varName)
        assertEquals("promotionRun", ProjectEntityType.PROMOTION_RUN.varName)
    }

    @Test
    fun `Type names`() {
        assertEquals("Project", ProjectEntityType.PROJECT.typeName)
        assertEquals("Branch", ProjectEntityType.BRANCH.typeName)
        assertEquals("Build", ProjectEntityType.BUILD.typeName)
        assertEquals("ValidationStamp", ProjectEntityType.VALIDATION_STAMP.typeName)
        assertEquals("ValidationRun", ProjectEntityType.VALIDATION_RUN.typeName)
        assertEquals("PromotionLevel", ProjectEntityType.PROMOTION_LEVEL.typeName)
        assertEquals("PromotionRun", ProjectEntityType.PROMOTION_RUN.typeName)
    }

}