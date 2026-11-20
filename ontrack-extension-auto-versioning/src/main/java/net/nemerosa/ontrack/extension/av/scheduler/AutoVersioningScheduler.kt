package net.nemerosa.ontrack.extension.av.scheduler

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.TimeServer
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntry
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.av.metrics.AutoVersioningMetricsService
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningQueuePayload
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningQueueProcessor
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningQueueSourceData
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningQueueSourceExtension
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.extension.queue.source.createQueueSource
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.SecurityService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class AutoVersioningScheduler(
    private val autoVersioningAuditQueryService: AutoVersioningAuditQueryService,
    private val autoVersioningAuditService: AutoVersioningAuditService,
    private val queueDispatcher: QueueDispatcher,
    private val queueProcessor: AutoVersioningQueueProcessor,
    private val queueSourceExtension: AutoVersioningQueueSourceExtension,
    private val metrics: AutoVersioningMetricsService,
    private val securityService: SecurityService,
) {

    private val logger: Logger = LoggerFactory.getLogger(AutoVersioningScheduler::class.java)
    private val timeServer: TimeServer = Time

    /**
     * Schedules the orders ready now.
     *
     * Needs the global [ApplicationManagement] function: the `AutoVersioningSchedulerJob` runs it as
     * an administrator.
     */
    fun schedule() {
        schedule(timeServer.now)
    }

    /**
     * Schedules the orders ready at the given [time].
     *
     * Needs the global [ApplicationManagement] function.
     */
    fun schedule(time: LocalDateTime) {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        logger.info("Launching scheduling for time = $time")
        // Gets all the queued orders
        val entries = autoVersioningAuditQueryService.findByReady(time = time)
        // Logging
        logger.info("Found ${entries.size} entries to schedule")
        // Schedules each entry
        entries.forEach { entry -> scheduleEntry(entry, time) }
    }

    fun scheduleEntry(
        entry: AutoVersioningAuditEntry,
    ) {
        scheduleEntry(entry, timeServer.now)
    }

    fun scheduleEntry(
        entry: AutoVersioningAuditEntry,
        time: LocalDateTime,
    ) {
        logger.info("Scheduling [${entry.order.uuid}] entry scheduled...")

        // Last check on state
        if (entry.mostRecentState.state != AutoVersioningAuditState.CREATED &&
            entry.mostRecentState.state != AutoVersioningAuditState.PENDING_SCHEDULE) {
            logger.warn("Entry [${entry.order.uuid}] already scheduled, processing or processed. Skipping.")
            metrics.onScheduledCancelled(entry.order)
            return
        }

        // Last check on time
        if (entry.order.schedule != null && entry.order.schedule > time) {
            logger.warn("Entry [${entry.order.uuid}] scheduled in the future. Skipping.")
            metrics.onScheduledCancelled(entry.order)
            return
        }

        // OK for scheduling
        metrics.onScheduled(entry.order)

        // Sending the request on the queue
        logger.info("Queuing [${entry.order.uuid}] entry...")
        queueDispatcher.dispatch(
            queueProcessor = queueProcessor,
            payload = AutoVersioningQueuePayload(
                order = entry.order,
            ),
            source = queueSourceExtension.createQueueSource(
                AutoVersioningQueueSourceData(
                    orderUuid = entry.order.uuid,
                )
            ),
            routingFeedback = { routingKey ->
                logger.info("Entry [${entry.order.uuid}] scheduled on routing key [$routingKey]")
                autoVersioningAuditService.onScheduled(
                    order = entry.order,
                    routing = routingKey,
                )
                metrics.onQueuing(entry.order, routingKey)
            }
        )
    }

}