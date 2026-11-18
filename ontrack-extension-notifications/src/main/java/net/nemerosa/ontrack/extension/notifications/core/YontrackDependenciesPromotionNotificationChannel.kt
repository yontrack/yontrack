package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.parseDuration
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.patchBoolean
import net.nemerosa.ontrack.json.patchNullableString
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.PromotionRunFieldValue
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.utils.patchList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.util.regex.PatternSyntaxException
import kotlin.jvm.optionals.getOrNull

@Component
@APIDescription("Promotes the builds linked from the build of the event")
@Documentation(YontrackDependenciesPromotionNotificationChannelConfig::class)
@Documentation(YontrackDependenciesPromotionNotificationChannelOutput::class, section = "output")
class YontrackDependenciesPromotionNotificationChannel(
    private val eventTemplatingService: EventTemplatingService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val targetSelector: YontrackDependenciesPromotionTargetSelector,
    private val notificationsWaiter: YontrackPromotionRunNotificationsWaiter,
) : AbstractNotificationChannel<YontrackDependenciesPromotionNotificationChannelConfig, YontrackDependenciesPromotionNotificationChannelOutput>(
    YontrackDependenciesPromotionNotificationChannelConfig::class
) {

    private val logger: Logger = LoggerFactory.getLogger(YontrackDependenciesPromotionNotificationChannel::class.java)

    override fun validateParsedConfig(config: YontrackDependenciesPromotionNotificationChannelConfig) {
        if (config.promotion.isBlank()) {
            throw EventSubscriptionConfigException("Promotion level name cannot be blank.")
        }
        validateRegex("project", config.project)
        validateRegex("qualifier", config.qualifier)
    }

    private fun validateRegex(field: String, value: String?) {
        try {
            value?.toRegex()
        } catch (ex: PatternSyntaxException) {
            throw EventSubscriptionConfigException("The $field filter is not a valid regular expression: ${ex.message}")
        }
    }

    override fun mergeConfig(
        a: YontrackDependenciesPromotionNotificationChannelConfig,
        changes: JsonNode
    ) = YontrackDependenciesPromotionNotificationChannelConfig(
        project = patchNullableString(changes, a::project),
        // An empty qualifier is a filter of its own, selecting the unqualified links
        qualifier = if (changes.has(a::qualifier.name)) {
            changes.get(a::qualifier.name).takeUnless { it.isNull }?.asString()
        } else {
            a.qualifier
        },
        promotion = patchString(changes, a::promotion),
        fields = patchList(changes, a::fields) { it.name },
        waitForPromotion = patchBoolean(changes, a::waitForPromotion),
        waitForPromotionTimeout = if (changes.has(a::waitForPromotionTimeout.name)) {
            val durationValue = changes.get(a::waitForPromotionTimeout.name).asString()
            parseDuration(durationValue) ?: error("Cannot parse duration: $durationValue")
        } else {
            a.waitForPromotionTimeout
        },
    )

    override fun publish(
        recordId: String,
        config: YontrackDependenciesPromotionNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: YontrackDependenciesPromotionNotificationChannelOutput) -> YontrackDependenciesPromotionNotificationChannelOutput
    ): NotificationResult<YontrackDependenciesPromotionNotificationChannelOutput> {

        val source = event.entities[ProjectEntityType.BUILD] as? Build
            ?: return NotificationResult.error("The event has no build to read the links from.")

        val targets = securityService.asAdmin {
            targetSelector.selectTargets(source, config.project, config.qualifier)
        }

        val description = eventTemplatingService.renderEvent(event, context, template)
        val fields = config.fields.renderFieldValues(eventTemplatingService, event, context)

        var output = YontrackDependenciesPromotionNotificationChannelOutput()
        targets.forEach { target ->
            output = outputProgressCallback(
                YontrackDependenciesPromotionNotificationChannelOutput(
                    targets = output.targets + promote(target, config.promotion, description, fields)
                )
            )
        }

        val waiting = config.waitForPromotion &&
                output.targets.any { it.status == YontrackDependenciesPromotionNotificationChannelTargetStatus.PROMOTED }
        return if (waiting) {
            NotificationResult.async(output)
        } else {
            finalResult(output, targetFailures(output))
        }
    }

    private fun promote(
        target: YontrackDependenciesPromotionTarget,
        promotion: String,
        description: String,
        fields: List<PromotionRunFieldValue>,
    ): YontrackDependenciesPromotionNotificationChannelOutputTarget {
        val build = target.build

        fun entry(
            status: YontrackDependenciesPromotionNotificationChannelTargetStatus,
            runId: Int? = null,
            message: String? = null,
        ) = YontrackDependenciesPromotionNotificationChannelOutputTarget(
            project = build.project.name,
            branch = build.branch.name,
            build = build.name,
            qualifiers = target.qualifiers,
            status = status,
            runId = runId,
            message = message,
        )

        return try {
            securityService.asAdmin("dependencies-promotion-notification") {
                val promotionLevel = structureService.findPromotionLevelByName(
                    project = build.project.name,
                    branch = build.branch.name,
                    promotionLevel = promotion,
                ).getOrNull()
                when {
                    promotionLevel == null -> entry(
                        status = YontrackDependenciesPromotionNotificationChannelTargetStatus.FAILED,
                        message = "Promotion level not found: $promotion",
                    )

                    structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel).isNotEmpty() ->
                        entry(
                            status = YontrackDependenciesPromotionNotificationChannelTargetStatus.SKIPPED,
                            message = "Already promoted to $promotion",
                        )

                    else -> {
                        val run = structureService.newPromotionRun(
                            PromotionRun.of(
                                build = build,
                                promotionLevel = promotionLevel,
                                signature = securityService.currentSignature,
                                description = description,
                            ).withFieldValues(fields)
                        )
                        logger.info("Promoted build ${build.entityDisplayName} to $promotion: ${run.id}")
                        entry(
                            status = YontrackDependenciesPromotionNotificationChannelTargetStatus.PROMOTED,
                            runId = run.id(),
                        )
                    }
                }
            }
        } catch (ex: Exception) {
            // Every target is attempted, whatever happens to the others
            logger.warn("Could not promote build ${build.entityDisplayName} to $promotion", ex)
            entry(
                status = YontrackDependenciesPromotionNotificationChannelTargetStatus.FAILED,
                message = ex.message?.trim()?.replace(WHITESPACES, " ") ?: ex::class.java.name,
            )
        }
    }

    override fun getNotificationResult(
        notificationRecord: NotificationRecord
    ): NotificationResult<YontrackDependenciesPromotionNotificationChannelOutput>? {
        val recorded = notificationRecord.result
        val output = recorded.output
            ?.takeUnless { it.isNull }
            ?.parse<YontrackDependenciesPromotionNotificationChannelOutput>()
        if (recorded.type != NotificationResultType.ASYNC || output == null) {
            // Only the waiting is computed: anything else is what has been recorded
            return NotificationResult(recorded.type, recorded.message, output)
        }

        val config = notificationRecord.channelConfig.parse<YontrackDependenciesPromotionNotificationChannelConfig>()
        val statuses = output.targets.mapNotNull { target ->
            target.runId?.let { runId ->
                target to notificationsWaiter.getStatus(runId, config.waitForPromotionTimeout)
            }
        }

        return if (statuses.any { (_, status) -> status == YontrackPromotionRunNotificationsStatus.Ongoing }) {
            NotificationResult.ongoing(output)
        } else {
            val waitFailures = statuses.mapNotNull { (target, status) ->
                (status as? YontrackPromotionRunNotificationsStatus.Failed)?.let { failure(target, it.message) }
            }
            finalResult(output, targetFailures(output) + waitFailures)
        }
    }

    private fun targetFailures(output: YontrackDependenciesPromotionNotificationChannelOutput): List<String> =
        output.targets
            .filter { it.status == YontrackDependenciesPromotionNotificationChannelTargetStatus.FAILED }
            .map { failure(it, it.message) }

    private fun failure(
        target: YontrackDependenciesPromotionNotificationChannelOutputTarget,
        message: String?,
    ) = "${target.project}/${target.branch}/${target.build}: $message"

    private fun finalResult(
        output: YontrackDependenciesPromotionNotificationChannelOutput,
        failures: List<String>,
    ): NotificationResult<YontrackDependenciesPromotionNotificationChannelOutput> =
        if (failures.isEmpty()) {
            NotificationResult.ok(output)
        } else {
            NotificationResult.error(
                "Some linked builds could not be promoted. ${failures.joinToString("; ")}",
                output,
            )
        }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            YontrackDependenciesPromotionNotificationChannelConfig::promotion.name to text
        ).asJson()

    override val type: String = "yontrack-dependencies-promotion"
    override val displayName: String = "Yontrack dependencies promotion"
    override val enabled: Boolean = true

    private companion object {
        val WHITESPACES = Regex("\\s+")
    }
}
