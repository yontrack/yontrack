package net.nemerosa.ontrack.model.structure

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

data class BuildSearchForm(
    @Min(1)
    @APILabel("Maximum count")
    @APIDescription("Maximum number of builds to return.")
    val maximumCount: Int = 10,
    @APILabel("Branch")
    @APIDescription("Regular expression to match against the branch name.")
    val branchName: String? = null,
    @APILabel("Build")
    @APIDescription("Regular expression to match against the build name, unless `buildExactMatch` is set to `true`.")
    val buildName: String? = null,
    @APILabel("Promotion level")
    @APIDescription("Matches a build having at least this promotion.")
    val promotionName: String? = null,
    @APILabel("Validation stamp")
    @APIDescription("Matches a build having at least this validation with PASSED as a status.")
    val validationStampName: String? = null,
    @APILabel("Property")
    @APIDescription("Matches a build having this property.")
    val property: String? = null,
    @Size(max = 200)
    @APILabel("Property value")
    @APIDescription("When the property is set, matches against the property value.")
    val propertyValue: String? = null,
    @APILabel("Match exact build name")
    @APIDescription("When `buildName` is set, considers an exact match on the build name.")
    val buildExactMatch: Boolean = false,
    @APILabel("Linked from")
    @APIDescription("`project:build` expression, matches against builds being linked from the build to match.")
    val linkedFrom: String? = null,
    @APILabel("Linked to")
    @APIDescription("`project:build` expression, matches against builds being linked to the build to match.")
    val linkedTo: String? = null,
    @APILabel("Extensions")
    @APIDescription("Search extensions")
    val extensions: List<BuildSearchFormExtension>? = null,
    @APILabel("Assisted")
    @APIDescription(ASSISTED_DESCRIPTION)
    val assisted: String? = null,
    @APILabel("Actor")
    @APIDescription(ACTOR_DESCRIPTION)
    val actor: String? = null,
) {
    companion object {
        /**
         * Description of the assisted criterion, shared by the build filters.
         */
        const val ASSISTED_DESCRIPTION =
            "Whether the commits of the build were written with assistants, from its assisted change: `YES`, `NO`, or `UNKNOWN` for a build whose assisted change is absent or could not be computed. Case-insensitive."

        /**
         * Description of the actor criterion, shared by the build filters.
         */
        const val ACTOR_DESCRIPTION =
            "Actor who created the build: `HUMAN` for a person, `AGENT` for any agent, or the identifier of one agent, `<slug>[agent]`. Case-insensitive."
    }
}
