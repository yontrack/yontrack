package net.nemerosa.ontrack.extension.general

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoPromotionPrerequisitesTest {

    private lateinit var structureService: StructureService
    private lateinit var promotionRunService: PromotionRunService
    private lateinit var validationRunService: ValidationRunService
    private lateinit var prerequisites: AutoPromotionPrerequisites

    private val branch = Branch.of(
        Project.of(nd("P", "")).withId(ID.of(1)),
        nd("B", "")
    ).withId(ID.of(1))

    private val build = Build.of(branch, nd("1", ""), Signature.of("test")).withId(ID.of(1))

    private val quality = ValidationStamp.of(branch, nd("QUALITY", "")).withId(ID.of(1))
    private val security = ValidationStamp.of(branch, nd("SECURITY", "")).withId(ID.of(2))
    private val ciSmoke = ValidationStamp.of(branch, nd("CI-SMOKE", "")).withId(ID.of(3))

    private val bronze = PromotionLevel.of(branch, nd("BRONZE", "")).withId(ID.of(1))
    private val silver = PromotionLevel.of(branch, nd("SILVER", "")).withId(ID.of(2))

    private val allValidationStamps = listOf(quality, security, ciSmoke)
    private val allPromotionLevels = listOf(bronze, silver)

    @BeforeEach
    fun setup() {
        structureService = mockk()
        promotionRunService = mockk()
        validationRunService = mockk()
        prerequisites = AutoPromotionPrerequisites(
            structureService = structureService,
            promotionRunService = promotionRunService,
            validationRunService = validationRunService,
        )
    }

    private fun passed(vararg stamps: ValidationStamp) {
        allValidationStamps.forEach { vs ->
            every { validationRunService.isValidationRunPassed(build, vs) } returns (vs in stamps)
        }
    }

    private fun promoted(vararg levels: PromotionLevel) {
        allPromotionLevels.forEach { pl ->
            every { promotionRunService.isBuildPromoted(build, pl) } returns (pl in levels)
        }
    }

    private fun areSatisfied(property: AutoPromotionProperty, excludedPromotionRunId: Int? = null) =
        prerequisites.areSatisfied(
            build = build,
            property = property,
            branchPromotionLevels = allPromotionLevels,
            branchValidationStamps = allValidationStamps,
            excludedPromotionRunId = excludedPromotionRunId,
        )

    @Test
    fun `Direct validation stamps - all passed`() {
        passed(quality, security)
        promoted()
        assertTrue(areSatisfied(AutoPromotionProperty(listOf(quality, security), "", "", emptyList())))
    }

    @Test
    fun `Direct validation stamps - one not passed`() {
        passed(quality)
        promoted()
        assertFalse(areSatisfied(AutoPromotionProperty(listOf(quality, security), "", "", emptyList())))
    }

    @Test
    fun `Include regular expression`() {
        passed(ciSmoke)
        promoted()
        assertTrue(areSatisfied(AutoPromotionProperty(emptyList(), "CI-.*", "", emptyList())))
        passed()
        assertFalse(areSatisfied(AutoPromotionProperty(emptyList(), "CI-.*", "", emptyList())))
    }

    @Test
    fun `Exclude regular expression removes a stamp from the requirements`() {
        // CI-SMOKE is included by the pattern but excluded again, and it has not passed
        passed()
        promoted()
        assertTrue(areSatisfied(AutoPromotionProperty(emptyList(), ".*", "CI-.*|QUALITY|SECURITY", emptyList())))
    }

    @Test
    fun `A direct validation stamp is required even when excluded by the pattern`() {
        passed()
        promoted()
        assertFalse(areSatisfied(AutoPromotionProperty(listOf(ciSmoke), "", "CI-.*", emptyList())))
    }

    @Test
    fun `Required promotions - all granted`() {
        passed()
        promoted(bronze)
        assertTrue(areSatisfied(AutoPromotionProperty(emptyList(), "", "", listOf(bronze))))
    }

    @Test
    fun `Required promotions - not granted`() {
        passed()
        promoted()
        assertFalse(areSatisfied(AutoPromotionProperty(emptyList(), "", "", listOf(bronze))))
    }

    @Test
    fun `An empty property has no prerequisite and is always satisfied`() {
        passed()
        promoted()
        assertTrue(areSatisfied(AutoPromotionProperty(emptyList(), "", "", emptyList())))
    }

    @Test
    fun `Excluded promotion run is not counted as a granted promotion`() {
        passed()
        val run = PromotionRun.of(build, bronze, Signature.of("test"), "").withId(ID.of(10))
        every { structureService.getPromotionRunsForBuildAndPromotionLevel(build, bronze) } returns listOf(run)
        assertFalse(
            areSatisfied(AutoPromotionProperty(emptyList(), "", "", listOf(bronze)), excludedPromotionRunId = 10),
            "The run being deleted must not keep the prerequisite alive"
        )
    }

    @Test
    fun `Another run for the same promotion level keeps the prerequisite satisfied`() {
        passed()
        val deleted = PromotionRun.of(build, bronze, Signature.of("test"), "").withId(ID.of(10))
        val other = PromotionRun.of(build, bronze, Signature.of("test"), "").withId(ID.of(11))
        every { structureService.getPromotionRunsForBuildAndPromotionLevel(build, bronze) } returns listOf(deleted, other)
        assertTrue(
            areSatisfied(AutoPromotionProperty(emptyList(), "", "", listOf(bronze)), excludedPromotionRunId = 10),
            "Only the deleted run is discounted"
        )
    }
}
