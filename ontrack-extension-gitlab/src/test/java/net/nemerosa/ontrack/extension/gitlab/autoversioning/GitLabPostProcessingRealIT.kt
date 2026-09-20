package net.nemerosa.ontrack.extension.gitlab.autoversioning

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.gitlab.AbstractGitLabTestSupport
import net.nemerosa.ontrack.extension.gitlab.GITLAB_TEST_URL
import net.nemerosa.ontrack.extension.gitlab.GitLabTestEnv
import net.nemerosa.ontrack.extension.gitlab.GitLabTestFixture
import net.nemerosa.ontrack.extension.gitlab.TestOnGitLabPipelines
import net.nemerosa.ontrack.extension.gitlab.gitLabTestBranch
import net.nemerosa.ontrack.extension.gitlab.gitLabTestConfigReal
import net.nemerosa.ontrack.extension.gitlab.gitLabTestEnv
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventRenderer
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue

/**
 * The `gitlab` auto-versioning post-processing against the `av` job of the real gitlab.com fixture project.
 *
 * It **starts a pipeline**, so it is gated like the channel's real tests on `@TestOnGitLabPipelines` and only
 * `.github/workflows/gitlab-real.yml` runs it. One pipeline, of a few seconds.
 *
 * What it proves is the whole of what Yontrack answers for: the pipeline is triggered on the right ref with
 * the six variables the post-processing promises - the `av` job fails on the first one which is missing - it
 * is waited for, and its URL is reported back as a [PostProcessingInfo].
 *
 * What it does not prove is that the pipeline pushed a commit on the upgrade branch, which the Bitbucket
 * Cloud equivalent does check. That is the pipeline's business rather than Yontrack's, and the fixture would
 * need a write token as a CI/CD variable of its own, with its own rotation, to do it. For the same reason the
 * upgrade branch is a name rather than a branch: nothing reads it, and creating it would cost two API calls
 * and a cleanup for nothing.
 *
 * The failing path - the upgrade command exiting non-zero - is deliberately **not** tested here. The only
 * thing a real pipeline adds over [GitLabPostProcessingTest], which covers the translation into a
 * [GitLabPostProcessingFailureException] against a fake service, is that GitLab really answers `failed`, and
 * `GitLabPipelineNotificationChannelRealIT` already pays a compute minute to establish that.
 */
@AsAdminTest
class GitLabPostProcessingRealIT : AbstractGitLabTestSupport() {

    @Autowired
    private lateinit var postProcessing: GitLabPostProcessing

    private val env: GitLabTestEnv get() = gitLabTestEnv

    @TestOnGitLabPipelines
    fun `Post-processing pipeline runs with the auto-versioning variables`() {
        val configuration = gitLabTestConfigReal()
        withDisabledConfigurationTest {
            gitConfigurationService.newConfiguration(configuration)
        }

        val upgradeBranch = gitLabTestBranch("av-post-processing")
        val version = "2.0.${System.currentTimeMillis() % 100_000}"

        val order = mockk<AutoVersioningOrder>()
        every { order.targetVersion } returns version

        val infos = mutableListOf<PostProcessingInfo>()

        postProcessing.postProcessing(
            config = GitLabPostProcessingConfig(
                dockerImage = "alpine:3.22",
                // What the `av` job runs: `true` is how this test asks the pipeline to succeed.
                dockerCommand = GitLabTestFixture.COMMAND_SUCCESS,
                commitMessage = "Post-processing for $version",
                config = configuration.name,
                project = env.projectPath,
                ref = GitLabTestFixture.MAIN_BRANCH,
            ),
            autoVersioningOrder = order,
            repositoryURI = "$GITLAB_TEST_URL/${env.projectPath}.git",
            repository = env.projectPath,
            upgradeBranch = upgradeBranch,
            scm = mockk(),
            avTemplateRenderer = object : AutoVersioningTemplateRenderer {
                override fun render(template: String, renderer: EventRenderer): String = template
            },
        ) { infos += it }

        val url = infos.single().data["url"] ?: error("No URL reported by the post-processing")
        println("[gitlab-post-processing] $url")
        assertTrue(
            url.startsWith("$GITLAB_TEST_URL/${env.projectPath}/-/pipelines/"),
            "The post-processing reports the pipeline it ran, got $url",
        )
    }
}
