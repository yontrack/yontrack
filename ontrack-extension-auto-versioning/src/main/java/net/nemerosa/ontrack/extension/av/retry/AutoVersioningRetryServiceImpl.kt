package net.nemerosa.ontrack.extension.av.retry

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.TimeServer
import net.nemerosa.ontrack.extension.av.audit.*
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEventService
import net.nemerosa.ontrack.extension.av.metrics.AutoVersioningMetricsService
import net.nemerosa.ontrack.extension.av.settings.AutoVersioningSettings
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

@Service
class AutoVersioningRetryServiceImpl(
    private val autoVersioningAuditQueryService: AutoVersioningAuditQueryService,
    private val autoVersioningAuditService: AutoVersioningAuditService,
    private val autoVersioningAuditStore: AutoVersioningAuditStore,
    private val autoVersioningEventService: AutoVersioningEventService,
    private val cachedSettingsService: CachedSettingsService,
    private val metrics: AutoVersioningMetricsService,
    private val timeServer: TimeServer,
) : AutoVersioningRetryService {

    @Autowired
    constructor(
        autoVersioningAuditQueryService: AutoVersioningAuditQueryService,
        autoVersioningAuditService: AutoVersioningAuditService,
        autoVersioningAuditStore: AutoVersioningAuditStore,
        autoVersioningEventService: AutoVersioningEventService,
        cachedSettingsService: CachedSettingsService,
        metrics: AutoVersioningMetricsService,
    ) : this(
        autoVersioningAuditQueryService = autoVersioningAuditQueryService,
        autoVersioningAuditService = autoVersioningAuditService,
        autoVersioningAuditStore = autoVersioningAuditStore,
        autoVersioningEventService = autoVersioningEventService,
        cachedSettingsService = cachedSettingsService,
        metrics = metrics,
        timeServer = Time,
    )

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningRetryServiceImpl::class.java)

    /**
     * Checks if the failure of this [order] would be retried automatically.
     */
    fun willRetry(order: AutoVersioningOrder, error: Throwable): Boolean =
        error.isAutoVersioningRetryable() && isEligible(order, getCreationData(order).retryAttempt)

    override fun getRetryAttempt(order: AutoVersioningOrder): Int =
        getCreationData(order).retryAttempt

    override fun onError(order: AutoVersioningOrder, error: Throwable) {
        val deferred = error as? AutoVersioningDeferredErrorException
        val failure: Throwable = deferred?.cause ?: error
        // Whatever happens when deciding on the retry, the failure must be recorded
        var attempt = 0
        val retried = try {
            val creation = getCreationData(order)
            attempt = creation.retryAttempt
            error.isAutoVersioningRetryable() &&
                    isEligible(order, attempt) &&
                    scheduleRetry(order, failure, creation, attempt)
        } catch (any: Exception) {
            logger.error("Could not decide on the automatic retry of order [${order.uuid}]", any)
            false
        }
        if (!retried) {
            autoVersioningAuditService.onError(order, failure)
            if (deferred != null) {
                try {
                    autoVersioningEventService.sendError(
                        order,
                        autoVersioningErrorMessage(deferred.notificationMessage, attempt),
                        deferred.cause,
                    )
                } catch (any: Exception) {
                    // The failure of the order is already recorded
                    logger.error("Could not notify the failure of order [${order.uuid}]", any)
                }
            }
            if (attempt > 0) {
                metrics.onRetryExhausted(order)
            }
        }
    }

    override fun getLineage(entry: AutoVersioningAuditEntry): AutoVersioningAuditEntryLineage {
        val creation = entry.creationData
        val retry = entry.audit.firstOrNull {
            it.state == AutoVersioningAuditState.ERROR &&
                    it.data.containsKey(AutoVersioningAuditEntryStateDataKeys.RETRY_UUID)
        }?.data ?: emptyMap()
        return AutoVersioningAuditEntryLineage(
            retryAttempt = creation[AutoVersioningAuditEntryStateDataKeys.RETRY_ATTEMPT]?.toIntOrNull(),
            retryMax = creation[AutoVersioningAuditEntryStateDataKeys.RETRY_MAX]?.toIntOrNull(),
            retryOf = creation[AutoVersioningAuditEntryStateDataKeys.RETRY_OF],
            retryOriginal = creation[AutoVersioningAuditEntryStateDataKeys.RETRY_ORIGINAL],
            retryUuid = retry[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID],
            retryAt = Time.fromStorage(retry[AutoVersioningAuditEntryStateDataKeys.RETRY_AT]),
            rescheduledFrom = creation[AutoVersioningAuditEntryStateDataKeys.RESCHEDULED_FROM],
            rescheduledAs = autoVersioningAuditStore.findUUIDsByCreationData(
                targetBranch = entry.order.branch,
                key = AutoVersioningAuditEntryStateDataKeys.RESCHEDULED_FROM,
                value = entry.order.uuid,
            ),
            configuredRetryMaxCount = settings.retryMaxCount,
        )
    }

    private fun isEligible(order: AutoVersioningOrder, attempt: Int): Boolean {
        val max = settings.retryMaxCount
        return max > 0 && attempt < max && !isSuperseded(order)
    }

    /**
     * Schedules the retry of the [order], and returns `true` once the retry is scheduled and the
     * failed order points to it.
     */
    private fun scheduleRetry(
        order: AutoVersioningOrder,
        failure: Throwable,
        creation: Map<String, String>,
        attempt: Int,
    ): Boolean = try {
        val settings = settings
        val retryAttempt = attempt + 1
        val original = creation[AutoVersioningAuditEntryStateDataKeys.RETRY_ORIGINAL] ?: order.uuid
        val retryAt = timeServer.now.plusMinutes(settings.retryDelayMinutes.toLong())
        val retryOrder = order.copy(
            uuid = UUID.randomUUID().toString(),
            schedule = retryAt,
        )
        logger.info(
            "Order [{}] failed on a transient error, retry {}/{} scheduled at {} as [{}]",
            order.uuid, retryAttempt, settings.retryMaxCount, retryAt, retryOrder.uuid
        )
        // The retry waits for its schedule, like any scheduled order
        autoVersioningAuditService.onCreated(
            retryOrder,
            mapOf(
                AutoVersioningAuditEntryStateDataKeys.RETRY_OF to order.uuid,
                AutoVersioningAuditEntryStateDataKeys.RETRY_ORIGINAL to original,
                AutoVersioningAuditEntryStateDataKeys.RETRY_ATTEMPT to retryAttempt.toString(),
                AutoVersioningAuditEntryStateDataKeys.RETRY_MAX to settings.retryMaxCount.toString(),
            )
        )
        autoVersioningAuditService.onPendingSchedule(retryOrder)
        // The failed order points to its retry, once it exists
        autoVersioningAuditService.onError(
            order,
            failure,
            AutoVersioningAuditEntryStateDataKeys.RETRY_UUID to retryOrder.uuid,
            AutoVersioningAuditEntryStateDataKeys.RETRY_AT to Time.store(retryAt),
        )
        metrics.onRetryScheduled(order)
        true
    } catch (any: Exception) {
        // The failure is then recorded & notified as a final one
        logger.error("Could not schedule the automatic retry of order [${order.uuid}]", any)
        false
    }

    /**
     * An order running for the same target would apply a version at least as recent as this one,
     * and retrying this order after it would overwrite it with an older version.
     */
    private fun isSuperseded(order: AutoVersioningOrder): Boolean =
        autoVersioningAuditQueryService.findByFilter(
            AutoVersioningAuditQueryFilter(
                source = order.sourceProject,
                project = order.branch.project.name,
                branch = order.branch.name,
                qualifier = order.qualifier,
                running = true,
                targetPaths = order.allPaths.flatMap { it.paths },
                // This order & at most one other are enough to know
                count = 2,
            )
        ).any { it.order.uuid != order.uuid }

    private fun getCreationData(order: AutoVersioningOrder): Map<String, String> =
        autoVersioningAuditQueryService.findByUUID(order.branch, order.uuid)?.creationData ?: emptyMap()

    private val AutoVersioningAuditEntry.creationData: Map<String, String>
        get() = audit.lastOrNull { it.state == AutoVersioningAuditState.CREATED }?.data ?: emptyMap()

    private val Map<String, String>.retryAttempt: Int
        get() = this[AutoVersioningAuditEntryStateDataKeys.RETRY_ATTEMPT]?.toIntOrNull() ?: 0

    private val settings: AutoVersioningSettings
        get() = cachedSettingsService.getCachedSettings(AutoVersioningSettings::class.java)
}
