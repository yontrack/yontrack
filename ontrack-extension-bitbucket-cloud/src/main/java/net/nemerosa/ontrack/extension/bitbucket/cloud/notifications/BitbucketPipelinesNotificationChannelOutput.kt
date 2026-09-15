package net.nemerosa.ontrack.extension.bitbucket.cloud.notifications

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.DocumentationList

data class BitbucketPipelinesNotificationChannelOutput(
    @APIDescription("Slug of the Bitbucket Cloud workspace")
    @APILabel("Workspace")
    val workspace: String,
    @APIDescription("Slug of the repository")
    @APILabel("Repository")
    val repository: String,
    @APIDescription("Branch the pipeline runs on")
    @APILabel("Branch")
    val branch: String,
    @APIDescription("Name of the custom pipeline, empty for the default pipeline of the branch")
    @APILabel("Pipeline")
    val pipeline: String?,
    @APIDescription("Variables passed to the pipeline")
    @APILabel("Variables")
    @DocumentationList
    val variables: List<BitbucketPipelinesNotificationChannelConfigVariable> = emptyList(),
    @APIDescription("UUID of the pipeline, filled in once it is triggered")
    @APILabel("UUID")
    val uuid: String? = null,
    @APIDescription("Build number of the pipeline, filled in once it is triggered")
    @APILabel("Build number")
    val buildNumber: Int? = null,
    @APIDescription("URL of the pipeline run, filled in once it is triggered")
    @APILabel("URL")
    val url: String? = null,
    @APIDescription("Last known state of the pipeline: PARSING, PENDING or IN_PROGRESS while it runs, then SUCCESSFUL, FAILED, ERROR, STOPPED or EXPIRED. Only followed in SYNC mode.")
    @APILabel("State")
    val state: String? = null,
)
