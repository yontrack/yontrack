package net.nemerosa.ontrack.extension.gitlab.config

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.nemerosa.ontrack.extension.config.model.BuildConfiguration
import net.nemerosa.ontrack.extension.gitlab.property.BuildGitLabPipelineRunProperty
import net.nemerosa.ontrack.extension.gitlab.property.BuildGitLabPipelineRunPropertyType
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PropertyService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitLabCIEngineTest {

    private lateinit var propertyService: PropertyService
    private lateinit var engine: GitLabCIEngine

    @BeforeEach
    fun before() {
        propertyService = mockk(relaxed = true)
        engine = GitLabCIEngine(propertyService)
    }

    private val env = mapOf(
        "GITLAB_CI" to "true",
        "CI_PROJECT_URL" to "https://gitlab.com/nemerosa/yontrack",
        "CI_PROJECT_PATH" to "nemerosa/yontrack",
        "CI_PROJECT_NAME" to "yontrack",
        "CI_COMMIT_SHA" to COMMIT,
        "CI_COMMIT_REF_NAME" to "release/1.0",
        "CI_PIPELINE_ID" to "1234567",
        "CI_PIPELINE_IID" to "42",
        "CI_PIPELINE_URL" to "https://gitlab.com/nemerosa/yontrack/-/pipelines/1234567",
        // Never to be used: it carries the job token.
        "CI_REPOSITORY_URL" to "https://gitlab-ci-token:$JOB_TOKEN@gitlab.com/nemerosa/yontrack.git",
    )

    @Test
    fun `Name of the engine`() {
        assertEquals("gitlab-ci", engine.name)
    }

    @Test
    fun `Detection on GITLAB_CI`() {
        assertTrue(engine.matchesEnv(env))
        assertTrue(engine.matchesEnv(mapOf("GITLAB_CI" to "true")))
        assertFalse(engine.matchesEnv(mapOf("GITLAB_CI" to "false")))
        assertFalse(engine.matchesEnv(mapOf("GITLAB_CI" to "")))
        assertFalse(engine.matchesEnv(emptyMap()))
    }

    @Test
    fun `SCM URL gets a git suffix`() {
        assertEquals("https://gitlab.com/nemerosa/yontrack.git", engine.getScmUrl(env))
    }

    @Test
    fun `SCM URL already having a git suffix`() {
        assertEquals(
            "https://gitlab.com/nemerosa/yontrack.git",
            engine.getScmUrl(mapOf("CI_PROJECT_URL" to "https://gitlab.com/nemerosa/yontrack.git"))
        )
    }

    @Test
    fun `No SCM URL`() {
        assertNull(engine.getScmUrl(emptyMap()))
        assertNull(engine.getScmUrl(mapOf("CI_PROJECT_URL" to "")))
    }

    /**
     * `CI_REPOSITORY_URL` embeds `gitlab-ci-token:<job token>`: using it would write a credential into the
     * project property, so it is never read - not even as a fallback when `CI_PROJECT_URL` is missing.
     */
    @Test
    fun `The job token of CI_REPOSITORY_URL never reaches the SCM URL`() {
        assertFalse(engine.getScmUrl(env)!!.contains(JOB_TOKEN))
        assertFalse(engine.getScmUrl(env)!!.contains("gitlab-ci-token"))
        assertNull(engine.getScmUrl(env - "CI_PROJECT_URL"))
    }

    @Test
    fun `SCM URL matched by the gitlab SCM engine`() {
        assertEquals(
            "nemerosa/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.com", engine.getScmUrl(env)!!)
        )
    }

    @Test
    fun `Self-managed instance`() {
        val selfManaged = env + ("CI_PROJECT_URL" to "https://gitlab.dev.yontrack.com/nemerosa/tools/yontrack")
        assertEquals("https://gitlab.dev.yontrack.com/nemerosa/tools/yontrack.git", engine.getScmUrl(selfManaged))
        assertEquals(
            "nemerosa/tools/yontrack",
            GitLabSCMUrl.projectPath("https://gitlab.dev.yontrack.com", engine.getScmUrl(selfManaged)!!)
        )
    }

    @Test
    fun `SCM revision`() {
        assertEquals(COMMIT, engine.getScmRevision(env))
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

    /**
     * `CI_COMMIT_REF_NAME` is the source branch in a merge request pipeline, so the merge request IID is
     * what makes the pipeline recognisable - and rejected, like on the other engines.
     */
    @Test
    fun `Merge request pipeline`() {
        assertEquals("PR-12", engine.getBranchName(env + ("CI_MERGE_REQUEST_IID" to "12")))
        assertEquals("release/1.0", engine.getBranchName(env + ("CI_MERGE_REQUEST_IID" to "")))
    }

    /**
     * `CI_COMMIT_BRANCH` is absent from merge request and tag pipelines, which is why `CI_COMMIT_REF_NAME`
     * is the variable being read.
     */
    @Test
    fun `Tag pipeline uses the ref name`() {
        assertEquals("1.0.0", engine.getBranchName(env + ("CI_COMMIT_REF_NAME" to "1.0.0")))
        assertNull(engine.getBranchName(env - "CI_COMMIT_REF_NAME"))
    }

    @Test
    fun `Build suffix`() {
        assertEquals("42", engine.getBuildSuffix(env))
        assertEquals("23", engine.getBuildSuffix(env + ("BUILD_NUMBER" to "23")))
    }

    @Test
    fun `Project name`() {
        assertEquals("yontrack", engine.getProjectName(env))
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
                BuildGitLabPipelineRunPropertyType::class.java,
                BuildGitLabPipelineRunProperty(
                    projectPath = "nemerosa/yontrack",
                    pipelineId = 1234567L,
                    pipelineIid = 42,
                    url = "https://gitlab.com/nemerosa/yontrack/-/pipelines/1234567",
                )
            )
        }
    }

    @Test
    fun `No pipeline run property without a pipeline ID`() {
        val build = mockk<Build>()
        engine.configureBuild(build, BuildConfiguration(), env - "CI_PIPELINE_ID")
        verify(exactly = 0) {
            propertyService.editProperty(build, BuildGitLabPipelineRunPropertyType::class.java, any())
        }
    }

    @Test
    fun `No pipeline run property without a pipeline URL`() {
        val build = mockk<Build>()
        engine.configureBuild(build, BuildConfiguration(), env - "CI_PIPELINE_URL")
        verify(exactly = 0) {
            propertyService.editProperty(build, BuildGitLabPipelineRunPropertyType::class.java, any())
        }
    }

    /**
     * The property is written from `CI_PIPELINE_URL` and `CI_PROJECT_PATH` only: no job token can reach it.
     */
    @Test
    fun `The job token of CI_REPOSITORY_URL never reaches the pipeline run property`() {
        val build = mockk<Build>()
        val slot = slot<BuildGitLabPipelineRunProperty>()
        engine.configureBuild(build, BuildConfiguration(), env)
        verify {
            propertyService.editProperty(
                build,
                BuildGitLabPipelineRunPropertyType::class.java,
                capture(slot),
            )
        }
        assertFalse(slot.captured.url.contains(JOB_TOKEN))
        assertFalse(slot.captured.url.contains("gitlab-ci-token"))
        assertFalse(slot.captured.projectPath.contains(JOB_TOKEN))
    }

    companion object {
        private const val COMMIT = "7c0b1745f513b9162791651582c0044d7b6d2a83"
        private const val JOB_TOKEN = "glcbt-secret-job-token"
    }
}
