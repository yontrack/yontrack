package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.structure.BranchCloneRequest
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.CopyService
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@AsAdminTest
class AutoPromotionPropertyIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var copyService: CopyService

    @Autowired
    private lateinit var autoPromotionPropertyType: AutoPromotionPropertyType

    /**
     * #850 - When the auto promotion is empty, do not promote automatically
     */
    @Test
    fun `Empty auto promotion property does not trigger promotion`() {
        project {
            branch {
                val vs = validationStamp()
                val pl = promotionLevel {
                    setProperty(
                        this,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(emptyList(), "", "", emptyList())
                    )
                }
                build {
                    // Validation
                    validate(vs)
                    // Checks that the build has not been promoted
                    val runs = structureService.getPromotionRunsForBuildAndPromotionLevel(this, pl)
                    assertTrue(runs.isEmpty(), "Build was not promoted")
                }
            }
        }
    }

    /**
     * This test checks that a promotion level is not attributed twice on validation upon auto promotion.
     */
    @Test
    fun `Auto promotion - only once`() {
        // Creation of a branch
        project {
            branch {
                // Creation of validation stamps
                val vs1 = validationStamp("CI.1")
                val vs2 = validationStamp("CI.2")
                // Creation of one promotion level
                val promotionLevel = promotionLevel("PL")
                // Sets the auto promotion
                setProperty(
                        promotionLevel,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(emptyList(), "CI.*", "", emptyList())
                )
                // Creates a build
                build("1") {
                    // Validates the build once
                    validate(vs1)
                    validate(vs2)
                    // Checks the promotion of the build
                    asUser().withView(branch).call {
                        val runs = structureService.getPromotionRunsForBuild(id)
                        assertEquals(
                                listOf("PL"),
                                runs.map { it.promotionLevel.name }
                        )
                    }
                    // Validates the build a second time
                    validate(vs1)
                    // Checks the promotion of the build (only one)
                    asUser().withView(branch).call {
                        val runs = structureService.getPromotionRunsForBuild(id)
                        assertEquals(
                                listOf("PL"),
                                runs.map { it.promotionLevel.name }
                        )
                    }
                }
            }
        }
    }

    /**
     * This test checks that whenever a validation stamp, which was part of an auto promotion configuration,
     * is deleted, it is automatically removed from the auto promotion configuration.
     */
    @Test
    fun `Auto promotion - auto configuration on validation stamp deletion`() {
        // Creation of a branch
        project {
            branch {
                // Creation of two validation stamps
                val vs1 = validationStamp("VS1")
                val vs2 = validationStamp("VS2")
                // Creation of one promotion level
                val promotionLevel = promotionLevel("PL")
                // Sets the auto promotion
                setProperty(
                        promotionLevel,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(listOf(vs1, vs2), "", "", emptyList())
                )
                // Deletes a validation stamp
                asUser().with(this, ProjectEdit::class.java).with(this, ProjectEdit::class.java).call {
                    structureService.deleteValidationStamp(vs1.id)
                }
                // Gets the auto promotion configuration
                val property: AutoPromotionProperty? = getProperty(promotionLevel, AutoPromotionPropertyType::class.java)
                // Checks it does not contain the VS1 any longer
                assertNotNull(property) {
                    assertEquals(
                            listOf("VS2"),
                            it.validationStamps.map { it.name }
                    )
                }
            }
        }
    }

    /**
     * Regression test for #290
     */
    @Test
    fun `Branch cloning with auto promotion`() {
        // Creates a promotion level
        project {
            branch {
                val promotionLevel = promotionLevel("PL")
                // Creates a validation stamp
                val vs1 = validationStamp("VS1")
                validationStamp("VS2")
                // Auto promotion
                setProperty(
                        promotionLevel,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(listOf(vs1), "", "", emptyList())
                )
                // Cloning the branch
                val clonedBranchName = TestUtils.uid("B")
                val clonedBranch = asUser().with(this, ProjectEdit::class.java).call {
                    copyService.cloneBranch(
                            this,
                            BranchCloneRequest(
                                    clonedBranchName,
                                    emptyList()
                            )
                    )
                }
                assertEquals(clonedBranchName, clonedBranch.name)
                // Gets the cloned promotion level
                val clonedPromotionLevel: PromotionLevel? = asUser().withView(clonedBranch).call {
                    structureService.findPromotionLevelByName(
                            clonedBranch.project.name,
                            clonedBranch.name,
                            promotionLevel.name
                    ).orElse(null)
                }

                assertNotNull(clonedPromotionLevel) {
                    // Gets the auto validation property for the cloned branch
                    val property: AutoPromotionProperty? = asUser().withView(it).call {
                        getProperty(it, AutoPromotionPropertyType::class.java)
                    }
                    assertNotNull(property) {
                        assertEquals(
                                listOf("VS1"),
                                it.validationStamps.map { it.name }
                        )
                    }
                }
            }
        }
    }

    /**
     * Backward compatibility test from 3.42 when `promotionLevels` was not set.
     */
    @Test
    fun `Backward compatibility parsing from 3-42`() {
        project {
            branch {
                val vs1 = validationStamp()
                val vs2 = validationStamp()
                val node = mapOf(
                        "validationStamps" to listOf(vs1.id(), vs2.id()),
                        "include" to "INCLUDE.*",
                        "exclude" to "EXCLUDE.*"
                ).asJson()
                val property = autoPromotionPropertyType.fromStorage(node)
                assertEquals(listOf(vs1, vs2), property.validationStamps)
                assertEquals("INCLUDE.*", property.include)
                assertEquals("EXCLUDE.*", property.exclude)
                assertEquals(emptyList(), property.promotionLevels)
            }
        }
    }

    @Test
    fun `Auto promotion based on another promotion`() {
        project {
            branch {
                val silver = promotionLevel()
                val gold = promotionLevel()
                // Auto promotion
                setProperty(
                        gold,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(emptyList(), "", "", listOf(silver))
                )
                // Testing on a build
                build {
                    // Promotes to silver
                    promote(silver)
                    // ... checks it has been promoted to gold
                    checkBuildIsPromoted(this, gold)
                }
            }
        }
    }

    @Test
    fun `Auto promotion based on another promotion and validation stamps`() {
        project {
            branch {
                val silver = promotionLevel()
                val gold = promotionLevel()
                val quality = validationStamp()
                val security = validationStamp()
                // Auto promotion
                setProperty(
                        gold,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(listOf(quality, security), "", "", listOf(silver))
                )
                // Testing on a build
                build {
                    // Performs validations
                    validate(quality)
                    validate(security)
                    // ... checks it's not promoted to gold yet
                    checkBuildIsNotPromoted(this, gold)
                    // Promotes to silver
                    promote(silver)
                    // ... checks it has been promoted to gold
                    checkBuildIsPromoted(this, gold)
                }
            }
        }
    }

    @Test
    fun `Auto promotion based on another promotion and validation stamps with promotion first`() {
        project {
            branch {
                val silver = promotionLevel()
                val gold = promotionLevel()
                val quality = validationStamp()
                val security = validationStamp()
                // Auto promotion
                setProperty(
                        gold,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(listOf(quality, security), "", "", listOf(silver))
                )
                // Testing on a build
                build {
                    // Promotes to silver
                    promote(silver)
                    // ... checks it's not promoted to gold yet
                    checkBuildIsNotPromoted(this, gold)
                    // Performs validations
                    validate(quality)
                    validate(security)
                    // ... checks it has been promoted to gold
                    checkBuildIsPromoted(this, gold)
                }
            }
        }
    }

    @Test
    fun `Auto promotion based on same promotion has no effect`() {
        project {
            branch {
                val gold = promotionLevel()
                // Auto promotion
                setProperty(
                        gold,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(emptyList(), "", "", listOf(gold))
                )
                // Testing on a build
                build {
                    // Promotes to gold
                    promote(gold)
                    // ... checks it has been promoted to gold only once
                    asUserWithView {
                        val runs = structureService.getPromotionRunsForBuildAndPromotionLevel(this, gold)
                        assertEquals(1, runs.size)
                    }
                }
            }
        }
    }

    @Test
    fun `Auto promotion adjusted on promotion deletion`() {
        project {
            branch {
                // Creation of two promotion levels
                val iron = promotionLevel()
                val silver = promotionLevel()
                val gold = promotionLevel()
                // Sets the auto promotion
                setProperty(
                        gold,
                        AutoPromotionPropertyType::class.java,
                        AutoPromotionProperty(emptyList(), "", "", listOf(iron, silver))
                )
                // Deletes the promotion level depended upon
                asUser().with(this, ProjectEdit::class.java).with(this, ProjectEdit::class.java).call {
                    structureService.deletePromotionLevel(silver.id)
                }
                // Gets the auto promotion configuration
                val property: AutoPromotionProperty? = getProperty(gold, AutoPromotionPropertyType::class.java)
                // Checks it does not contain the silver promotion any longer
                assertNotNull(property) {
                    assertEquals(
                            listOf(iron.id),
                            it.promotionLevels.map { pl -> pl.id }
                    )
                }
            }
        }
    }

    /**
     * #1629 - a validation which was failed and is later fixed must count as passed
     * and grant the auto promotion.
     */
    @Test
    fun `Auto promotion granted when a failed validation becomes fixed`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                setProperty(
                    pl,
                    AutoPromotionPropertyType::class.java,
                    AutoPromotionProperty(listOf(vs), "", "", emptyList())
                )
                build("1") {
                    // The validation fails: no promotion
                    val run = validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    checkBuildIsNotPromoted(this, pl)
                    // The validation is being investigated: still no promotion
                    val investigated =
                        run.validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                    checkBuildIsNotPromoted(this, pl)
                    // The validation is fixed: the promotion is granted
                    investigated.validationStatus(ValidationRunStatusID.STATUS_FIXED, "Fixed")
                    checkBuildIsPromoted(this, pl)
                }
            }
        }
    }

    /**
     * #1629 - a fixed validation is not enough when another prerequisite is not met.
     */
    @Test
    fun `No auto promotion when a validation is fixed but another one is still failed`() {
        project {
            branch {
                val vs1 = validationStamp("VS1")
                val vs2 = validationStamp("VS2")
                val pl = promotionLevel("PL")
                setProperty(
                    pl,
                    AutoPromotionPropertyType::class.java,
                    AutoPromotionProperty(listOf(vs1, vs2), "", "", emptyList())
                )
                build("1") {
                    validate(vs2, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                    validate(vs1, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                        .validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                        .validationStatus(ValidationRunStatusID.STATUS_FIXED, "Fixed")
                    // VS2 is still failed
                    checkBuildIsNotPromoted(this, pl)
                }
            }
        }
    }

    /**
     * #1629 - a status change to a status which is not passed must not grant the promotion.
     */
    @Test
    fun `No auto promotion when the validation status changes to a non passed status`() {
        project {
            branch {
                val vs = validationStamp("VS")
                val pl = promotionLevel("PL")
                setProperty(
                    pl,
                    AutoPromotionPropertyType::class.java,
                    AutoPromotionProperty(listOf(vs), "", "", emptyList())
                )
                build("1") {
                    validate(vs, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                        .validationStatus(ValidationRunStatusID.STATUS_EXPLAINED, "Explained")
                    checkBuildIsNotPromoted(this, pl)
                }
            }
        }
    }

    /**
     * #1629 - a validation fixed earlier must still count when the last missing
     * prerequisite is met later on.
     */
    @Test
    fun `Auto promotion granted on a later validation when an earlier one was fixed`() {
        project {
            branch {
                val vs1 = validationStamp("VS1")
                val vs2 = validationStamp("VS2")
                val pl = promotionLevel("PL")
                setProperty(
                    pl,
                    AutoPromotionPropertyType::class.java,
                    AutoPromotionProperty(listOf(vs1, vs2), "", "", emptyList())
                )
                build("1") {
                    // VS1 fails, then is fixed - VS2 has not run yet
                    validate(vs1, validationRunStatusID = ValidationRunStatusID.STATUS_FAILED)
                        .validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Investigating")
                        .validationStatus(ValidationRunStatusID.STATUS_FIXED, "Fixed")
                    checkBuildIsNotPromoted(this, pl)
                    // VS2 passes: the fixed VS1 still counts and the promotion is granted
                    validate(vs2, validationRunStatusID = ValidationRunStatusID.STATUS_PASSED)
                    checkBuildIsPromoted(this, pl)
                }
            }
        }
    }

    private fun checkBuildIsPromoted(build: Build, promotionLevel: PromotionLevel) {
        build.asUserWithView {
            val runs = structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel)
            assertEquals(
                    listOf(promotionLevel.id),
                    runs.map { it.promotionLevel.id }
            )
        }
    }

    private fun checkBuildIsNotPromoted(build: Build, promotionLevel: PromotionLevel) {
        build.asUserWithView {
            val runs = structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel)
            assertTrue(runs.isEmpty())
        }
    }
}
