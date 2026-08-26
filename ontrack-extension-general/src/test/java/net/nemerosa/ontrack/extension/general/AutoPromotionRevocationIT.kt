package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #1639 - opt-in revocation of an auto promotion when one of its prerequisites is no longer valid.
 */
@AsAdminTest
class AutoPromotionRevocationIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var testEventListener: AutoPromotionRevocationTestEventListener

    @BeforeEach
    fun before() {
        testEventListener.reset()
    }

    /**
     * `PASSED` and `FIXED` are terminal statuses, so the last run of a stamp which granted the promotion
     * can never be flipped to a non-passed status. The status-change trigger is therefore reachable only
     * when the promotion did not come from that run - here, a manual promotion over a failed validation.
     */
    @Test
    fun `A status change to a non-passed status on a required validation revokes the promotion`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                autoPromotion(pl, validationStamps = listOf(vs), autoRevoke = true)
                build("1") {
                    val run = validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    promote(pl, description = "Manual")
                    assertPromoted(this, pl)
                    // Status change, still on a non-passed status
                    run.validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                    assertNotPromoted(this, pl)
                    assertEquals(listOf(pl.id()), testEventListener.revokedPromotionLevelIds(this))
                }
            }
        }
    }

    @Test
    fun `A new failing run on a stamp which already passed revokes the promotion`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                autoPromotion(pl, validationStamps = listOf(vs), autoRevoke = true)
                build("1") {
                    validate(vs)
                    assertPromoted(this, pl)
                    // A re-run fails - `isValidationRunPassed` looks at the last run only, so the
                    // prerequisite is no longer satisfied even though the earlier passing run still exists
                    validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    assertNotPromoted(this, pl)
                }
            }
        }
    }

    @Test
    fun `Revoking a promotion cascades to the promotions which required it`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val bronze = promotionLevel("BRONZE")
                val silver = promotionLevel("SILVER")
                autoPromotion(bronze, validationStamps = listOf(vs), autoRevoke = true)
                autoPromotion(silver, promotionLevels = listOf(bronze), autoRevoke = true)
                build("1") {
                    validate(vs)
                    assertPromoted(this, bronze)
                    assertPromoted(this, silver)
                    // A re-run fails: BRONZE goes, and SILVER goes with it
                    validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    assertNotPromoted(this, bronze)
                    assertNotPromoted(this, silver)
                    assertEquals(
                        setOf(bronze.id(), silver.id()),
                        testEventListener.revokedPromotionLevelIds(this).toSet()
                    )
                }
            }
        }
    }

    @Test
    fun `The cascade only fires once the last run of the prerequisite is gone`() {
        project {
            branch {
                val bronze = promotionLevel("BRONZE")
                val silver = promotionLevel("SILVER")
                autoPromotion(silver, promotionLevels = listOf(bronze), autoRevoke = true)
                build("1") {
                    // Two runs on BRONZE - the auto promotion of SILVER only fires once
                    val firstBronze = promote(bronze)
                    val secondBronze = promote(bronze)
                    assertPromoted(this, silver)
                    // Deleting the first run leaves BRONZE granted, so SILVER stands
                    deletePromotionRun(firstBronze)
                    assertPromoted(this, silver)
                    // Deleting the last one revokes SILVER
                    deletePromotionRun(secondBronze)
                    assertNotPromoted(this, silver)
                }
            }
        }
    }

    @Test
    fun `Deleting a promotion by hand cascades to the promotions which required it`() {
        project {
            branch {
                val bronze = promotionLevel("BRONZE")
                val silver = promotionLevel("SILVER")
                autoPromotion(silver, promotionLevels = listOf(bronze), autoRevoke = true)
                build("1") {
                    val run = promote(bronze)
                    assertPromoted(this, silver)
                    // `DELETE_PROMOTION_RUN` is source-agnostic: a prerequisite is no longer valid whoever
                    // made it untrue
                    deletePromotionRun(run)
                    assertNotPromoted(this, silver)
                }
            }
        }
    }

    @Test
    fun `Every run of the promotion level is revoked, including the manual ones`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                autoPromotion(pl, validationStamps = listOf(vs), autoRevoke = true)
                build("1") {
                    validate(vs)
                    // A second, manual run on the same promotion level
                    promote(pl, description = "Manual")
                    assertEquals(2, promotionRuns(this, pl).size)
                    // Opting into autoRevoke declares the promotion level fully auto-managed
                    validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    assertNotPromoted(this, pl)
                }
            }
        }
    }

    /**
     * Deleting a run posts its event before the run leaves the database, so a promotion level carrying
     * several runs used to recurse forever - each nested call still saw the run the outer one was deleting.
     */
    @Test
    fun `Revoking a promotion level which carries several runs terminates`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                autoPromotion(pl, validationStamps = listOf(vs), autoRevoke = true)
                build("1") {
                    validate(vs)
                    promote(pl, description = "Manual 1")
                    promote(pl, description = "Manual 2")
                    assertEquals(3, promotionRuns(this, pl).size)
                    validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    assertNotPromoted(this, pl)
                    // ... and the revocation is reported once, not once per run
                    assertEquals(listOf(pl.id()), testEventListener.revokedPromotionLevelIds(this))
                }
            }
        }
    }

    @Test
    fun `Without the flag nothing is revoked`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                autoPromotion(pl, validationStamps = listOf(vs), autoRevoke = false)
                build("1") {
                    validate(vs)
                    assertPromoted(this, pl)
                    validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    assertPromoted(this, pl)
                    assertTrue(testEventListener.revokedPromotionLevelIds(this).isEmpty())
                }
            }
        }
    }

    @Test
    fun `Deleting a required validation stamp does not revoke`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                autoPromotion(pl, validationStamps = listOf(vs), autoRevoke = true)
                build("1") {
                    validate(vs)
                    assertPromoted(this, pl)
                    // The existing listener prunes the stamp out of the property: the requirement ceases to
                    // exist, nothing became invalid
                    structureService.deleteValidationStamp(vs.id)
                    assertPromoted(this, pl)
                }
            }
        }
    }

    @Test
    fun `Deleting a required promotion level does not revoke`() {
        project {
            branch {
                val bronze = promotionLevel("BRONZE")
                val silver = promotionLevel("SILVER")
                autoPromotion(silver, promotionLevels = listOf(bronze), autoRevoke = true)
                build("1") {
                    promote(bronze)
                    assertPromoted(this, silver)
                    structureService.deletePromotionLevel(bronze.id)
                    assertPromoted(this, silver)
                }
            }
        }
    }

    @Test
    fun `A failing revocation does not roll back the validation status change`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                autoPromotion(pl, validationStamps = listOf(vs), autoRevoke = true)
                build("1") {
                    val run = validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    promote(pl, description = "Manual")
                    assertPromoted(this, pl)
                    // Recording validation results is the primary duty of the system: a revocation which
                    // blows up must not fail the CI job's validation call
                    testEventListener.failOnPromotionRunDeletion = true
                    run.validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                    // The status change went through...
                    assertEquals(
                        ValidationRunStatusID.STATUS_INVESTIGATING.id,
                        structureService.getValidationRun(run.id).lastStatus.statusID.id,
                    )
                    // ... and the revocation was simply skipped
                    assertPromoted(this, pl)
                    assertTrue(testEventListener.revokedPromotionLevelIds(this).isEmpty())
                }
            }
        }
    }

    @Test
    fun `A promotion revoked on a failure is granted again when the validation passes again`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                autoPromotion(pl, validationStamps = listOf(vs), autoRevoke = true)
                build("1") {
                    validate(vs)
                    assertPromoted(this, pl)
                    // Revoked...
                    validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    assertNotPromoted(this, pl)
                    // ... granted again, and it settles there rather than oscillating
                    validate(vs)
                    assertEquals(
                        1, promotionRuns(this, pl).size,
                        "Exactly one promotion run, and no promote/revoke loop"
                    )
                    assertEquals(1, testEventListener.revokedPromotionLevelIds(this).size)
                }
            }
        }
    }

    private fun Branch.autoPromotion(
        promotionLevel: PromotionLevel,
        validationStamps: List<ValidationStamp> = emptyList(),
        promotionLevels: List<PromotionLevel> = emptyList(),
        autoRevoke: Boolean,
    ) {
        setProperty(
            promotionLevel,
            AutoPromotionPropertyType::class.java,
            AutoPromotionProperty(
                validationStamps = validationStamps,
                include = "",
                exclude = "",
                promotionLevels = promotionLevels,
                autoRevoke = autoRevoke,
            )
        )
    }

    private fun deletePromotionRun(run: PromotionRun) {
        structureService.deletePromotionRun(run.id)
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
        assertFalse(
            promotionRuns(build, promotionLevel).isNotEmpty(),
            "Build ${build.name} is no longer promoted to ${promotionLevel.name}"
        )
    }
}
