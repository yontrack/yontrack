package net.nemerosa.ontrack.extension.github.autoversioning

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingFailureException
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.av.retry.AutoVersioningRetryableException
import net.nemerosa.ontrack.extension.github.client.GitHubWorkflowDispatchException
import net.nemerosa.ontrack.extension.github.client.GitHubWorkflowRunFailedException
import net.nemerosa.ontrack.extension.github.client.GitHubWorkflowRunNotFoundException
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClient
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class GitHubPostProcessingTest {

    @Test
    fun `Templating of the GitHub parameters to the workflow`() {

        val client = mockk<OntrackGitHubClient>(relaxed = true)

        val ontrackGitHubClientFactory = mockk<OntrackGitHubClientFactory>()
        every { ontrackGitHubClientFactory.create(any()) } returns client

        val cachedSettingsService = mockk<CachedSettingsService>()

        val settings = GitHubPostProcessingSettings(
            config = "my-config",
            repository = "repository",
            workflow = "workflow.yml",
            branch = "main",
        )
        every { cachedSettingsService.getCachedSettings(GitHubPostProcessingSettings::class.java) } returns settings

        val gitHubConfigurationService = mockk<GitHubConfigurationService>()

        val gitHubConfig = mockk<GitHubEngineConfiguration>()
        every { gitHubConfigurationService.findConfiguration("my-config") } returns gitHubConfig

        every { gitHubConfig.url } returns "https://github.com"
        every { gitHubConfig.workflowSendId } returns true

        val processing = GitHubPostProcessing(
            extensionFeature = mockk(),
            cachedSettingsService = cachedSettingsService,
            gitHubConfigurationService = gitHubConfigurationService,
            ontrackGitHubClientFactory = ontrackGitHubClientFactory,
        )

        val config = GitHubPostProcessingConfig(
            dockerImage = "docker/image",
            dockerCommand = "command.sh ${'$'}${'$'}{VERSION}",
            commitMessage = "Commit message for version ${'$'}{VERSION}",
            config = null,
            workflow = null,
            parameters = listOf(
                GitHubPostProcessingConfigParam(
                    name = "param1",
                    value = "${'$'}{sourceBuild.release}"
                )
            )
        )

        val order = mockk<AutoVersioningOrder>()
        every { order.targetVersion } returns "1.0.0"

        val avTemplateRenderer = mockk<AutoVersioningTemplateRenderer>()
        every {
            avTemplateRenderer.render(
                "main",
                PlainEventRenderer.INSTANCE
            )
        } returns "main"
        every {
            avTemplateRenderer.render(
                "docker/image",
                PlainEventRenderer.INSTANCE
            )
        } returns "docker/image"
        every {
            avTemplateRenderer.render(
                "Commit message for version ${'$'}{VERSION}",
                PlainEventRenderer.INSTANCE
            )
        } returns "Commit message for version 1.0.0"
        every {
            avTemplateRenderer.render(
                "command.sh ${'$'}${'$'}{VERSION}",
                PlainEventRenderer.INSTANCE
            )
        } returns "command.sh ${'$'}{VERSION}"
        every {
            avTemplateRenderer.render(
                "${'$'}{sourceBuild.release}",
                PlainEventRenderer.INSTANCE
            )
        } returns "my-release"

        processing.postProcessing(
            config = config,
            autoVersioningOrder = order,
            repositoryURI = "uri://repository",
            repository = "repository",
            upgradeBranch = "av/upgrade",
            scm = mockk(),
            avTemplateRenderer = avTemplateRenderer,
        ) {}

        verify {
            client.launchWorkflowRun(
                repository = "repository",
                workflow = "workflow.yml",
                branch = "main",
                inputs = mapOf(
                    "repository" to "repository",
                    "upgrade_branch" to "av/upgrade",
                    "docker_image" to "docker/image",
                    "docker_command" to "command.sh ${'$'}{VERSION}",
                    "commit_message" to "Commit message for version 1.0.0",
                    "version" to "1.0.0",
                    "param1" to "my-release",
                ),
                sendId = true,
                retries = 10,
                retriesDelaySeconds = 30,
            )
        }

    }

    @Test
    fun `Sending the ID defaults to the GitHub configuration`() {
        val client = mockk<OntrackGitHubClient>(relaxed = true)

        runPostProcessing(client, workflowSendId = false)

        verify { client.launchWorkflowRun(any(), any(), any(), any(), sendId = false, any(), any()) }
    }

    @Test
    fun `Sending the ID in the post-processing config overrides the GitHub configuration`() {
        val client = mockk<OntrackGitHubClient>(relaxed = true)

        runPostProcessing(client, sendId = true, workflowSendId = false)

        verify { client.launchWorkflowRun(any(), any(), any(), any(), sendId = true, any(), any()) }
    }

    @Test
    fun `Not sending the ID in the post-processing config overrides the GitHub configuration`() {
        val client = mockk<OntrackGitHubClient>(relaxed = true)

        runPostProcessing(client, sendId = false, workflowSendId = true)

        verify { client.launchWorkflowRun(any(), any(), any(), any(), sendId = false, any(), any()) }
    }

    @Test
    fun `A workflow run completing without success is a post-processing failure carrying the run link`() {
        val client = mockk<OntrackGitHubClient>()
        every { client.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) } returns RUN_ID
        val failure = GitHubWorkflowRunFailedException("repository", RUN_ID)
        every { client.waitUntilWorkflowRun("repository", RUN_ID, any(), any()) } throws failure

        val ex = assertThrows<GitHubPostProcessingFailureException> {
            runPostProcessing(client)
        }

        assertIs<PostProcessingFailureException>(ex)
        assertIs<BaseException>(ex)
        assertEquals(RUN_URL, ex.link)
        assertSame(failure, ex.cause)
    }

    @Test
    fun `A timeout while waiting for the workflow run is a post-processing failure carrying the run link`() {
        val client = mockk<OntrackGitHubClient>()
        every { client.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) } returns RUN_ID
        val timeout = TimeoutException("Waiting for workflow run repository/$RUN_ID - Could not get result in time")
        every { client.waitUntilWorkflowRun("repository", RUN_ID, any(), any()) } throws timeout

        val ex = assertThrows<GitHubPostProcessingFailureException> {
            runPostProcessing(client)
        }

        assertEquals(RUN_URL, ex.link)
        assertSame(timeout, ex.cause)
        assertFalse(ex is AutoVersioningRetryableException, "Waiting too long for a run to complete is not retried")
    }

    @Test
    fun `An HTTP error while waiting for the workflow run is a post-processing failure carrying the run link`() {
        val client = mockk<OntrackGitHubClient>()
        every { client.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) } returns RUN_ID
        val httpError = HttpServerErrorException(HttpStatus.BAD_GATEWAY)
        every { client.waitUntilWorkflowRun("repository", RUN_ID, any(), any()) } throws httpError

        val ex = assertThrows<GitHubPostProcessingFailureException> {
            runPostProcessing(client)
        }

        assertEquals(RUN_URL, ex.link)
        assertSame(httpError, ex.cause)
    }

    @Test
    fun `A failure to launch the workflow run is propagated unchanged`() {
        val client = mockk<OntrackGitHubClient>()
        val launchError = HttpServerErrorException(HttpStatus.BAD_GATEWAY)
        every { client.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) } throws launchError

        val ex = assertThrows<HttpServerErrorException> {
            runPostProcessing(client)
        }

        assertSame(launchError, ex)
        verify(exactly = 0) { client.waitUntilWorkflowRun(any(), any(), any(), any()) }
    }

    @Test
    fun `A dispatch still failing after its retries is a transient failure, eligible for an automatic retry`() {
        val client = mockk<OntrackGitHubClient>()
        val dispatchError = GitHubWorkflowDispatchException(
            repository = "repository",
            workflow = "workflow.yml",
            attempts = 3,
            cause = HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE),
        )
        every { client.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) } throws dispatchError

        val ex = assertThrows<GitHubPostProcessingTransientException> {
            runPostProcessing(client)
        }

        assertIs<AutoVersioningRetryableException>(ex)
        assertSame(dispatchError, ex.cause)
        verify(exactly = 0) { client.waitUntilWorkflowRun(any(), any(), any(), any()) }
    }

    @Test
    fun `A launched workflow run not found in time is a transient failure, eligible for an automatic retry`() {
        val client = mockk<OntrackGitHubClient>()
        val notFound = GitHubWorkflowRunNotFoundException(
            repository = "repository",
            workflow = "workflow.yml",
            branch = "main",
            cause = TimeoutException("Could not get result in time"),
        )
        every { client.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) } throws notFound

        val ex = assertThrows<GitHubPostProcessingTransientException> {
            runPostProcessing(client)
        }

        assertIs<AutoVersioningRetryableException>(ex)
        assertSame(notFound, ex.cause)
    }

    @Test
    fun `A missing GitHub configuration is propagated unchanged`() {
        val client = mockk<OntrackGitHubClient>()

        assertThrows<GitHubPostProcessingConfigException> {
            runPostProcessing(client, ghConfigFound = false)
        }

        verify(exactly = 0) { client.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun runPostProcessing(
        client: OntrackGitHubClient,
        ghConfigFound: Boolean = true,
        sendId: Boolean? = null,
        workflowSendId: Boolean = true,
    ) {
        val ontrackGitHubClientFactory = mockk<OntrackGitHubClientFactory>()
        every { ontrackGitHubClientFactory.create(any()) } returns client

        val cachedSettingsService = mockk<CachedSettingsService>()
        every { cachedSettingsService.getCachedSettings(GitHubPostProcessingSettings::class.java) } returns GitHubPostProcessingSettings(
            config = "my-config",
            repository = "repository",
            workflow = "workflow.yml",
            branch = "main",
        )

        val gitHubConfig = mockk<GitHubEngineConfiguration>()
        every { gitHubConfig.url } returns "https://github.com"
        every { gitHubConfig.workflowSendId } returns workflowSendId
        val gitHubConfigurationService = mockk<GitHubConfigurationService>()
        every { gitHubConfigurationService.findConfiguration("my-config") } returns gitHubConfig.takeIf { ghConfigFound }

        val processing = GitHubPostProcessing(
            extensionFeature = mockk(),
            cachedSettingsService = cachedSettingsService,
            gitHubConfigurationService = gitHubConfigurationService,
            ontrackGitHubClientFactory = ontrackGitHubClientFactory,
        )

        val order = mockk<AutoVersioningOrder>()
        every { order.targetVersion } returns "1.0.0"

        val avTemplateRenderer = mockk<AutoVersioningTemplateRenderer>()
        every { avTemplateRenderer.render(any(), PlainEventRenderer.INSTANCE) } answers { firstArg() }

        processing.postProcessing(
            config = GitHubPostProcessingConfig(
                dockerImage = "docker/image",
                dockerCommand = "command.sh",
                commitMessage = "Post processing",
                config = null,
                workflow = null,
                sendId = sendId,
            ),
            autoVersioningOrder = order,
            repositoryURI = "uri://repository",
            repository = "repository",
            upgradeBranch = "av/upgrade",
            scm = mockk(),
            avTemplateRenderer = avTemplateRenderer,
        ) {}
    }

    companion object {
        private const val RUN_ID = 12345L
        private const val RUN_URL = "https://github.com/repository/actions/runs/12345"
    }

}
