package net.nemerosa.ontrack.extension.gitlab.config

import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import net.nemerosa.ontrack.extension.config.model.BuildConfiguration
import net.nemerosa.ontrack.extension.gitlab.property.BuildGitLabPipelineRunProperty
import net.nemerosa.ontrack.extension.gitlab.property.BuildGitLabPipelineRunPropertyType
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

/**
 * CI engine for GitLab CI/CD, based on its
 * [predefined variables](https://docs.gitlab.com/ci/variables/predefined_variables/).
 *
 * It is typically used together with the [GitLabSCMEngine], which is detected from the SCM URL this engine
 * provides.
 *
 * **`CI_REPOSITORY_URL` is deliberately never read.** GitLab sets it to
 * `https://gitlab-ci-token:<job token>@<host>/<path>.git`, so taking it as the SCM URL would write a
 * credential into the project property, where it would be stored and displayed. `CI_PROJECT_URL` is the same
 * repository without the credential, and it is the only URL this engine uses - there is no fallback onto
 * `CI_REPOSITORY_URL` when it is missing.
 */
@Component
class GitLabCIEngine(
    private val propertyService: PropertyService,
) : CIEngine {

    override val name: String = "gitlab-ci"

    override fun matchesEnv(env: Map<String, String>): Boolean = env[GITLAB_CI] == "true"

    /**
     * `CI_PROJECT_URL` has no `.git` suffix, which is added for consistency with the other engines.
     */
    override fun getScmUrl(env: Map<String, String>): String? =
        env[CI_PROJECT_URL]?.takeIf { it.isNotBlank() }?.let {
            if (it.endsWith(".git")) it else "$it.git"
        }

    override fun getScmRevision(env: Map<String, String>): String? = env[CI_COMMIT_SHA]

    override fun getProjectName(env: Map<String, String>): String? =
        super.getProjectName(env) ?: env[CI_PROJECT_NAME]

    /**
     * A merge request pipeline is named `PR-<iid>`, so that it's rejected like for the other engines.
     *
     * `CI_COMMIT_BRANCH` is absent from merge request and tag pipelines, so `CI_COMMIT_REF_NAME` - which is
     * always set - is the variable being read.
     */
    override fun getBranchName(env: Map<String, String>): String? =
        super.getBranchName(env)
            ?: env[CI_MERGE_REQUEST_IID]?.takeIf { it.isNotBlank() }?.let { "PR-$it" }
            ?: env[CI_COMMIT_REF_NAME]

    override fun getBuildSuffix(env: Map<String, String>): String? =
        super.getBuildSuffix(env) ?: env[CI_PIPELINE_IID]

    override fun configureBuild(
        build: Build,
        configuration: BuildConfiguration,
        env: Map<String, String>,
    ) {
        val projectPath = env[CI_PROJECT_PATH]?.takeIf { it.isNotBlank() } ?: return
        val pipelineId = env[CI_PIPELINE_ID]?.toLongOrNull() ?: return
        val pipelineIid = env[CI_PIPELINE_IID]?.toIntOrNull() ?: return
        val url = env[CI_PIPELINE_URL]?.takeIf { it.isNotBlank() } ?: return
        propertyService.editProperty(
            entity = build,
            propertyType = BuildGitLabPipelineRunPropertyType::class.java,
            data = BuildGitLabPipelineRunProperty(
                projectPath = projectPath,
                pipelineId = pipelineId,
                pipelineIid = pipelineIid,
                url = url,
            )
        )
    }

    companion object {
        const val GITLAB_CI = "GITLAB_CI"
        const val CI_PROJECT_URL = "CI_PROJECT_URL"
        const val CI_PROJECT_PATH = "CI_PROJECT_PATH"
        const val CI_PROJECT_NAME = "CI_PROJECT_NAME"
        const val CI_COMMIT_SHA = "CI_COMMIT_SHA"
        const val CI_COMMIT_REF_NAME = "CI_COMMIT_REF_NAME"
        const val CI_MERGE_REQUEST_IID = "CI_MERGE_REQUEST_IID"
        const val CI_PIPELINE_ID = "CI_PIPELINE_ID"
        const val CI_PIPELINE_IID = "CI_PIPELINE_IID"
        const val CI_PIPELINE_URL = "CI_PIPELINE_URL"
    }
}
