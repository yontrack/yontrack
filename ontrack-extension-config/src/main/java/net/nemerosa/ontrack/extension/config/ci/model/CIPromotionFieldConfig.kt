package net.nemerosa.ontrack.extension.config.ci.model

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.structure.PromotionLevelFieldType

@APIDescription("Configuration of a promotion field")
data class CIPromotionFieldConfig(
    @APIDescription("Technical name of the field")
    val name: String,
    @APIDescription("Display name of the field")
    val displayName: String,
    @APIDescription("Description of the field")
    val description: String?,
    @APIDescription("Type of the field")
    val type: PromotionLevelFieldType,
    @APIDescription("Is the field required?")
    val required: Boolean = false,
    @APIDescription("List of options for a choice field")
    val options: List<String> = emptyList(),
)
