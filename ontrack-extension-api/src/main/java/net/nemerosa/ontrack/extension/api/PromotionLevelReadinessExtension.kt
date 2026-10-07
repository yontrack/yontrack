package net.nemerosa.ontrack.extension.api

import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.readiness.ReadinessItem
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel

/**
 * Contributes to the readiness of a build for a promotion level what decides on the promotion
 * besides the [promotion checks][PromotionRunCheckExtension]: the auto promotion conditions, or the
 * fact that the level is granted by a person.
 *
 * It is only called for a build which does not have the promotion level yet.
 */
interface PromotionLevelReadinessExtension : Extension {

    /**
     * What the [build] still lacks to reach the [promotionLevel], which is on the build's branch.
     *
     * @return The missing items, empty when this extension sees nothing missing
     */
    fun getPromotionLevelMissing(build: Build, promotionLevel: PromotionLevel): List<ReadinessItem>
}
