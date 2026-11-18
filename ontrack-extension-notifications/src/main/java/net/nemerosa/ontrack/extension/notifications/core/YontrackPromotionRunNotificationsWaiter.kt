package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.extension.notifications.processing.NotificationProcessingResultService
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordFilter
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.extension.notifications.sources.EntitySubscriptionNotificationSource
import net.nemerosa.ontrack.extension.notifications.sources.EntitySubscriptionNotificationSourceDataType
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionFilter
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Where the notifications triggered by a promotion run stand.
 */
sealed interface YontrackPromotionRunNotificationsStatus {
    data object Completed : YontrackPromotionRunNotificationsStatus
    data object Ongoing : YontrackPromotionRunNotificationsStatus
    data class Failed(val message: String) : YontrackPromotionRunNotificationsStatus
}

/**
 * Follows the notifications triggered by a promotion run, for the channels waiting for them.
 */
@Component
class YontrackPromotionRunNotificationsWaiter(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val notificationRecordingService: NotificationRecordingService,
    private val eventSubscriptionService: EventSubscriptionService,
    private val entitySubscriptionNotificationSource: EntitySubscriptionNotificationSource,
    private val notificationProcessingResultService: NotificationProcessingResultService,
) {

    fun getStatus(runId: Int, timeout: Duration): YontrackPromotionRunNotificationsStatus =
        securityService.asAdmin {
            val run = structureService.getPromotionRun(ID.of(runId))
            val records = findRecords(run)
            val savedRecords = records.filterNotNull()
            when {
                // No subscription, no record expected
                records.isEmpty() -> YontrackPromotionRunNotificationsStatus.Completed
                // Not all subscriptions have led to notification records yet
                savedRecords.size != records.size -> checkTimeout(
                    run,
                    timeout,
                    YontrackPromotionRunNotificationsStatus.Ongoing
                )

                else -> {
                    // A channel without any dynamic result is known by what has been recorded
                    val types = savedRecords.map { record ->
                        notificationProcessingResultService.getActualizedResult(record.id)?.type
                            ?: record.result.type
                    }
                    when {
                        types.all { it == NotificationResultType.OK } ->
                            YontrackPromotionRunNotificationsStatus.Completed

                        types.any { it.running } -> checkTimeout(
                            run,
                            timeout,
                            YontrackPromotionRunNotificationsStatus.Ongoing
                        )

                        else -> checkTimeout(
                            run,
                            timeout,
                            YontrackPromotionRunNotificationsStatus.Failed(
                                "At least one notification linked to the promotion failed, was not valid or was interrupted"
                            )
                        )
                    }
                }
            }
        }

    private fun checkTimeout(
        run: PromotionRun,
        timeout: Duration,
        status: YontrackPromotionRunNotificationsStatus,
    ): YontrackPromotionRunNotificationsStatus =
        if (Duration.between(run.signature.time, Time.now) > timeout) {
            YontrackPromotionRunNotificationsStatus.Failed("Timeout after $timeout")
        } else {
            status
        }

    private fun findRecords(run: PromotionRun): List<NotificationRecord?> =
        eventSubscriptionService.filterSubscriptions(
            EventSubscriptionFilter(
                entity = run.promotionLevel.toProjectEntityID(),
            )
        ).pageItems // We assume no more than 20 subscriptions for a promotion level
            .map { subscription ->
                notificationRecordingService.filter(
                    filter = NotificationRecordFilter(
                        eventEntityId = ProjectEntityID(
                            type = ProjectEntityType.PROMOTION_RUN,
                            id = run.id()
                        ),
                        sourceId = entitySubscriptionNotificationSource.id,
                        sourceData = mapOf(
                            EntitySubscriptionNotificationSourceDataType::subscriptionName.name to subscription.name,
                        ).asJson(),
                    )
                ).pageItems.firstOrNull()
            }
}
