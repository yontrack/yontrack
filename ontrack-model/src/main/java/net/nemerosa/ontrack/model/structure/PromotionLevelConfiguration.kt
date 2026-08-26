package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.mergeList

@APIDescription("Promotion level configuration")
data class PromotionLevelConfiguration(
    @APIDescription("Name of the promotion level")
    val name: String,
    @APIDescription("Description of the promotion level")
    val description: String = "",
    @APIDescription("List of validations")
    val validations: List<String> = emptyList(),
    @APIDescription("List of promotions")
    val promotions: List<String> = emptyList(),
    @APIDescription("List of promotions this promotion depends on")
    val dependencies: List<String> = emptyList(),
    @APIDescription("List of field definitions for this promotion level")
    val fields: List<PromotionLevelField> = emptyList(),
    /**
     * Nullable on purpose: `null` means "this layer does not say". With a non-null default, any layer
     * omitting the flag would silently override a `true` set by an earlier layer back to `false`.
     * The resolved, non-null value lives on the stored `AutoPromotionProperty` instead.
     *
     * See [merge] for which layer wins.
     */
    @APIDescription("Revokes the promotion when one of its prerequisites is no longer valid. Revoking a promotion deletes it, but does not undo its effects: any notification or workflow already triggered by the promotion remains fired.")
    val autoRevoke: Boolean? = null,
) {
    fun merge(other: PromotionLevelConfiguration) = PromotionLevelConfiguration(
        name = name,
        description = other.description.takeIf { it.isNotBlank() } ?: description,
        validations = (validations + other.validations).distinct(),
        promotions = (promotions + other.promotions).distinct(),
        dependencies = (dependencies + other.dependencies).distinct(),
        fields = mergeList(fields, other.fields, { it.name }) { e, _ -> e },
        // `mergeList` calls this as `later.merge(earlier)` - see `BranchConfiguration.merge` - so the
        // receiver is the layer which gets the last word. It keeps its value when it states one, and falls
        // back to the earlier layer when it says nothing.
        autoRevoke = autoRevoke ?: other.autoRevoke,
    )
}