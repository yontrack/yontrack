package net.nemerosa.ontrack.extension.agents.assisted

import net.nemerosa.ontrack.extension.agents.AgentsExtensionFeature
import net.nemerosa.ontrack.extension.agents.license.AgentsLicense
import net.nemerosa.ontrack.extension.api.PromotionRunCheckExtension
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeBasis
import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeService
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRunService
import org.springframework.stereotype.Component

/**
 * Applies the [Assisted builds require][AssistedBuildsRequirePropertyType] property of a promotion
 * level: if the build is assisted, every listed validation stamp must have passed (its last run being
 * `PASSED` or `FIXED`) before the build is promoted to the level.
 *
 * * **Licence off**: the check does nothing.
 * * **Fail closed**: a build whose assisted change has not been computed yet, or is `UNKNOWN`, counts
 *   as assisted - a race with the asynchronous computation, or a project without SCM, never bypasses
 *   the review. A listed stamp which does not exist on the branch cannot pass.
 *
 * Manual promotions, auto-promotion and workflows all go through it.
 */
@Component
class AssistedBuildsRequireCheckExtension(
    extensionFeature: AgentsExtensionFeature,
    private val agentsLicense: AgentsLicense,
    private val propertyService: PropertyService,
    private val assistedChangeService: AssistedChangeService,
    private val structureService: StructureService,
    private val validationRunService: ValidationRunService,
) : AbstractExtension(extensionFeature), PromotionRunCheckExtension {

    override fun checkPromotionRunCreation(promotionRun: PromotionRun) {
        missingStamps(promotionRun.build, promotionRun.promotionLevel).firstOrNull()?.let { stamp ->
            throw AssistedBuildsRequireException(stamp)
        }
    }

    /**
     * One reason per required stamp which has not passed.
     */
    override fun explainPromotionRunCreation(build: Build, promotionLevel: PromotionLevel): List<String> =
        missingStamps(build, promotionLevel).map { AssistedBuildsRequireException.message(it) }

    /**
     * Names of the stamps required by the [promotionLevel] which the [build] has not passed, in the
     * order of the property. Empty when the licence is off, when the level requires nothing, or when
     * the build is known not to be assisted.
     */
    private fun missingStamps(build: Build, promotionLevel: PromotionLevel): List<String> {
        if (!agentsLicense.agentsEnabled) {
            return emptyList()
        }
        val required = propertyService.getPropertyValue(promotionLevel, AssistedBuildsRequirePropertyType::class.java)
            ?.validationStamps
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()
        if (!isAssisted(build)) {
            return emptyList()
        }
        val stamps = structureService.getValidationStampListForBranch(build.branch.id).associateBy { it.name }
        return required.filter { name ->
            val stamp = stamps[name]
            stamp == null || !validationRunService.isValidationRunPassed(build, stamp)
        }
    }

    /**
     * Fail closed: only a known, not assisted change lets the build through.
     */
    private fun isAssisted(build: Build): Boolean {
        val change = assistedChangeService.getAssistedChange(build)
        return change == null || change.basis == AssistedChangeBasis.UNKNOWN || change.assisted
    }

    override val checkName: String = "Assisted builds require"

    /**
     * After the previous promotion condition (0) and the promotion dependencies (1).
     */
    override val order: Int = 10
}
