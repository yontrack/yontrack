package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.DocumentationList
import net.nemerosa.ontrack.model.docs.SelfDocumented

data class YontrackDependenciesPromotionNotificationChannelOutput(
    @APIDescription("Linked builds selected by this notification, and what happened to each of them")
    @DocumentationList
    val targets: List<YontrackDependenciesPromotionNotificationChannelOutputTarget> = emptyList(),
)

/**
 * What happened to a linked build
 */
@SelfDocumented
data class YontrackDependenciesPromotionNotificationChannelOutputTarget(
    @APIDescription("Project of the target")
    @APILabel("Project")
    val project: String,
    @APIDescription("Branch of the target")
    @APILabel("Branch")
    val branch: String,
    @APIDescription("Name of the target build")
    @APILabel("Build")
    val build: String,
    @APIDescription("Matching link qualifiers. An unqualified link has an empty qualifier.")
    @APILabel("Qualifiers")
    val qualifiers: List<String>,
    @APIDescription("Outcome for this target: PROMOTED, SKIPPED or FAILED")
    @APILabel("Status")
    val status: YontrackDependenciesPromotionNotificationChannelTargetStatus,
    @APIDescription("ID of the created promotion run, when promoted")
    @APILabel("Run ID")
    val runId: Int? = null,
    @APIDescription("Reason, when skipped or failed")
    @APILabel("Message")
    val message: String? = null,
)

enum class YontrackDependenciesPromotionNotificationChannelTargetStatus {
    PROMOTED,
    SKIPPED,
    FAILED,
}
