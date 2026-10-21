package net.nemerosa.ontrack.kdsl.acceptance.tests.general

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.spec.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.kdsl.spec.extension.general.ON_PROMOTION_VALIDATION_DATA_TYPE
import net.nemerosa.ontrack.kdsl.spec.extension.general.autoPromotion
import net.nemerosa.ontrack.kdsl.spec.extension.general.createOnPromotionValidationStamp
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #2101 - validating a build when the build it links to reaches a promotion.
 */
class ACCOnPromotionValidation : AbstractACCDSLTestSupport() {

    @Test
    fun `A consumer is promoted to SILVER once its dependencies are SILVER`() {
        // Dependencies
        val b = project { branch { promotion("SILVER"); this } }
        val c = project { branch { promotion("SILVER"); this } }
        val b1 = b.build("1.0") { this }
        val c1 = c.build("2.0") { this }
        // Consumer
        project {
            branch {
                val tests = validationStamp("tests")
                val bSilver = createOnPromotionValidationStamp(
                    name = "B-SILVER",
                    project = b.project.name,
                    promotion = "SILVER",
                )
                val cSilver = createOnPromotionValidationStamp(
                    name = "C-SILVER",
                    project = c.project.name,
                    promotion = "SILVER",
                )
                val silver = promotion("SILVER")
                silver.autoPromotion = AutoPromotionProperty(
                    validationStamps = listOf(tests.id, bSilver.id, cSilver.id),
                )
                build("1") {
                    linkTo(b1)
                    linkTo(c1)
                    validate(tests.name, status = "PASSED")
                    assertTrue(getPromotionRunsForPromotionLevel(silver.name).isEmpty(), "Not SILVER yet")
                    // B reaches SILVER
                    b1.promote("SILVER")
                    assertTrue(getPromotionRunsForPromotionLevel(silver.name).isEmpty(), "Not SILVER yet")
                    // C reaches SILVER
                    c1.promote("SILVER")
                    assertTrue(getPromotionRunsForPromotionLevel(silver.name).isNotEmpty(), "SILVER")
                    // The run carries the linked build
                    val run = getValidationRuns(bSilver.name).first()
                    assertEquals("PASSED", run.lastStatus.id)
                    val data = assertNotNull(run.data)
                    assertEquals(ON_PROMOTION_VALIDATION_DATA_TYPE, data.type)
                    assertEquals(b.project.name, data.data.path("project").asText())
                    assertEquals(b1.name, data.data.path("build").asText())
                }
            }
        }
    }
}
