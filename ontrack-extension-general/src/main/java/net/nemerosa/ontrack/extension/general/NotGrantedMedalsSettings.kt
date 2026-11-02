package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Not-granted medals")
data class NotGrantedMedalsSettings(
    @APIDescription("Shows, in the branch builds view, a medal for each auto-promoted level a build does not have yet. A project or a branch can override it with a property.")
    val enabled: Boolean,
)
