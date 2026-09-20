package net.nemerosa.ontrack.extension.gitlab.notifications

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.DocumentationList

/**
 * What the `gitlab-pipeline` channel stores about a notification.
 *
 * It holds nothing of the GitLab configuration but its name: the token never reaches a variable, and the
 * URL is built from the configured instance URL rather than from what GitLab answered.
 */
data class GitLabPipelineNotificationChannelOutput(
    @APIDescription("Full path of the GitLab project")
    @APILabel("Project")
    val project: String,
    @APIDescription("Branch or tag the pipeline runs on")
    @APILabel("Ref")
    val ref: String,
    @APIDescription("Variables passed to the pipeline")
    @APILabel("Variables")
    @DocumentationList
    val variables: List<GitLabPipelineNotificationChannelConfigVariable> = emptyList(),
    @APIDescription("Identifier of the pipeline in the GitLab instance, filled in once it is triggered")
    @APILabel("ID")
    val id: Long? = null,
    @APIDescription("Number of the pipeline inside its project, the one GitLab displays, filled in once it is triggered")
    @APILabel("IID")
    val iid: Long? = null,
    @APIDescription("URL of the pipeline, filled in once it is triggered")
    @APILabel("URL")
    val url: String? = null,
    @APIDescription("Last known status of the pipeline: created, waiting_for_resource, preparing, pending or running while it runs, then success, failed, canceled, skipped or manual. Only followed to completion in SYNC mode.")
    @APILabel("Status")
    val status: String? = null,
)
