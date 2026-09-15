package net.nemerosa.ontrack.extension.bitbucket.cloud.config

import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BuildBitbucketPipelineRunProperty
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BuildBitbucketPipelineRunPropertyType
import net.nemerosa.ontrack.extension.config.model.BuildConfiguration
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PropertyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BitbucketPipelinesCIEngineTest {

    private lateinit var propertyService: PropertyService
    private lateinit var engine: BitbucketPipelinesCIEngine

    @BeforeEach
    fun before() {
        propertyService = mockk(relaxed = true)
        engine = BitbucketPipelinesCIEngine(propertyService)
    }

    private val env = mapOf(
        "BITBUCKET_BUILD_NUMBER" to "42",
        "BITBUCKET_GIT_HTTP_ORIGIN" to "http://bitbucket.org/my-workspace/my-repository",
        "BITBUCKET_COMMIT" to "7c0b1745f513b9162791651582c0044d7b6d2a83",
        "BITBUCKET_BRANCH" to "release/1.0",
        "BITBUCKET_REPO_SLUG" to "my-repository",
        "BITBUCKET_WORKSPACE" to "my-workspace",
        "BITBUCKET_PIPELINE_UUID" to "{1c9f0a2e-5b6d-4f3e-8a7b-9c0d1e2f3a4b}",
    )

    @Test
    fun `Name of the engine`() {
        assertEquals("bitbucket-pipelines", engine.name)
    }

    @Test
    fun `Detection on the build number`() {
        assertTrue(engine.matchesEnv(env))
        assertTrue(engine.matchesEnv(mapOf("BITBUCKET_BUILD_NUMBER" to "1")))
        assertFalse(engine.matchesEnv(mapOf("BITBUCKET_BUILD_NUMBER" to "")))
        assertFalse(engine.matchesEnv(mapOf("BUILD_NUMBER" to "1")))
        assertFalse(engine.matchesEnv(emptyMap()))
    }

    @Test
    fun `SCM URL gets a git suffix`() {
        assertEquals("http://bitbucket.org/my-workspace/my-repository.git", engine.getScmUrl(env))
    }

    @Test
    fun `SCM URL already having a git suffix`() {
        assertEquals(
            "https://bitbucket.org/my-workspace/my-repository.git",
            engine.getScmUrl(mapOf("BITBUCKET_GIT_HTTP_ORIGIN" to "https://bitbucket.org/my-workspace/my-repository.git"))
        )
    }

    @Test
    fun `No SCM URL`() {
        assertNull(engine.getScmUrl(emptyMap()))
    }

    @Test
    fun `SCM URL matched by the bitbucket-cloud SCM engine`() {
        val scmEngine = BitbucketCloudSCMEngine(mockk(), mockk(), mockk())
        assertTrue(scmEngine.matchesUrl(engine.getScmUrl(env)!!))
        assertEquals("my-workspace" to "my-repository", scmEngine.getWorkspaceAndRepository(engine.getScmUrl(env)!!))
    }

    @Test
    fun `SCM revision`() {
        assertEquals("7c0b1745f513b9162791651582c0044d7b6d2a83", engine.getScmRevision(env))
        assertNull(engine.getScmRevision(emptyMap()))
    }

    @Test
    fun `Branch name`() {
        assertEquals("release/1.0", engine.getBranchName(env))
    }

    @Test
    fun `Explicit branch name takes precedence`() {
        assertEquals("main", engine.getBranchName(env + ("BRANCH_NAME" to "main")))
    }

    @Test
    fun `Pull request pipeline`() {
        assertEquals("PR-12", engine.getBranchName(env + ("BITBUCKET_PR_ID" to "12")))
    }

    @Test
    fun `Missing branch, for example in a tag pipeline`() {
        assertNull(engine.getBranchName(env - "BITBUCKET_BRANCH"))
        assertEquals("main", engine.getBranchName(env - "BITBUCKET_BRANCH" + ("BRANCH_NAME" to "main")))
    }

    @Test
    fun `Build suffix`() {
        assertEquals("42", engine.getBuildSuffix(env))
        assertEquals("23", engine.getBuildSuffix(env + ("BUILD_NUMBER" to "23")))
    }

    @Test
    fun `Project name`() {
        assertEquals("my-repository", engine.getProjectName(env))
        assertEquals("explicit", engine.getProjectName(env + ("PROJECT_NAME" to "explicit")))
        assertNull(engine.getProjectName(emptyMap()))
    }

    @Test
    fun `Build version`() {
        assertEquals("1.2.3", engine.getBuildVersion(env + ("VERSION" to "1.2.3")))
    }

    @Test
    fun `Pipeline run property`() {
        val build = mockk<Build>()
        engine.configureBuild(build, BuildConfiguration(), env)
        verify {
            propertyService.editProperty(
                build,
                BuildBitbucketPipelineRunPropertyType::class.java,
                BuildBitbucketPipelineRunProperty(
                    workspace = "my-workspace",
                    repository = "my-repository",
                    buildNumber = 42,
                    uuid = "{1c9f0a2e-5b6d-4f3e-8a7b-9c0d1e2f3a4b}",
                    url = "https://bitbucket.org/my-workspace/my-repository/pipelines/results/42",
                )
            )
        }
    }

    @Test
    fun `No pipeline run property without a build number`() {
        val build = mockk<Build>()
        engine.configureBuild(build, BuildConfiguration(), env - "BITBUCKET_BUILD_NUMBER")
        verify(exactly = 0) {
            propertyService.editProperty(build, BuildBitbucketPipelineRunPropertyType::class.java, any())
        }
    }

}
