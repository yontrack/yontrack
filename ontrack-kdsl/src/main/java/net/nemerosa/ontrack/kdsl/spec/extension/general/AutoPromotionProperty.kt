package net.nemerosa.ontrack.kdsl.spec.extension.general

import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.spec.PromotionLevel
import net.nemerosa.ontrack.kdsl.spec.deleteProperty
import net.nemerosa.ontrack.kdsl.spec.getProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty

/**
 * Sets an auto promotion property on a promotion level.
 */
var PromotionLevel.autoPromotion: AutoPromotionProperty?
    get() = getProperty(AUTO_PROMOTION_PROPERTY)?.parse()
    set(value) {
        if (value != null) {
            setProperty(AUTO_PROMOTION_PROPERTY, value)
        } else {
            deleteProperty(AUTO_PROMOTION_PROPERTY)
        }
    }

const val AUTO_PROMOTION_PROPERTY = "net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType"

data class AutoPromotionProperty(
    /**
     * List of needed validation stamps
     */
    val validationStamps: List<UInt> = emptyList(),
    /**
     * Regular expression to include validation stamps by name
     */
    val include: String = "",
    /**
     * Regular expression to exclude validation stamps by name
     */
    val exclude: String = "",
    /**
     * List of needed promotion levels
     */
    val promotionLevels: List<UInt> = emptyList(),
    /**
     * When enabled, the promotion is revoked as soon as one of its prerequisites - a required validation
     * stamp or a required promotion - is no longer valid. Revoking a promotion deletes it, but does not
     * undo its effects: any notification or workflow already triggered by the promotion remains fired.
     */
    val autoRevoke: Boolean = false,
)
