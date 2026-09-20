package net.nemerosa.ontrack.extension.gitlab.notifications

import net.nemerosa.ontrack.extension.gitlab.AbstractGitLabTestSupport
import net.nemerosa.ontrack.extension.gitlab.GITLAB_TEST_URL
import net.nemerosa.ontrack.extension.gitlab.GitLabTestEnv
import net.nemerosa.ontrack.extension.gitlab.GitLabTestFixture
import net.nemerosa.ontrack.extension.gitlab.TestOnGitLabPipelines
import net.nemerosa.ontrack.extension.gitlab.gitLabTestConfigReal
import net.nemerosa.ontrack.extension.gitlab.gitLabTestEnv
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabPipelineStatuses
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventFactory
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The `gitlab-pipeline` channel against the `mock` job of the real gitlab.com fixture project.
 *
 * Every test here **starts a pipeline** and therefore spends compute minutes out of the fixture namespace's
 * 400 a month, which is why `@TestOnGitLabPipelines` gates them on a switch of their own and only
 * `.github/workflows/gitlab-real.yml` sets it. Three pipelines, each asked to be as short as its assertion
 * allows. See the module's README.
 */
@AsAdminTest
class GitLabPipelineNotificationChannelRealIT : AbstractGitLabTestSupport() {

    @Autowired
    private lateinit var channel: GitLabPipelineNotificationChannel

    @Autowired
    private lateinit var eventFactory: EventFactory

    private val env: GitLabTestEnv get() = gitLabTestEnv

    /**
     * ASYNC returns as soon as GitLab has created the pipeline, so the only thing known about it is what the
     * creation answered: an id, an iid, a URL and a status which is still in flight.
     */
    @TestOnGitLabPipelines
    fun `Async pipeline triggered`() {
        val result = publish(
            result = GitLabTestFixture.RESULT_SUCCESS,
            message = "Async \${project}",
            callMode = GitLabPipelineNotificationChannelConfigCallMode.ASYNC,
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertNotNull(result.output) { output ->
            assertNotNull(output.id, "The pipeline has an id")
            assertNotNull(output.iid, "The pipeline has an iid")
            assertEquals(
                "$GITLAB_TEST_URL/${env.projectPath}/-/pipelines/${output.id}",
                output.url,
            )
            assertTrue(
                output.status?.let { it in GitLabPipelineStatuses.IN_FLIGHT } == true,
                "A pipeline which has just been created is still in flight, got ${output.status}",
            )
            report("async", output)
        }
    }

    @TestOnGitLabPipelines
    fun `Sync pipeline successful`() {
        val result = publish(
            result = GitLabTestFixture.RESULT_SUCCESS,
            message = "Sync \${project}",
            callMode = GitLabPipelineNotificationChannelConfigCallMode.SYNC,
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertEquals(GitLabPipelineStatuses.SUCCESS, result.output?.status)
        result.output?.let { report("sync-success", it) }
    }

    @TestOnGitLabPipelines
    fun `Sync pipeline failing`() {
        val result = publish(
            result = GitLabTestFixture.RESULT_FAILURE,
            message = "Sync failing \${project}",
            callMode = GitLabPipelineNotificationChannelConfigCallMode.SYNC,
        )
        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals("failed", result.output?.status)
        result.output?.let { report("sync-failure", it) }
    }

    /**
     * Triggers the fixture's `mock` job on the fixture project's main branch.
     *
     * `MOCK_DURATION` is left at zero: the job is only there to give the poll something to follow, and the
     * pipeline's own scheduling already takes longer than any sleep worth paying for.
     */
    private fun publish(
        result: String,
        message: String,
        callMode: GitLabPipelineNotificationChannelConfigCallMode,
    ): NotificationResult<GitLabPipelineNotificationChannelOutput> {
        val configuration = realConfiguration()
        var notification: NotificationResult<GitLabPipelineNotificationChannelOutput>? = null
        project {
            notification = channel.publish(
                recordId = "1",
                config = GitLabPipelineNotificationChannelConfig(
                    config = configuration.name,
                    project = env.projectPath,
                    ref = GitLabTestFixture.MAIN_BRANCH,
                    variables = listOf(
                        GitLabPipelineNotificationChannelConfigVariable(
                            GitLabTestFixture.VARIABLE_RESULT,
                            result,
                        ),
                        GitLabPipelineNotificationChannelConfigVariable(
                            GitLabTestFixture.VARIABLE_MESSAGE,
                            message,
                        ),
                    ),
                    callMode = callMode,
                    timeoutSeconds = SYNC_TIMEOUT_SECONDS,
                ),
                event = eventFactory.newProject(this),
                context = emptyMap(),
                template = null,
            ) { it }
        }
        return notification ?: error("No result")
    }

    private fun realConfiguration(): GitLabConfiguration {
        val configuration = gitLabTestConfigReal()
        withDisabledConfigurationTest {
            gitConfigurationService.newConfiguration(configuration)
        }
        return configuration
    }

    /**
     * What the run cost, in the test output: the fixture's budget is the constraint this whole arrangement
     * exists for, and a pipeline nobody can point at is one nobody can account for.
     */
    private fun report(kind: String, output: GitLabPipelineNotificationChannelOutput) {
        println("[gitlab-pipeline] $kind #${output.iid} status=${output.status} ${output.url}")
    }

    companion object {
        /**
         * Generous on purpose: a shared runner on the Free tier can leave a pipeline pending for a while,
         * and a timeout expiring is a test failure which says nothing about the code.
         */
        const val SYNC_TIMEOUT_SECONDS = 600
    }
}
