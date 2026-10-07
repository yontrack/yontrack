package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.api.PromotionLevelReadinessExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.readiness.ReadinessItem
import net.nemerosa.ontrack.model.readiness.ReadinessKind
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component

/**
 * Readiness of a build for a promotion level, as its auto promotion sees it.
 *
 * - With an auto promotion, every required validation stamp which has not passed, and every required
 *   promotion level which is not reached, is missing - the same conditions as the auto promotion
 *   itself, read through [AutoPromotionConditionsService].
 * - Without one, the level is granted by a person, which is missing until that person acts.
 */
@Component
class AutoPromotionReadinessExtension(
    extensionFeature: GeneralExtensionFeature,
    private val autoPromotionConditionsService: AutoPromotionConditionsService,
) : AbstractExtension(extensionFeature), PromotionLevelReadinessExtension {

    override fun getPromotionLevelMissing(build: Build, promotionLevel: PromotionLevel): List<ReadinessItem> {
        val conditions = autoPromotionConditionsService.getBuildConditions(build, promotionLevel)
            ?: return listOf(
                ReadinessItem(
                    kind = ReadinessKind.MANUAL,
                    name = promotionLevel.name,
                    message = "${promotionLevel.name} has no auto promotion and is granted by a person: " +
                            "the build is not ready until someone promotes it, even when nothing else is missing.",
                )
            )
        val stamps = conditions.validationStamps.filterNot { it.passed }.map { condition ->
            ReadinessItem(
                kind = ReadinessKind.VALIDATION,
                name = condition.validationStamp.name,
                message = condition.lastRun?.let { run -> "Last status: ${run.lastStatusId}" }
                    ?: "Not validated",
            )
        }
        val levels = conditions.promotionLevels.filter { it.promotionRun == null }.map { condition ->
            ReadinessItem(
                kind = ReadinessKind.PROMOTION,
                name = condition.promotionLevel.name,
                message = "Not promoted to ${condition.promotionLevel.name}",
            )
        }
        return stamps + levels
    }
}
