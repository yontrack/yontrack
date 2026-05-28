package net.nemerosa.ontrack.extension.notifications.core

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.parseDuration
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.extension.notifications.processing.NotificationProcessingResultService
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordFilter
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.extension.notifications.sources.EntitySubscriptionNotificationSource
import net.nemerosa.ontrack.extension.notifications.sources.EntitySubscriptionNotificationSourceDataType
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionFilter
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.json.*
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
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
    private val notificationRecordingService: NotificationRecordingService,
    private val eventSubscriptionService: EventSubscriptionService,
    private val entitySubscriptionNotificationSource: EntitySubscriptionNotificationSource,
    private val notificationProcessingResultService: NotificationProcessingResultService,
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

        val build = securityService.asAdmin { getTargetBuild(config, event, context) }
        val description = eventTemplatingService.renderEvent(event, context, template)

        val promotionLevel = structureService.findPromotionLevelByName(
            project = build.branch.project.name,
            branch = build.branch.name,
            promotionLevel = config.promotion,
        ).getOrNull() ?: return NotificationResult.error("Promotion level not found: ${config.promotion}")

        logger.info("Promoting build ${build.name} to ${promotionLevel.name}...")

        val run = securityService.asAdmin {
            structureService.newPromotionRun(
                PromotionRun.of(
                    build = build,
                    promotionLevel = promotionLevel,
                    signature = securityService.currentSignature,
                    description = description,
                )
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
            ?.parse<YontrackPromotionNotificationChannelOutput>()
        val runId = output
            ?.runId
            ?: return null

        return if (config.waitForPromotion) {

            val run = structureService.getPromotionRun(ID.of(runId))

            val records = findTargetPromotionNotificationRecords(run)

            if (records.isEmpty()) {
                // No subscription record expected
                NotificationResult.ok(output)
            } else {
                val savedRecords = records.filterNotNull()
                if (savedRecords.size != records.size) {
                    // Not all subscriptions have led to notification records yet
                    checkTimeout(
                        config,
                        run,
                        output,
                        NotificationResult.ongoing(output)
                    )
                } else {
                    val actualRecordResults = savedRecords.map { record ->
                        notificationProcessingResultService.getActualizedResult(
                            recordId = record.id,
                        )
                    }
                    if (actualRecordResults.size != savedRecords.size) {
                        checkTimeout(
                            config,
                            run,
                            output,
                            NotificationResult.ongoing(output)
                        )
                    } else {
                        val savedResults = actualRecordResults.filterNotNull()
                        if (savedResults.all { it.type == NotificationResultType.OK }) {
                            NotificationResult.ok(output)
                        } else if (savedResults.any { it.type.running }) {
                            checkTimeout(
                                config,
                                run,
                                output,
                                NotificationResult.ongoing(output)
                            )
                        } else {
                            checkTimeout(
                                config,
                                run,
                                output,
                                NotificationResult.error(
                                    "At least one notification linked to the promotion failed, was not valid or was interrupted",
                                    output
                                )
                            )
                        }
                    }
                }
            }
        } else {
            NotificationResult.ok(output)
        }
    }

    private fun checkTimeout(
        config: YontrackPromotionNotificationChannelConfig,
        run: PromotionRun,
        output: YontrackPromotionNotificationChannelOutput,
        result: NotificationResult<YontrackPromotionNotificationChannelOutput>
    ): NotificationResult<YontrackPromotionNotificationChannelOutput> {
        val runStart = run.signature.time
        val elapsedTime = Duration.between(runStart, Time.now)
        return if (elapsedTime > config.waitForPromotionTimeout) {
            NotificationResult.error(
                "Timeout after ${config.waitForPromotionTimeout}",
                output,
            )
        } else {
            result
        }
    }

    private fun findTargetPromotionNotificationRecords(
        targetRun: PromotionRun,
    ): List<NotificationRecord?> {

        val subscriptions = eventSubscriptionService.filterSubscriptions(
            EventSubscriptionFilter(
                entity = targetRun.promotionLevel.toProjectEntityID(),
            )
        ).pageItems // We assume no more than 20 subscriptions for a promotion level

        // If there is at least one subscription
        return if (subscriptions.isNotEmpty()) {
            securityService.asAdmin {
                subscriptions.map { subscription ->
                    notificationRecordingService.filter(
                        filter = NotificationRecordFilter(
                            eventEntityId = ProjectEntityID(
                                type = ProjectEntityType.PROMOTION_RUN,
                                id = targetRun.id()
                            ),
                            sourceId = entitySubscriptionNotificationSource.id,
                            sourceData = mapOf(
                                EntitySubscriptionNotificationSourceDataType::subscriptionName.name to subscription.name,
                            ).asJson(),
                        )
                    ).pageItems.firstOrNull()
                }
            }
        } else {
            emptyList()
        }
    }

    internal fun getTargetBuild(
        config: YontrackPromotionNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>
    ): Build {
        val buildName = config.build?.run { eventTemplatingService.renderEvent(event, context, this) }
        val branchName = config.branch?.run { eventTemplatingService.renderEvent(event, context, this) }
        val projectName = config.project?.run { eventTemplatingService.renderEvent(event, context, this) }
        return yontrackBuildNotificationHelper.getBuild(
            event = event,
            projectName = projectName,
            branchName = branchName,
            buildName = buildName,
        )
    }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            YontrackPromotionNotificationChannelConfig::promotion.name to text
        ).asJson()

    override val type: String = "yontrack-promotion"
    override val displayName: String = "Yontrack promotion"
    override val enabled: Boolean = true
}