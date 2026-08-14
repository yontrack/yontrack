package net.nemerosa.ontrack.extension.notifications.core

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchNullableString
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.exceptions.BranchNotFoundException
import net.nemerosa.ontrack.model.exceptions.BuildNotFoundException
import net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Component
@APIDescription("Validates a build in Ontrack")
@Documentation(OntrackValidationNotificationChannelConfig::class)
@Documentation(OntrackValidationNotificationChannelOutput::class, section = "output")
class OntrackValidationNotificationChannel(
    private val eventTemplatingService: EventTemplatingService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val runInfoService: RunInfoService,
) : AbstractNotificationChannel<OntrackValidationNotificationChannelConfig, OntrackValidationNotificationChannelOutput>(
    OntrackValidationNotificationChannelConfig::class
) {

    override fun validateParsedConfig(config: OntrackValidationNotificationChannelConfig) {
        if (config.validation.isBlank()) {
            throw EventSubscriptionConfigException("Validation stamp name cannot be blank.")
        }
    }

    override fun mergeConfig(
        a: OntrackValidationNotificationChannelConfig,
        changes: JsonNode
    ): OntrackValidationNotificationChannelConfig {
        return OntrackValidationNotificationChannelConfig(
            project = patchNullableString(changes, a::project),
            branch = patchNullableString(changes, a::branch),
            build = patchNullableString(changes, a::build),
            validation = patchString(changes, a::validation),
            runTime = patchNullableString(changes, a::runTime),
        )
    }

    /**
     * The validation run and its run info must be created together, so this channel carries its own
     * transaction (the notification processing service does not provide any).
     */
    @Transactional
    override fun publish(
        recordId: String,
        config: OntrackValidationNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: OntrackValidationNotificationChannelOutput) -> OntrackValidationNotificationChannelOutput
    ): NotificationResult<OntrackValidationNotificationChannelOutput> {

        val build = securityService.asAdmin { getTargetBuild(config, event, context) }
        val description = eventTemplatingService.renderEvent(event, context, template)

        val run = securityService.asAdmin {
            structureService.newValidationRun(
                build = build,
                validationRunRequest = ValidationRunRequest(
                    validationStampName = config.validation,
                    validationRunStatusId = ValidationRunStatusID.STATUS_PASSED,
                    description = description,
                )
            )
        }

        if (!config.runTime.isNullOrBlank()) {
            // Evaluates the run time as a template
            val runTime: Int? = renderRunTime(config.runTime, event, context)
            // Setting the run info
            securityService.asAdmin {
                runInfoService.setRunInfo(
                    entity = run,
                    input = RunInfoInput(
                        sourceType = "ontrack-validation",
                        triggerType = "notification",
                        runTime = runTime,
                    )
                )
            }
        }

        return NotificationResult.ok(
            OntrackValidationNotificationChannelOutput(
                runId = run.id(),
            )
        )
    }

    private fun renderRunTime(runTime: String, event: Event, context: Map<String, Any>): Int? {
        // Basic rendering
        val rendered = eventTemplatingService.renderEvent(event, context, template = runTime)
        // If not blank and a number
        return rendered.takeIf { it.isNotBlank() }?.toIntOrNull()
    }

    internal fun getTargetBuild(
        config: OntrackValidationNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>
    ): Build =
        if (config.build.isNullOrBlank()) {
            event.getEntity(ProjectEntityType.BUILD)
        } else {
            val buildName = eventTemplatingService.renderEvent(event, context, config.build)
            val branch = getTargetBranch(config, event, context)
            structureService.findBuildByName(
                branch.project.name,
                branch.name,
                buildName
            ).getOrNull()
                ?: throw BuildNotFoundException(
                    branch.project.name,
                    branch.name,
                    buildName
                )
        }

    internal fun getTargetBranch(
        config: OntrackValidationNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>
    ): Branch =
        if (config.branch.isNullOrBlank()) {
            event.getEntity(ProjectEntityType.BRANCH)
        } else {
            val branchName = eventTemplatingService.renderEvent(event, context, config.branch)
            val project = getTargetProject(config, event, context)
            structureService.findBranchByName(project.name, branchName).getOrNull()
                ?: throw BranchNotFoundException(project.name, branchName)
        }

    internal fun getTargetProject(
        config: OntrackValidationNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>
    ): Project =
        if (config.project.isNullOrBlank()) {
            event.getEntity(ProjectEntityType.PROJECT)
        } else {
            val projectName = eventTemplatingService.renderEvent(event, context, config.project)
            structureService.findProjectByName(projectName).getOrNull()
                ?: throw ProjectNotFoundException(projectName)
        }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            OntrackValidationNotificationChannelConfig::validation.name to text
        ).asJson()

    override val type: String = "ontrack-validation"
    override val displayName: String = "Ontrack validation"
    override val enabled: Boolean = true
}