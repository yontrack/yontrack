package net.nemerosa.ontrack.extension.findings.validation

import net.nemerosa.ontrack.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #1944 - a `security-findings` validation stamp honours `warningPassesAutoPromotion` as CHML does.
 */
@AsAdminTest
class FindingsAutoPromotionWarningIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var findingsValidationDataType: FindingsValidationDataType

    @Test
    fun `With the flag on, a WARNING run on a required findings stamp promotes`() {
        project {
            branch {
                val vs = findingsStamp(warningPassesAutoPromotion = true)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs)
                build {
                    val run = findingsWarning(vs)
                    assertEquals(ValidationRunStatusID.WARNING, run.lastStatusId)
                    assertPromoted(this, pl)
                }
            }
        }
    }

    @Test
    fun `With the flag off, a WARNING run on a required findings stamp does not promote`() {
        project {
            branch {
                val vs = findingsStamp(warningPassesAutoPromotion = false)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs)
                build {
                    val run = findingsWarning(vs)
                    assertEquals(ValidationRunStatusID.WARNING, run.lastStatusId)
                    assertNotPromoted(this, pl)
                }
            }
        }
    }

    @Test
    fun `With the flag on, a later WARNING run does not revoke the promotion`() {
        project {
            branch {
                val vs = findingsStamp(warningPassesAutoPromotion = true)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs, autoRevoke = true)
                build {
                    validate(vs)
                    assertPromoted(this, pl)
                    findingsWarning(vs)
                    assertPromoted(this, pl)
                }
            }
        }
    }

    @Test
    fun `With the flag off, a later WARNING run revokes the promotion`() {
        project {
            branch {
                val vs = findingsStamp(warningPassesAutoPromotion = false)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs, autoRevoke = true)
                build {
                    validate(vs)
                    assertPromoted(this, pl)
                    findingsWarning(vs)
                    assertNotPromoted(this, pl)
                }
            }
        }
    }

    private fun Branch.findingsStamp(warningPassesAutoPromotion: Boolean) = validationStamp(
        validationDataTypeConfig = findingsValidationDataType.config(
            CHMLValidationDataTypeConfig(
                warningLevel = CHMLLevel(CHML.HIGH, 1),
                failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                warningPassesAutoPromotion = warningPassesAutoPromotion,
            )
        )
    )

    /**
     * Run whose status is computed as `WARNING` from the counts of its findings
     */
    private fun Build.findingsWarning(vs: ValidationStamp): ValidationRun =
        validateWithData(
            validationStamp = vs,
            validationDataTypeId = FindingsValidationDataType::class.java.name,
            validationRunData = FindingsValidationDataTypeData(
                levels = mapOf(CHML.CRITICAL to 0, CHML.HIGH to 1, CHML.MEDIUM to 0, CHML.LOW to 0),
            ),
        )

    private fun Branch.autoPromotion(
        promotionLevel: PromotionLevel,
        vs: ValidationStamp,
        autoRevoke: Boolean = false,
    ) {
        setProperty(
            promotionLevel,
            AutoPromotionPropertyType::class.java,
            AutoPromotionProperty(
                validationStamps = listOf(vs),
                include = "",
                exclude = "",
                promotionLevels = emptyList(),
                autoRevoke = autoRevoke,
            )
        )
    }

    private fun promotionRuns(build: Build, promotionLevel: PromotionLevel): List<PromotionRun> =
        structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel)

    private fun assertPromoted(build: Build, promotionLevel: PromotionLevel) {
        assertTrue(
            promotionRuns(build, promotionLevel).isNotEmpty(),
            "Build ${build.name} is promoted to ${promotionLevel.name}"
        )
    }

    private fun assertNotPromoted(build: Build, promotionLevel: PromotionLevel) {
        assertTrue(
            promotionRuns(build, promotionLevel).isEmpty(),
            "Build ${build.name} is not promoted to ${promotionLevel.name}"
        )
    }
}
