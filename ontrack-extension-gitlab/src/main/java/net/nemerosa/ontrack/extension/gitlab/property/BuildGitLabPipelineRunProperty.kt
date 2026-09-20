package net.nemerosa.ontrack.extension.gitlab.property

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Link between a build and the GitLab CI/CD pipeline which created it.
 *
 * The URL is stored rather than computed: GitLab runs on gitlab.com and on self-managed instances alike, so
 * the host is not known from the project path only. GitLab CI provides it as `CI_PIPELINE_URL`.
 */
data class BuildGitLabPipelineRunProperty(
    @APIDescription("Path of the GitLab project, subgroups included, like `nemerosa/tools/yontrack`")
    val projectPath: String,
    @APIDescription("ID of the pipeline, unique across the GitLab instance")
    val pipelineId: Long,
    @APIDescription("IID of the pipeline, its number inside the project")
    val pipelineIid: Int,
    @APIDescription("Link to the pipeline")
    val url: String,
)
