package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.general.validation.*
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #1943 - a CHML validation stamp can opt in to having a `WARNING` run count as passed for the auto
 * promotion.
 */
@AsAdminTest
class AutoPromotionCHMLWarningIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var chmlValidationDataType: CHMLValidationDataType

    @Autowired
    private lateinit var autoPromotionConditionsService: AutoPromotionConditionsService

    @Autowired
    private lateinit var validationRunService: ValidationRunService

    @Test
    fun `With the flag on, a WARNING run on a required CHML stamp promotes`() {
        project {
            branch {
                val vs = chmlStamp(warningPassesAutoPromotion = true)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs)
                build("1") {
                    val run = chmlWarning(vs)
                    assertEquals(ValidationRunStatusID.WARNING, run.lastStatusId)
                    assertPromoted(this, pl)
                }
            }
        }
    }

    @Test
    fun `With the flag off, a WARNING run on a required CHML stamp does not promote`() {
        project {
            branch {
                val vs = chmlStamp(warningPassesAutoPromotion = false)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs)
                build("1") {
                    val run = chmlWarning(vs)
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
                val vs = chmlStamp(warningPassesAutoPromotion = true)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs, autoRevoke = true)
                build("1") {
                    validate(vs)
                    assertPromoted(this, pl)
                    chmlWarning(vs)
                    assertPromoted(this, pl)
                }
            }
        }
    }

    @Test
    fun `With the flag off, a later WARNING run revokes the promotion`() {
        project {
            branch {
                val vs = chmlStamp(warningPassesAutoPromotion = false)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs, autoRevoke = true)
                build("1") {
                    validate(vs)
                    assertPromoted(this, pl)
                    chmlWarning(vs)
                    assertNotPromoted(this, pl)
                }
            }
        }
    }

    @Test
    fun `With the flag on, a WARNING run moved to another status revokes the promotion`() {
        project {
            branch {
                val vs = chmlStamp(warningPassesAutoPromotion = true)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs, autoRevoke = true)
                build("1") {
                    val run = chmlWarning(vs)
                    assertPromoted(this, pl)
                    // Only a last status of exactly WARNING is accepted
                    run.validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                    assertNotPromoted(this, pl)
                }
            }
        }
    }

    @Test
    fun `A WARNING run on a stamp without any data type does not count as passed`() {
        project {
            branch {
                val vs = validationStamp()
                build {
                    validate(vs, ValidationRunStatusID.STATUS_WARNING)
                    assertFalse(validationRunService.isValidationRunPassedForAutoPromotion(this, vs))
                    validate(vs)
                    assertTrue(validationRunService.isValidationRunPassedForAutoPromotion(this, vs))
                }
            }
        }
    }

    @Test
    fun `No run at all does not count as passed for the auto promotion`() {
        project {
            branch {
                val vs = chmlStamp(warningPassesAutoPromotion = true)
                build {
                    assertFalse(validationRunService.isValidationRunPassedForAutoPromotion(this, vs))
                }
            }
        }
    }

    @Test
    fun `The conditions report a WARNING run as passed with the flag on`() {
        project {
            branch {
                val vs = chmlStamp(warningPassesAutoPromotion = true)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs)
                build {
                    chmlWarning(vs)
                    val conditions = autoPromotionConditionsService.getBuildConditions(this, pl)
                    assertNotNull(conditions) {
                        val condition = it.validationStamps.single()
                        assertEquals(ValidationRunStatusID.WARNING, condition.lastRun?.lastStatusId)
                        assertTrue(condition.passed)
                    }
                }
            }
        }
    }

    @Test
    fun `The conditions report a WARNING run as not passed with the flag off`() {
        project {
            branch {
                val vs = chmlStamp(warningPassesAutoPromotion = false)
                val pl = promotionLevel("PL")
                autoPromotion(pl, vs)
                build {
                    chmlWarning(vs)
                    val conditions = autoPromotionConditionsService.getBuildConditions(this, pl)
                    assertNotNull(conditions) {
                        assertFalse(it.validationStamps.single().passed)
                    }
                }
            }
        }
    }

    private fun Branch.chmlStamp(warningPassesAutoPromotion: Boolean) = validationStamp(
        validationDataTypeConfig = chmlValidationDataType.config(
            CHMLValidationDataTypeConfig(
                warningLevel = CHMLLevel(CHML.HIGH, 1),
                failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                warningPassesAutoPromotion = warningPassesAutoPromotion,
            )
        )
    )

    /**
     * Run whose status is computed as `WARNING` from its data
     */
    private fun Build.chmlWarning(vs: ValidationStamp): ValidationRun =
        validateWithData(
            validationStamp = vs,
            validationDataTypeId = CHMLValidationDataType::class.java.name,
            validationRunData = CHMLValidationDataTypeData(mapOf(CHML.HIGH to 1)),
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
