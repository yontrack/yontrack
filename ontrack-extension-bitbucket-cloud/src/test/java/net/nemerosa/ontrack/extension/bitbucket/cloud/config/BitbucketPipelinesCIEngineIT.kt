package net.nemerosa.ontrack.extension.bitbucket.cloud.config

import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BuildBitbucketPipelineRunProperty
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BuildBitbucketPipelineRunPropertyType
import net.nemerosa.ontrack.extension.config.ci.CIConfigPRNotSupportedException
import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BitbucketPipelinesCIEngineIT : AbstractDSLTestSupport() {

    /**
     * One repository slug per test instance, so no two tests configure the same project. See #1657.
     */
    private val repoSlug = uid("repo-")

    @Autowired
    private lateinit var bitbucketCloudConfigurationService: BitbucketCloudConfigurationService

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @BeforeEach
    fun cleanup() {
        asAdmin {
            bitbucketCloudConfigurationService.configurations.forEach {
                bitbucketCloudConfigurationService.deleteConfiguration(it.name)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Bitbucket Pipelines build detected without any ci or scm`() {
        val config = bitbucketCloudTestConfigMock(name = uid("bbc-"))
        withDisabledConfigurationTest {
            bitbucketCloudConfigurationService.newConfiguration(config)
            val build = configTestSupport.configureBuild(
                ci = null,
                scm = null,
                env = bitbucketPipelinesEnv(),
            )
            assertEquals(repoSlug, build.project.name)
            assertEquals("release-1.0", build.branch.name)
            assertTrue(build.name.endsWith("-42"), "Build suffix is the pipeline build number")
            assertNotNull(
                propertyService.getPropertyValue(build.project, BitbucketCloudProjectConfigurationPropertyType::class.java),
                "Bitbucket Cloud project configuration has been set"
            ) {
                assertEquals(config.name, it.configuration.name)
                assertEquals("my-workspace", it.workspace)
                assertEquals(repoSlug, it.repository)
            }
            assertNotNull(
                propertyService.getPropertyValue(build.branch, GitBranchConfigurationPropertyType::class.java),
                "Git branch configuration has been set"
            ) {
                assertEquals("release/1.0", it.branch)
            }
            assertNotNull(
                propertyService.getPropertyValue(build, GitCommitPropertyType::class.java),
                "Git build commit has been set"
            ) {
                assertEquals(COMMIT, it.commit)
            }
            assertNotNull(
                propertyService.getPropertyValue(build, BuildBitbucketPipelineRunPropertyType::class.java),
                "Bitbucket Pipelines run has been set"
            ) {
                assertEquals(
                    BuildBitbucketPipelineRunProperty(
                        workspace = "my-workspace",
                        repository = repoSlug,
                        buildNumber = 42,
                        uuid = PIPELINE_UUID,
                        url = "https://bitbucket.org/my-workspace/$repoSlug/pipelines/results/42",
                    ),
                    it
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Bitbucket Pipelines pull request pipeline is rejected`() {
        withDisabledConfigurationTest {
            bitbucketCloudConfigurationService.newConfiguration(bitbucketCloudTestConfigMock(name = uid("bbc-")))
            assertFailsWith<CIConfigPRNotSupportedException> {
                configTestSupport.configureBuild(
                    ci = null,
                    scm = null,
                    env = bitbucketPipelinesEnv() + ("BITBUCKET_PR_ID" to "12"),
                )
            }
        }
    }

    private fun bitbucketPipelinesEnv() = mapOf(
        "BITBUCKET_BUILD_NUMBER" to "42",
        "BITBUCKET_GIT_HTTP_ORIGIN" to "http://bitbucket.org/my-workspace/$repoSlug",
        "BITBUCKET_GIT_SSH_ORIGIN" to "git@bitbucket.org:my-workspace/$repoSlug.git",
        "BITBUCKET_COMMIT" to COMMIT,
        "BITBUCKET_BRANCH" to "release/1.0",
        "BITBUCKET_REPO_SLUG" to repoSlug,
        "BITBUCKET_REPO_FULL_NAME" to "my-workspace/$repoSlug",
        "BITBUCKET_WORKSPACE" to "my-workspace",
        "BITBUCKET_PIPELINE_UUID" to PIPELINE_UUID,
        "BITBUCKET_STEP_UUID" to "{7d2b1c3a-0e9f-4a8b-9c6d-5e4f3a2b1c0d}",
        "BITBUCKET_CLONE_DIR" to "/opt/atlassian/pipelines/agent/build",
    )

    companion object {
        private const val COMMIT = "7c0b1745f513b9162791651582c0044d7b6d2a83"
        private const val PIPELINE_UUID = "{1c9f0a2e-5b6d-4f3e-8a7b-9c0d1e2f3a4b}"
    }

}
