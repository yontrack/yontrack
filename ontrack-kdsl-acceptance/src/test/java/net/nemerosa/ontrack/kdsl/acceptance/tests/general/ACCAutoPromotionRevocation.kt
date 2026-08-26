package net.nemerosa.ontrack.kdsl.acceptance.tests.general

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.spec.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.kdsl.spec.extension.general.autoPromotion
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #1639 - opt-in revocation of an auto promotion when a prerequisite is no longer valid.
 */
class ACCAutoPromotionRevocation : AbstractACCDSLTestSupport() {

    @Test
    fun `Auto promotion property carries the auto revoke flag`() {
        project {
            branch {
                val vs = validationStamp()
                val pl = promotion()
                pl.autoPromotion = AutoPromotionProperty(
                    validationStamps = listOf(vs.id),
                    autoRevoke = true,
                )
                val property = pl.autoPromotion
                assertEquals(listOf(vs.id), property?.validationStamps)
                assertTrue(property?.autoRevoke == true, "Auto revoke is set")
            }
        }
    }

    @Test
    fun `Auto revoke defaults to false when not set`() {
        project {
            branch {
                val vs = validationStamp()
                val pl = promotion()
                pl.autoPromotion = AutoPromotionProperty(validationStamps = listOf(vs.id))
                assertFalse(pl.autoPromotion?.autoRevoke ?: true, "Auto revoke defaults to false")
            }
        }
    }

    @Test
    fun `A failing validation revokes the promotion`() {
        project {
            branch {
                val vs = validationStamp()
                val pl = promotion()
                pl.autoPromotion = AutoPromotionProperty(
                    validationStamps = listOf(vs.id),
                    autoRevoke = true,
                )
                build {
                    // The validation passes: the build is promoted
                    validate(vs.name, status = "PASSED")
                    assertTrue(
                        getPromotionRunsForPromotionLevel(pl.name).isNotEmpty(),
                        "Build is promoted"
                    )
                    // A re-run fails: the promotion is revoked
                    validate(vs.name, status = "FAILED")
                    assertTrue(
                        getPromotionRunsForPromotionLevel(pl.name).isEmpty(),
                        "Build is no longer promoted"
                    )
                }
            }
        }
    }

    @Test
    fun `Without the flag a failing validation leaves the promotion alone`() {
        project {
            branch {
                val vs = validationStamp()
                val pl = promotion()
                pl.autoPromotion = AutoPromotionProperty(
                    validationStamps = listOf(vs.id),
                    autoRevoke = false,
                )
                build {
                    validate(vs.name, status = "PASSED")
                    validate(vs.name, status = "FAILED")
                    assertTrue(
                        getPromotionRunsForPromotionLevel(pl.name).isNotEmpty(),
                        "Build is still promoted"
                    )
                }
            }
        }
    }
}
