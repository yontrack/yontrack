package net.nemerosa.ontrack.extension.bitbucket.cloud.config

import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BuildBitbucketPipelineRunProperty
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BuildBitbucketPipelineRunPropertyType
import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import net.nemerosa.ontrack.extension.config.model.BuildConfiguration
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

/**
 * CI engine for Bitbucket Pipelines, based on its
 * [default variables](https://support.atlassian.com/bitbucket-cloud/docs/variables-and-secrets/).
 */
@Component
class BitbucketPipelinesCIEngine(
    private val propertyService: PropertyService,
) : CIEngine {

    override val name: String = "bitbucket-pipelines"

    override fun matchesEnv(env: Map<String, String>): Boolean =
        !env[BITBUCKET_BUILD_NUMBER].isNullOrBlank()

    /**
     * The origin has no `.git` suffix, which is added for consistency with the other engines.
     */
    override fun getScmUrl(env: Map<String, String>): String? =
        env[BITBUCKET_GIT_HTTP_ORIGIN]?.takeIf { it.isNotBlank() }?.let {
            if (it.endsWith(".git")) it else "$it.git"
        }

    override fun getScmRevision(env: Map<String, String>): String? = env[BITBUCKET_COMMIT]

    override fun getProjectName(env: Map<String, String>): String? =
        super.getProjectName(env) ?: env[BITBUCKET_REPO_SLUG]

    /**
     * A pull-request pipeline is named `PR-<id>`, so that it's rejected like for the other engines.
     * `BITBUCKET_BRANCH` is absent from tag pipelines.
     */
    override fun getBranchName(env: Map<String, String>): String? =
        super.getBranchName(env)
            ?: env[BITBUCKET_PR_ID]?.takeIf { it.isNotBlank() }?.let { "PR-$it" }
            ?: env[BITBUCKET_BRANCH]

    override fun getBuildSuffix(env: Map<String, String>): String? =
        super.getBuildSuffix(env) ?: env[BITBUCKET_BUILD_NUMBER]

    override fun configureBuild(
        build: Build,
        configuration: BuildConfiguration,
        env: Map<String, String>,
    ) {
        val workspace = env[BITBUCKET_WORKSPACE]?.takeIf { it.isNotBlank() } ?: return
        val repository = env[BITBUCKET_REPO_SLUG]?.takeIf { it.isNotBlank() } ?: return
        val buildNumber = env[BITBUCKET_BUILD_NUMBER]?.toIntOrNull() ?: return
        propertyService.editProperty(
            entity = build,
            propertyType = BuildBitbucketPipelineRunPropertyType::class.java,
            data = BuildBitbucketPipelineRunProperty(
                workspace = workspace,
                repository = repository,
                buildNumber = buildNumber,
                uuid = env[BITBUCKET_PIPELINE_UUID]?.takeIf { it.isNotBlank() },
                url = BuildBitbucketPipelineRunProperty.runUrl(workspace, repository, buildNumber),
            )
        )
    }

    companion object {
        const val BITBUCKET_BUILD_NUMBER = "BITBUCKET_BUILD_NUMBER"
        const val BITBUCKET_GIT_HTTP_ORIGIN = "BITBUCKET_GIT_HTTP_ORIGIN"
        const val BITBUCKET_COMMIT = "BITBUCKET_COMMIT"
        const val BITBUCKET_BRANCH = "BITBUCKET_BRANCH"
        const val BITBUCKET_PR_ID = "BITBUCKET_PR_ID"
        const val BITBUCKET_REPO_SLUG = "BITBUCKET_REPO_SLUG"
        const val BITBUCKET_WORKSPACE = "BITBUCKET_WORKSPACE"
        const val BITBUCKET_PIPELINE_UUID = "BITBUCKET_PIPELINE_UUID"
    }
}
