package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.common.api.APIDescription

data class NotGrantedMedalsProperty(
    @APIDescription("Shows, in the branch builds view, a medal for each auto-promoted level a build does not have yet.")
    val enabled: Boolean,
)
