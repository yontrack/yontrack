package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.SelfDocumented

/**
 * Field to set on the promotion run
 */
@SelfDocumented
data class YontrackPromotionNotificationChannelConfigField(
    @APIDescription("Name of the field")
    @APILabel("Name")
    val name: String,
    @APIDescription("[template] Value of the field")
    @APILabel("Value")
    val value: String,
)
