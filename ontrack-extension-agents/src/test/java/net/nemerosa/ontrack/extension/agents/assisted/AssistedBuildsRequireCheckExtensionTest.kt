package net.nemerosa.ontrack.extension.agents.assisted

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.agents.AgentsExtensionFeature
import net.nemerosa.ontrack.extension.agents.license.AgentsLicense
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeBasis
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeProperty
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRunService
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssistedBuildsRequireCheckExtensionTest {

    private lateinit var agentsLicense: AgentsLicense
    private lateinit var propertyService: PropertyService
    private lateinit var assistedChangeService: AssistedChangeService
    private lateinit var structureService: StructureService
    private lateinit var validationRunService: ValidationRunService
    private lateinit var check: AssistedBuildsRequireCheckExtension

    private val build: Build = mockk(relaxed = true)
    private val promotionLevel: PromotionLevel = mockk(relaxed = true)
    private val review: ValidationStamp = mockk(relaxed = true)
    private val scan: ValidationStamp = mockk(relaxed = true)

    @BeforeEach
    fun before() {
        agentsLicense = mockk()
        propertyService = mockk()
        assistedChangeService = mockk()
        structureService = mockk()
        validationRunService = mockk()
        check = AssistedBuildsRequireCheckExtension(
            extensionFeature = mockk<AgentsExtensionFeature>(relaxed = true),
            agentsLicense = agentsLicense,
            propertyService = propertyService,
            assistedChangeService = assistedChangeService,
            structureService = structureService,
            validationRunService = validationRunService,
        )
        every { agentsLicense.agentsEnabled } returns true
        every { promotionLevel.name } returns "GOLD"
        every { review.name } returns "REVIEW"
        every { scan.name } returns "SCAN"
        every { build.branch.id } returns ID.of(1)
        every { structureService.getValidationStampListForBranch(ID.of(1)) } returns listOf(review, scan)
        every { validationRunService.isValidationRunPassed(build, any()) } returns false
    }

    private fun required(vararg stamps: String) {
        every {
            propertyService.getPropertyValue(promotionLevel, AssistedBuildsRequirePropertyType::class.java)
        } returns AssistedBuildsRequireProperty(stamps.toList())
    }

    private fun assistedChange(value: AssistedChangeProperty?) {
        every { assistedChangeService.getAssistedChange(build) } returns value
    }

    private val assisted = AssistedChangeProperty(
        basis = AssistedChangeBasis.COMPUTED,
        assistants = listOf("Claude Code"),
        assistedCommits = 1,
        totalCommits = 2,
    )

    private val notAssisted = AssistedChangeProperty(
        basis = AssistedChangeBasis.COMPUTED,
        totalCommits = 2,
    )

    private fun promotionRun() = PromotionRun.of(build, promotionLevel, Signature.anonymous(), null)

    @Test
    fun `Licence off - nothing is checked`() {
        every { agentsLicense.agentsEnabled } returns false
        required("REVIEW")
        assistedChange(assisted)
        assertEquals(emptyList(), check.explainPromotionRunCreation(build, promotionLevel))
        check.checkPromotionRunCreation(promotionRun())
        verify(exactly = 0) { propertyService.getPropertyValue(any(), AssistedBuildsRequirePropertyType::class.java) }
    }

    @Test
    fun `No property on the level - nothing is required`() {
        every {
            propertyService.getPropertyValue(promotionLevel, AssistedBuildsRequirePropertyType::class.java)
        } returns null
        assistedChange(assisted)
        assertEquals(emptyList(), check.explainPromotionRunCreation(build, promotionLevel))
        check.checkPromotionRunCreation(promotionRun())
    }

    @Test
    fun `A build which is not assisted is not concerned`() {
        required("REVIEW")
        assistedChange(notAssisted)
        assertEquals(emptyList(), check.explainPromotionRunCreation(build, promotionLevel))
        check.checkPromotionRunCreation(promotionRun())
    }

    @Test
    fun `An assisted build without the stamp is refused`() {
        required("REVIEW")
        assistedChange(assisted)
        val ex = assertFailsWith<AssistedBuildsRequireException> {
            check.checkPromotionRunCreation(promotionRun())
        }
        assertEquals("Assisted build: REVIEW must pass first.", ex.message)
    }

    @Test
    fun `An assisted build with the stamp passed is accepted`() {
        required("REVIEW")
        assistedChange(assisted)
        every { validationRunService.isValidationRunPassed(build, review) } returns true
        assertEquals(emptyList(), check.explainPromotionRunCreation(build, promotionLevel))
        check.checkPromotionRunCreation(promotionRun())
    }

    @Test
    fun `Fail closed - an absent assisted change counts as assisted`() {
        required("REVIEW")
        assistedChange(null)
        assertEquals(
            listOf("Assisted build: REVIEW must pass first."),
            check.explainPromotionRunCreation(build, promotionLevel)
        )
    }

    @Test
    fun `Fail closed - an unknown assisted change counts as assisted`() {
        required("REVIEW")
        assistedChange(AssistedChangeProperty.unknown(AssistedChangeProperty.REASON_NO_SCM))
        assertEquals(
            listOf("Assisted build: REVIEW must pass first."),
            check.explainPromotionRunCreation(build, promotionLevel)
        )
    }

    @Test
    fun `Every missing stamp is explained, in the order of the property`() {
        required("SCAN", "REVIEW")
        assistedChange(assisted)
        assertEquals(
            listOf(
                "Assisted build: SCAN must pass first.",
                "Assisted build: REVIEW must pass first.",
            ),
            check.explainPromotionRunCreation(build, promotionLevel)
        )
    }

    @Test
    fun `Fail closed - a stamp which does not exist on the branch cannot pass`() {
        required("MISSING")
        assistedChange(assisted)
        assertEquals(
            listOf("Assisted build: MISSING must pass first."),
            check.explainPromotionRunCreation(build, promotionLevel)
        )
    }

    @Test
    fun `An empty list of stamps requires nothing`() {
        required()
        assistedChange(assisted)
        assertTrue(check.explainPromotionRunCreation(build, promotionLevel).isEmpty())
    }

    @Test
    fun `Name of the check`() {
        assertEquals("Assisted builds require", check.checkName)
    }
}
