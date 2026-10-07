package net.nemerosa.ontrack.extension.config.ci.model

import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Configuration for a promotion")
data class CIPromotionConfig(
    @APIDescription("List of validation stamps to get for this promotion")
    val validations: List<String> = emptyList(),
    @APIDescription("List of promotion levels to get for this promotion")
    val promotions: List<String> = emptyList(),
    @APIDescription("List of promotion levels this promotion depends on")
    val dependsOn: List<String> = emptyList(),
    @APIDescription("List of field definitions for this promotion")
    val fields: List<CIPromotionFieldConfig> = emptyList(),
    /**
     * Nullable on purpose - see [net.nemerosa.ontrack.model.structure.PromotionLevelConfiguration.autoRevoke].
     */
    @APIDescription(
        "When enabled, the promotion is revoked as soon as one of its prerequisites - a required " +
                "validation stamp or a required promotion - is no longer valid. Revoking a promotion " +
                "deletes it, but does not undo its effects: any notification or workflow already " +
                "triggered by the promotion remains fired."
    )
    val autoRevoke: Boolean? = null,
    /**
     * Nullable on purpose - see [net.nemerosa.ontrack.model.structure.PromotionLevelConfiguration.agents].
     */
    @APIDescription(
        "When true, registered agents are admitted to this promotion: an agent may promote a build to it, " +
                "provided its owner may. When false, they are not. Left out, the setting of the promotion " +
                "level is kept as it is."
    )
    val agents: Boolean? = null,
)
