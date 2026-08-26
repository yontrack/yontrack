package net.nemerosa.ontrack.kdsl.spec.extension.general

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.kdsl.spec.PromotionLevel
import net.nemerosa.ontrack.kdsl.spec.deleteProperty
import net.nemerosa.ontrack.kdsl.spec.getProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty

/**
 * Sets an auto promotion property on a promotion level.
 *
 * Note that the property is _written_ using lists of validation stamp & promotion level IDs but
 * is _read back_ from the API using lists of complete validation stamps & promotion levels. The
 * getter takes care of the conversion so that the same [AutoPromotionProperty] type can be used
 * on both sides.
 */
var PromotionLevel.autoPromotion: AutoPromotionProperty?
    get() = getProperty(AUTO_PROMOTION_PROPERTY)?.let { node ->
        AutoPromotionProperty(
            validationStamps = node.path("validationStamps").parseIds(),
            include = node.path("include").asText(""),
            exclude = node.path("exclude").asText(""),
            promotionLevels = node.path("promotionLevels").parseIds(),
            autoRevoke = node.path("autoRevoke").asBoolean(false),
        )
    }
    set(value) {
        if (value != null) {
            setProperty(AUTO_PROMOTION_PROPERTY, value)
        } else {
            deleteProperty(AUTO_PROMOTION_PROPERTY)
        }
    }

/**
 * Reads a list of entity IDs, accepting both a list of raw IDs and a list of entities carrying an `id` field.
 */
private fun JsonNode.parseIds(): List<UInt> =
    if (isArray) {
        map { item ->
            if (item.isObject) {
                item.path("id").asInt().toUInt()
            } else {
                item.asInt().toUInt()
            }
        }
    } else {
        emptyList()
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
