package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.extension.general.AutoPromotionProperty
import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationRunRequest
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #2101 - validating a build when the build it links to reaches a promotion.
 */
@AsAdminTest
class OnPromotionValidationIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var onPromotionValidationDataType: OnPromotionValidationDataType

    @Test
    fun `Dependencies reaching SILVER validate the existing consumer build, which is auto promoted`() {
        withSetup {
            val b1 = b.build("1.0")
            val c1 = c.build("2.0")
            val a1 = a.build("1").apply {
                linkTo(b1)
                linkTo(c1)
                validate(aTests)
            }
            assertNoRun(a1, aBSilver)
            assertNotPromoted(a1, aSilver)
            // B reaches SILVER
            val bRun = b1.promote(bSilver)
            assertPassed(a1, aBSilver, b1, bRun.id())
            assertNotPromoted(a1, aSilver)
            // C reaches SILVER
            val cRun = c1.promote(cSilver)
            assertPassed(a1, aCSilver, c1, cRun.id())
            // A is now SILVER
            assertPromoted(a1, aSilver)
        }
    }

    @Test
    fun `A consumer build linked to already promoted dependencies is validated at once`() {
        withSetup {
            val b1 = b.build("1.0")
            val c1 = c.build("2.0")
            val bRun = b1.promote(bSilver)
            val cRun = c1.promote(cSilver)
            val a1 = a.build("1").apply {
                validate(aTests)
                linkTo(b1)
                assertPassed(this, aBSilver, b1, bRun.id())
                assertNotPromoted(this, aSilver)
                linkTo(c1)
                assertPassed(this, aCSilver, c1, cRun.id())
            }
            assertPromoted(a1, aSilver)
        }
    }

    @Test
    fun `A link with another qualifier does not match`() {
        withSetup {
            val b1 = b.build("1.0")
            val bQualified = b.build("1.1")
            bQualified.promote(bSilver)
            val a1 = a.build("1").apply {
                // Already promoted, but linked with another qualifier
                linkTo(bQualified, "other")
                assertNoRun(this, aBSilver)
                // Promoted later, linked with another qualifier
                linkTo(b1, "other")
            }
            b1.promote(bSilver)
            assertNoRun(a1, aBSilver)
        }
    }

    @Test
    fun `A qualified stamp matches a link with the same qualifier only`() {
        withSetup {
            val aQualified = a.validationStamp(
                "B-SILVER-QUALIFIED",
                onPromotionValidationDataType.config(
                    OnPromotionValidationConfig(project = b.project.name, qualifier = "dep", promotion = "SILVER")
                )
            )
            val b1 = b.build("1.0")
            val a1 = a.build("1").apply {
                linkTo(b1, "dep")
            }
            b1.promote(bSilver)
            assertPassed(a1, aQualified, b1)
            // The default stamp is not concerned by the qualified link
            assertNoRun(a1, aBSilver)
        }
    }

    @Test
    fun `A promotion other than the configured one does not match`() {
        withSetup {
            val b1 = b.build("1.0")
            val a1 = a.build("1").apply {
                linkTo(b1)
            }
            // GOLD is not SILVER
            b1.promote(bGold)
            assertNoRun(a1, aBSilver)
            // A new link to a build which has only GOLD
            val b2 = b.build("1.1")
            b2.promote(bGold)
            val a2 = a.build("2").apply {
                linkTo(b2)
            }
            assertNoRun(a2, aBSilver)
        }
    }

    @Test
    fun `An existing passed run is not duplicated`() {
        withSetup {
            val b1 = b.build("1.0")
            b1.promote(bSilver)
            val a1 = a.build("1").apply {
                linkTo(b1)
            }
            assertEquals(1, runs(a1, aBSilver).size)
            // Second promotion of the same build
            b1.promote(bSilver)
            assertEquals(1, runs(a1, aBSilver).size)
            // Another dependency build, already promoted, also linked
            val b2 = b.build("1.1")
            b2.promote(bSilver)
            a1.linkTo(b2)
            assertEquals(1, runs(a1, aBSilver).size)
        }
    }

    @Test
    fun `All consumer builds using the promoted build are validated, beyond the first page`() {
        withSetup {
            val b1 = b.build("1.0")
            val consumers = (1..12).map { index ->
                a.build("$index").apply {
                    linkTo(b1)
                }
            }
            b1.promote(bSilver)
            consumers.forEach { consumer ->
                assertPassed(consumer, aBSilver, b1)
            }
        }
    }

    @Test
    fun `Deleting the promotion of the linked build fails the run, and revokes the consumer promotion`() {
        withSetup {
            val b1 = b.build("1.0")
            val c1 = c.build("2.0")
            val bRun = b1.promote(bSilver)
            c1.promote(cSilver)
            val a1 = a.build("1").apply {
                validate(aTests)
                linkTo(b1)
                linkTo(c1)
            }
            assertPromoted(a1, aSilver)
            // Deleting B's SILVER
            structureService.deletePromotionRun(bRun.id)
            val last = lastRun(a1, aBSilver)
            assertEquals(ValidationRunStatusID.FAILED, last.lastStatusId)
            assertTrue(
                last.lastStatus.description?.contains(b1.name) == true &&
                        last.lastStatus.description?.contains(bSilver.name) == true,
                "The description names the linked build and its promotion"
            )
            // A's SILVER is revoked
            assertNotPromoted(a1, aSilver)
            // B is promoted again: A is validated again, and promoted again
            val bRun2 = b1.promote(bSilver)
            assertPassed(a1, aBSilver, b1, bRun2.id())
            assertPromoted(a1, aSilver)
        }
    }

    @Test
    fun `Deleting one of several promotion runs of the linked build changes nothing`() {
        withSetup {
            val b1 = b.build("1.0")
            val c1 = c.build("2.0")
            val bRun = b1.promote(bSilver)
            b1.promote(bSilver)
            c1.promote(cSilver)
            val a1 = a.build("1").apply {
                validate(aTests)
                linkTo(b1)
                linkTo(c1)
            }
            assertPromoted(a1, aSilver)
            structureService.deletePromotionRun(bRun.id)
            assertEquals(1, runs(a1, aBSilver).size)
            assertTrue(lastRun(a1, aBSilver).isPassed)
            assertPromoted(a1, aSilver)
        }
    }

    @Test
    fun `Deleting the promotion of another linked build does not fail a run granted by a different build`() {
        withSetup {
            val b1 = b.build("1.0")
            val b2 = b.build("1.1")
            val b2Run = b2.promote(bSilver)
            b1.promote(bSilver)
            // A1 is validated by B1 only
            val a1 = a.build("1").apply {
                linkTo(b1)
            }
            assertPassed(a1, aBSilver, b1)
            // B2 is linked afterwards: the run already passed, nothing is added
            a1.linkTo(b2)
            // Deleting B2's SILVER does not touch the run granted by B1
            structureService.deletePromotionRun(b2Run.id)
            assertEquals(1, runs(a1, aBSilver).size)
            assertTrue(lastRun(a1, aBSilver).isPassed)
        }
    }

    @Test
    fun `A run posted by hand without data is accepted`() {
        withSetup {
            val a1 = a.build("1")
            val run = structureService.newValidationRun(
                a1,
                ValidationRunRequest(
                    validationStampName = aBSilver.name,
                    validationRunStatusId = ValidationRunStatusID.STATUS_PASSED,
                    description = "Checked by hand",
                )
            )
            assertTrue(run.isPassed)
            assertNull(run.data)
        }
    }

    // Setup

    private inner class Setup(
        val a: Branch,
        val b: Branch,
        val c: Branch,
        val aTests: ValidationStamp,
        val aBSilver: ValidationStamp,
        val aCSilver: ValidationStamp,
        val aSilver: PromotionLevel,
        val bSilver: PromotionLevel,
        val bGold: PromotionLevel,
        val cSilver: PromotionLevel,
    )

    /**
     * Dependencies B and C, each with a SILVER promotion, and a consumer A whose SILVER requires its
     * own tests and B's and C's SILVER, through two validation on promotion stamps.
     */
    private fun withSetup(code: Setup.() -> Unit) {
        lateinit var b: Branch
        lateinit var c: Branch
        project {
            b = branch("main") {}
        }
        project {
            c = branch("main") {}
        }
        project {
            branch("main") {
                val aTests = validationStamp("tests")
                val aBSilver = validationStamp(
                    "B-SILVER",
                    onPromotionValidationDataType.config(
                        OnPromotionValidationConfig(project = b.project.name, promotion = "SILVER")
                    )
                )
                val aCSilver = validationStamp(
                    "C-SILVER",
                    onPromotionValidationDataType.config(
                        OnPromotionValidationConfig(project = c.project.name, promotion = "SILVER")
                    )
                )
                val aSilver = promotionLevel("SILVER")
                setProperty(
                    aSilver,
                    AutoPromotionPropertyType::class.java,
                    AutoPromotionProperty(
                        validationStamps = listOf(aTests, aBSilver, aCSilver),
                        include = "",
                        exclude = "",
                        promotionLevels = emptyList(),
                        autoRevoke = true,
                    )
                )
                Setup(
                    a = this,
                    b = b,
                    c = c,
                    aTests = aTests,
                    aBSilver = aBSilver,
                    aCSilver = aCSilver,
                    aSilver = aSilver,
                    bSilver = b.promotionLevel("SILVER"),
                    bGold = b.promotionLevel("GOLD"),
                    cSilver = c.promotionLevel("SILVER"),
                ).code()
            }
        }
    }

    // Assertions

    private fun runs(build: Build, validationStamp: ValidationStamp): List<ValidationRun> =
        structureService.getValidationRunsForBuildAndValidationStamp(build, validationStamp, 0, 100)

    private fun lastRun(build: Build, validationStamp: ValidationStamp): ValidationRun =
        runs(build, validationStamp).maxBy { it.runOrder }

    private fun assertNoRun(build: Build, validationStamp: ValidationStamp) {
        assertTrue(
            runs(build, validationStamp).isEmpty(),
            "No ${validationStamp.name} run on build ${build.name}"
        )
    }

    private fun assertPassed(
        build: Build,
        validationStamp: ValidationStamp,
        linkedBuild: Build,
        promotionRunId: Int? = null,
    ) {
        val run = lastRun(build, validationStamp)
        assertTrue(run.isPassed, "Last ${validationStamp.name} run on build ${build.name} is passed")
        val data = run.data?.data as? OnPromotionValidationData
            ?: error("The run carries the linked build")
        assertEquals(linkedBuild.id(), data.buildId)
        assertEquals(linkedBuild.project.name, data.project)
        assertEquals(linkedBuild.branch.name, data.branch)
        assertEquals(linkedBuild.name, data.build)
        assertEquals(validationStamp.dataType?.config.let { it as OnPromotionValidationConfig }.promotion, data.promotion)
        if (promotionRunId != null) {
            assertEquals(promotionRunId, data.promotionRunId)
        }
    }

    private fun assertPromoted(build: Build, promotionLevel: PromotionLevel) {
        assertTrue(
            structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel).isNotEmpty(),
            "Build ${build.name} is promoted to ${promotionLevel.name}"
        )
    }

    private fun assertNotPromoted(build: Build, promotionLevel: PromotionLevel) {
        assertTrue(
            structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel).isEmpty(),
            "Build ${build.name} is not promoted to ${promotionLevel.name}"
        )
    }
}
