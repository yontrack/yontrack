package net.nemerosa.ontrack.extension.notifications.core

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.parseDuration
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.*
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.utils.patchList
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
@APIDescription("Promotes a build in Yontrack")
@Documentation(YontrackPromotionNotificationChannelConfig::class)
@Documentation(YontrackPromotionNotificationChannelOutput::class, section = "output")
class YontrackPromotionNotificationChannel(
    private val eventTemplatingService: EventTemplatingService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val yontrackBuildNotificationHelper: YontrackBuildNotificationHelper,
    private val notificationsWaiter: YontrackPromotionRunNotificationsWaiter,
) : AbstractNotificationChannel<YontrackPromotionNotificationChannelConfig, YontrackPromotionNotificationChannelOutput>(
    YontrackPromotionNotificationChannelConfig::class
) {

    private val logger: Logger = LoggerFactory.getLogger(YontrackPromotionNotificationChannel::class.java)

    override fun validateParsedConfig(config: YontrackPromotionNotificationChannelConfig) {
        if (config.promotion.isBlank()) {
            throw EventSubscriptionConfigException("Promotion level name cannot be blank.")
        }
    }

    override fun mergeConfig(
        a: YontrackPromotionNotificationChannelConfig,
        changes: JsonNode
    ): YontrackPromotionNotificationChannelConfig {
        return YontrackPromotionNotificationChannelConfig(
            project = patchNullableString(changes, a::project),
            branch = patchNullableString(changes, a::branch),
            build = patchNullableString(changes, a::build),
            promotion = patchString(changes, a::promotion),
            fields = patchList(changes, a::fields) { it.name },
            waitForPromotion = patchBoolean(changes, a::waitForPromotion),
            waitForPromotionTimeout = if (changes.has(a::waitForPromotionTimeout.name)) {
                val durationValue = changes.get(a::waitForPromotionTimeout.name).asText()
                parseDuration(durationValue) ?: error("Cannot parse duration: $durationValue")
            } else {
                a.waitForPromotionTimeout
            },
        )
    }

    override fun publish(
        recordId: String,
        config: YontrackPromotionNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: YontrackPromotionNotificationChannelOutput) -> YontrackPromotionNotificationChannelOutput
    ): NotificationResult<YontrackPromotionNotificationChannelOutput> {

        val build = try {
            securityService.asAdmin { getTargetBuild(config, event, context) }
        } catch (ex: YontrackPromotionBlankTargetException) {
            return NotificationResult.error(ex.message)
        }
        val description = eventTemplatingService.renderEvent(event, context, template)

        val promotionLevel = structureService.findPromotionLevelByName(
            project = build.branch.project.name,
            branch = build.branch.name,
            promotionLevel = config.promotion,
        ).getOrNull() ?: return NotificationResult.error("Promotion level not found: ${config.promotion}")

        logger.info("Promoting build ${build.name} to ${promotionLevel.name}...")

        val resolvedFields = config.fields.renderFieldValues(eventTemplatingService, event, context)

        val run = securityService.asAdmin("promotion-notification") {
            structureService.newPromotionRun(
                PromotionRun.of(
                    build = build,
                    promotionLevel = promotionLevel,
                    signature = securityService.currentSignature,
                    description = description,
                ).withFieldValues(resolvedFields)
            )
        }

        logger.info("Promoted build ${build.name} to ${promotionLevel.name}: ${run.id}")

        val output = YontrackPromotionNotificationChannelOutput(
            runId = run.id(),
            promotionLevelId = promotionLevel.id(),
        ).run {
            // Early notification of the creation of the promotion run
            outputProgressCallback(this)
        }

        return if (config.waitForPromotion) {
            // Promotion created, waiting for the promotion notifications to be completed
            // will be done asynchronously
            NotificationResult.async(
                output
            )
        } else {
            // Done & done
            NotificationResult.ok(
                output
            )
        }


    }

    override fun getNotificationResult(notificationRecord: NotificationRecord): NotificationResult<YontrackPromotionNotificationChannelOutput>? {
        val config = notificationRecord.channelConfig.parse<YontrackPromotionNotificationChannelConfig>()
        val output = notificationRecord.result.output
            ?.takeUnless { it.isNull }
            ?.parse<YontrackPromotionNotificationChannelOutput>()
        val runId = output
            ?.runId
            ?: return null

        return if (config.waitForPromotion) {
            when (val status = notificationsWaiter.getStatus(runId, config.waitForPromotionTimeout)) {
                YontrackPromotionRunNotificationsStatus.Completed -> NotificationResult.ok(output)
                YontrackPromotionRunNotificationsStatus.Ongoing -> NotificationResult.ongoing(output)
                is YontrackPromotionRunNotificationsStatus.Failed -> NotificationResult.error(status.message, output)
            }
        } else {
            NotificationResult.ok(output)
        }
    }

    internal fun getTargetBuild(
        config: YontrackPromotionNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>
    ): Build {
        val buildName = renderTarget("build", config.build, event, context)
        val branchName = renderTarget("branch", config.branch, event, context)
        val projectName = renderTarget("project", config.project, event, context)
        return yontrackBuildNotificationHelper.getBuild(
            event = event,
            projectName = projectName,
            branchName = branchName,
            buildName = buildName,
        )
    }

    /**
     * Renders a target of the promotion.
     *
     * @return The rendered value, or `null` when the target is not configured, in which case the entity of the event
     * is used
     * @throws YontrackPromotionBlankTargetException When the target is configured but is rendered blank
     */
    private fun renderTarget(
        field: String,
        template: String?,
        event: Event,
        context: Map<String, Any>,
    ): String? =
        template
            ?.takeIf { it.isNotBlank() }
            ?.let { eventTemplatingService.renderEvent(event, context, it) }
            ?.also { rendered ->
                if (rendered.isBlank()) throw YontrackPromotionBlankTargetException(field)
            }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            YontrackPromotionNotificationChannelConfig::promotion.name to text
        ).asJson()

    override val type: String = "yontrack-promotion"
    override val displayName: String = "Yontrack promotion"
    override val enabled: Boolean = true
}

/**
 * A target of the promotion is configured, but its template is rendered blank.
 */
internal class YontrackPromotionBlankTargetException(field: String) : RuntimeException(
    "The $field of the promotion is configured but was rendered blank. Nothing has been promoted."
) {
    override val message: String get() = super.message!!
}
