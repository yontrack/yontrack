package net.nemerosa.ontrack.kdsl.acceptance.tests.general

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.spec.ReadinessItem
import net.nemerosa.ontrack.kdsl.spec.ReadinessKind
import net.nemerosa.ontrack.kdsl.spec.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.kdsl.spec.extension.general.autoPromotion
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #2022 - what a build still lacks to reach a promotion level.
 */
class ACCBuildReadiness : AbstractACCDSLTestSupport() {

    @Test
    fun `Readiness for an auto promotion follows the validations of the build`() {
        project {
            branch {
                val compile = validationStamp()
                val tests = validationStamp()
                val silver = promotion()
                silver.autoPromotion = AutoPromotionProperty(
                    validationStamps = listOf(compile.id, tests.id),
                )
                build {
                    // One stamp validated: the other one is missing
                    validate(compile.name, status = "PASSED")
                    val readiness = readiness(promotionLevel = silver.name)
                    assertFalse(readiness.ready, "Not ready yet")
                    assertEquals(
                        listOf(ReadinessItem(ReadinessKind.VALIDATION, tests.name, "Not validated")),
                        readiness.missing
                    )
                    // Both validated: ready
                    validate(tests.name, status = "PASSED")
                    val ready = readiness(promotionLevel = silver.name)
                    assertTrue(ready.ready, "Ready")
                    assertTrue(ready.missing.isEmpty(), "Nothing missing")
                }
            }
        }
    }

    @Test
    fun `Readiness for a level without auto promotion needs a person`() {
        project {
            branch {
                val gold = promotion()
                build {
                    val readiness = readiness(promotionLevel = gold.name)
                    assertFalse(readiness.ready)
                    assertEquals(listOf(ReadinessKind.MANUAL), readiness.missing.map { it.kind })
                    promote(gold.name)
                    assertTrue(readiness(promotionLevel = gold.name).ready, "Ready once promoted")
                }
            }
        }
    }
}
