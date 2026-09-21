package net.nemerosa.ontrack.extension.github.notifications

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClient
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.client.WorkflowRun
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchEnum
import net.nemerosa.ontrack.json.patchInt
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationLink
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.utils.patchList
import org.springframework.stereotype.Component

@APIDescription("This channel is used to trigger remote GItHub Actions workflows with some inputs.")
@Documentation(GitHubWorkflowNotificationChannelConfig::class)
@Documentation(GitHubWorkflowNotificationChannelOutput::class, section = "output")
@DocumentationLink(value = "integrations/notifications/github-workflow.md", name = "GitHub workflows")
@NoTemplate
@Component
class GitHubWorkflowNotificationChannel(
    private val gitHubConfigurationService: GitHubConfigurationService,
    private val ontrackGitHubClientFactory: OntrackGitHubClientFactory,
    private val eventTemplatingService: EventTemplatingService,
) : AbstractNotificationChannel<GitHubWorkflowNotificationChannelConfig, GitHubWorkflowNotificationChannelOutput>(
    configClass = GitHubWorkflowNotificationChannelConfig::class,
) {

    override val type: String = "github-workflow"
    override val displayName: String = "GitHub Workflow"
    override val enabled: Boolean = true

    override fun validateParsedConfig(config: GitHubWorkflowNotificationChannelConfig) {
        if (config.config.isBlank()) {
            throw EventSubscriptionConfigException("GitHub workflow configuration name is required")
        } else {
            gitHubConfigurationService.findConfiguration(config.config)
                ?: throw EventSubscriptionConfigException("GitHub configuration ${config.config} could not be found")
        }
        if (config.owner.isBlank()) {
            throw EventSubscriptionConfigException("GitHub workflow owner is required")
        }
        if (config.repository.isBlank()) {
            throw EventSubscriptionConfigException("GitHub workflow repository is required")
        }
        if (config.reference.isBlank()) {
            throw EventSubscriptionConfigException("GitHub workflow reference is required")
        }
        if (config.workflowId.isBlank()) {
            throw EventSubscriptionConfigException("GitHub workflow ID is required")
        }
    }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            GitHubWorkflowNotificationChannelConfig::config.name to text,
        ).asJson()

    override fun mergeConfig(
        a: GitHubWorkflowNotificationChannelConfig,
        changes: JsonNode
    ): GitHubWorkflowNotificationChannelConfig = GitHubWorkflowNotificationChannelConfig(
        config = patchString(changes, a::config),
        owner = patchString(changes, a::owner),
        repository = patchString(changes, a::repository),
        workflowId = patchString(changes, a::workflowId),
        reference = patchString(changes, a::reference),
        inputs = patchList(changes, a::inputs) { it.name },
        callMode = patchEnum(changes, a::callMode),
        timeoutSeconds = patchInt(changes, a::timeoutSeconds),
    )

    override fun publish(
        recordId: String,
        config: GitHubWorkflowNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: GitHubWorkflowNotificationChannelOutput) -> GitHubWorkflowNotificationChannelOutput
    ): NotificationResult<GitHubWorkflowNotificationChannelOutput> {

        // Gets the GitHub configuration
        val gitHubConfig = gitHubConfigurationService.findConfiguration(config.config)
            ?: return NotificationResult.invalidConfiguration("GitHub configuration cannot be found: ${config.config}")

        // Gets the GitHub client
        val gitHubClient = createGitHubClient(gitHubConfig)

        // Rendering all configuration parameters using templates

        // Getting the job parameters
        val owner = eventTemplatingService.render(
            template = config.owner,
            event = event,
            context = context,
            renderer = PlainEventRenderer.INSTANCE,
        )
        val repository = eventTemplatingService.render(
            template = config.repository,
            event = event,
            context = context,
            renderer = PlainEventRenderer.INSTANCE,
        )
        val workflowId = eventTemplatingService.render(
            template = config.workflowId,
            event = event,
            context = context,
            renderer = PlainEventRenderer.INSTANCE,
        )
        val reference = eventTemplatingService.render(
            template = config.reference,
            event = event,
            context = context,
            renderer = PlainEventRenderer.INSTANCE,
        )
        val inputs = config.inputs.map {
            GitHubWorkflowNotificationChannelConfigInput(
                name = it.name,
                value = eventTemplatingService.render(
                    template = it.value,
                    event = event,
                    context = context,
                    renderer = PlainEventRenderer.INSTANCE
                )
            )
        }

        // Calling back the output with actual parameters
        var output = outputProgressCallback(
            GitHubWorkflowNotificationChannelOutput(
                url = gitHubConfig.url,
                owner = owner,
                repository = repository,
                workflowId = workflowId,
                reference = reference,
                inputs = inputs,
            )
        )

        // Running the job
        val (error, runId) = when (config.callMode) {
            GitHubWorkflowNotificationChannelConfigCallMode.ASYNC -> launchAsync(
                gitHubClient = gitHubClient,
                owner = owner,
                repository = repository,
                workflowId = workflowId,
                reference = reference,
                inputs = inputs,
            )

            GitHubWorkflowNotificationChannelConfigCallMode.SYNC -> launchSync(
                gitHubClient = gitHubClient,
                owner = owner,
                repository = repository,
                workflowId = workflowId,
                reference = reference,
                inputs = inputs,
                timeoutSeconds = config.timeoutSeconds,
            ) { run ->
                output = outputProgressCallback(
                    output.withWorkflowRunId(run.id)
                )
            }
        }
        // Output
        if (runId != null) {
            output = outputProgressCallback(
                output.withWorkflowRunId(runId)
            )
        }

        // In case of error
        return if (error != null) {
            NotificationResult.error(
                message = error,
                output = output,
            )
        }
        // OK
        else {
            NotificationResult.ok(output = output)
        }
    }

    private fun launchSync(
        gitHubClient: OntrackGitHubClient,
        owner: String,
        repository: String,
        workflowId: String,
        reference: String,
        inputs: List<GitHubWorkflowNotificationChannelConfigInput>,
        timeoutSeconds: Int,
        runFeedback: (run: WorkflowRun) -> Unit,
    ): GitHubWorkflowRunResult {
        // Launching the workflow and getting the run (id)
        val run = gitHubClient.launchWorkflowRun(
            repository = "$owner/$repository",
            workflow = workflowId,
            branch = reference,
            inputs = inputs.associate { it.name to it.value },
            retries = 10,
            retriesDelaySeconds = 10,
        )
        // Feedback
        runFeedback(run)
        // Waiting for the completion of the run
        val waitIntervalSeconds = 10 // seconds
        val waitRetries = timeoutSeconds / waitIntervalSeconds + 1
        gitHubClient.waitUntilWorkflowRun(
            repository = "$owner/$repository",
            runId = run.id,
            retries = waitRetries,
            retriesDelaySeconds = waitIntervalSeconds,
        )
        // Gets the final result of the workflow run
        val finalRun = gitHubClient.getWorkflowRun(repository = "$owner/$repository", runId = run.id)
        val success = finalRun.success
        return if (success != null && success) {
            GitHubWorkflowRunResult(
                error = null,
                runId = finalRun.id,
            )
        } else {
            GitHubWorkflowRunResult(
                error = "GitHub workflow run was not reported successful.",
                runId = finalRun.id,
            )
        }
    }

    private fun launchAsync(
        gitHubClient: OntrackGitHubClient,
        owner: String,
        repository: String,
        workflowId: String,
        reference: String,
        inputs: List<GitHubWorkflowNotificationChannelConfigInput>
    ): GitHubWorkflowRunResult {
        // Launching the workflow and getting the run (id)
        val run = try {
            gitHubClient.launchWorkflowRun(
                repository = "$owner/$repository",
                workflow = workflowId,
                branch = reference,
                inputs = inputs.associate { it.name to it.value },
                retries = 10,
                retriesDelaySeconds = 10,
            )
        } catch (ex: Exception) {
            return GitHubWorkflowRunResult(
                error = ex.message,
                runId = null,
            )
        }
        // OK
        return GitHubWorkflowRunResult(
            error = null,
            runId = run.id,
        )
    }

    private fun createGitHubClient(gitHubConfig: GitHubEngineConfiguration) =
        ontrackGitHubClientFactory.create(gitHubConfig)

}