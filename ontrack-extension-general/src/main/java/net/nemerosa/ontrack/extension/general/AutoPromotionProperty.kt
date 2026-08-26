package net.nemerosa.ontrack.extension.general

import com.fasterxml.jackson.annotation.JsonIgnore
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.ValidationStamp
import java.util.regex.Pattern

data class AutoPromotionProperty(
    @APIDescription("List of needed validation stamps")
    val validationStamps: List<ValidationStamp>,
    @APIDescription("Regular expression to include validation stamps by name")
    val include: String,
    @APIDescription("Regular expression to exclude validation stamps by name")
    val exclude: String,
    @APIDescription("List of needed promotion levels")
    val promotionLevels: List<PromotionLevel>,
    @APIDescription(
        "When enabled, the promotion is revoked as soon as one of its prerequisites - a required validation " +
                "stamp or a required promotion - is no longer valid. Revoking a promotion deletes it, but does " +
                "not undo its effects: any notification or workflow already triggered by the promotion remains fired."
    )
    val autoRevoke: Boolean = false,
) {

    /**
     * Checks if this property is empty of not
     */
    @JsonIgnore
    fun isEmpty(): Boolean {
        return validationStamps.isEmpty() &&
                promotionLevels.isEmpty() &&
                include.isBlank()
    }

    operator fun contains(vs: ValidationStamp): Boolean {
        return (containsDirectValidationStamp(vs)
                || containsByPattern(vs))
    }

    operator fun contains(pl: PromotionLevel): Boolean {
        return promotionLevels.any { pl.id() == it.id() }
    }

    fun containsDirectValidationStamp(vs: ValidationStamp): Boolean {
        return validationStamps.any { vs.id() == it.id() }
    }

    private fun containsByPattern(vs: ValidationStamp): Boolean {
        return includes(vs) && !excludes(vs)
    }

    private fun includes(vs: ValidationStamp): Boolean {
        return matches(vs, include)
    }

    private fun excludes(vs: ValidationStamp): Boolean {
        return matches(vs, exclude)
    }

    private fun matches(vs: ValidationStamp, pattern: String): Boolean {
        return pattern.isNotBlank() && Pattern.matches(pattern, vs.name)
    }

}