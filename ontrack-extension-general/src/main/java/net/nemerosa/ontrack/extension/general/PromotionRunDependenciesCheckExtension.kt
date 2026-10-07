package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.api.PromotionRunCheckExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Checks if a promotion can be granted according
 * to the promotion dependencies defined by the
 * [PromotionDependenciesPropertyType] property.
 */
@Component
class PromotionRunDependenciesCheckExtension(
        extensionFeature: GeneralExtensionFeature,
        private val propertyService: PropertyService,
        private val structureService: StructureService
) : AbstractExtension(extensionFeature), PromotionRunCheckExtension {

    override fun checkPromotionRunCreation(promotionRun: PromotionRun) {
        val (dependencies, missing) = missingDependencies(promotionRun.build, promotionRun.promotionLevel)
        // If not promoted, we fail the check on the first missing dependency
        missing.firstOrNull()?.let { dependency ->
            throw PromotionDependenciesException(
                    promotionRun,
                    dependencies,
                    dependency
            )
        }
    }

    /**
     * One reason per dependency which is not granted.
     */
    override fun explainPromotionRunCreation(build: Build, promotionLevel: PromotionLevel): List<String> {
        val (dependencies, missing) = missingDependencies(build, promotionLevel)
        return missing.map { dependency ->
            "${promotionLevel.name} requires the $dependency promotion to be granted first " +
                    "(promotion dependencies of ${promotionLevel.name}: ${dependencies.joinToString(", ")})."
        }
    }

    /**
     * Dependencies of the [promotion], and the ones of them the [build] has not been granted, in the
     * order of the dependencies.
     */
    private fun missingDependencies(build: Build, promotion: PromotionLevel): Pair<List<String>, List<String>> {
        // Gets its dependencies
        val dependencies = propertyService
                .getProperty(promotion, PromotionDependenciesPropertyType::class.java)
                .value
                ?.dependencies
                ?: emptyList()
        val missing = dependencies.filter { dependency ->
            // Gets the associated promotion
            val dependencyPromotion: PromotionLevel? = structureService.findPromotionLevelByName(
                    build.project.name,
                    build.branch.name,
                    dependency
            ).orElse(null)
            // A dependency which does not exist on the branch is ignored
            dependencyPromotion != null &&
                    structureService.getPromotionRunsForBuildAndPromotionLevel(build, dependencyPromotion).isEmpty()
        }
        return dependencies to missing
    }

    override val checkName: String = "Promotion dependencies"

    /**
     * After [PreviousPromotionConditionCheckExtension] has been applied.
     */
    override val order: Int = 1
}
