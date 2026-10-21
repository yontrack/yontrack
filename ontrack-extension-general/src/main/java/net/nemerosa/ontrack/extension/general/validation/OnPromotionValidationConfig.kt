package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

/**
 * Configuration of a validation stamp granted when the build linked to the validated build reaches a promotion.
 *
 * @property project Name of the project of the linked build
 * @property qualifier Qualifier of the build link, empty for the default link
 * @property promotion Name of the promotion level the linked build must reach
 */
data class OnPromotionValidationConfig(
    @APILabel("Project")
    @APIDescription("Name of the project of the linked build.")
    val project: String,
    @APILabel("Qualifier")
    @APIDescription("Qualifier of the build link, empty for the default link.")
    val qualifier: String = "",
    @APILabel("Promotion")
    @APIDescription("Name of the promotion level the linked build must reach, in its own branch.")
    val promotion: String,
) {
    /**
     * Checks if this configuration applies to a link to a build of [project], with [qualifier], which has
     * reached [promotion].
     */
    fun matches(project: String, qualifier: String, promotion: String) =
        matches(project, qualifier) && this.promotion == promotion

    /**
     * Checks if this configuration applies to a link to a build of [project], with [qualifier].
     */
    fun matches(project: String, qualifier: String) =
        this.project == project && this.qualifier == qualifier
}
