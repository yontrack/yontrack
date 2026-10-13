@file:Suppress("DEPRECATION")

package net.nemerosa.ontrack.rabbitmq

import com.rabbitmq.client.Channel
import com.rabbitmq.client.ShutdownSignalException
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * Deletes the [old queues and exchanges][LegacyQueues] once no backend uses them anymore.
 *
 * During a rolling upgrade, a backend of the older version still consumes from its queues and
 * publishes to its exchanges: a queue is deleted only when it has no consumer (`if-unused`),
 * whatever its pending messages, which are abandoned, and an exchange only when no queue is bound
 * to it anymore. Once everything is gone, every run is a no-op.
 */
@Deprecated("Removed in V7. No replacement. See #2091")
@Component
class LegacyQueueCleanupJob(
    private val legacyQueues: ObjectProvider<LegacyQueues>,
    private val connectionFactory: ConnectionFactory,
) : JobProvider {

    private val logger = LoggerFactory.getLogger(LegacyQueueCleanupJob::class.java)

    override fun getStartingJobs(): Collection<JobRegistration> = listOf(
        JobRegistration(
            job = createLegacyQueueCleanupJob(),
            schedule = Schedule.everyMinutes(10),
        )
    )

    private fun createLegacyQueueCleanupJob() = object : Job {

        override fun getKey(): JobKey =
            JobCategory.CORE.getType("rabbitmq").withName("RabbitMQ").getKey("legacy-queue-cleanup")

        override fun getTask() = JobRun {
            cleanup()
        }

        override fun getDescription(): String = "Legacy queue cleanup"

        override fun isDisabled(): Boolean = false
    }

    /**
     * Deletes the unused old queues, then the unused old exchanges.
     */
    fun cleanup(): LegacyQueueCleanupResult {
        val contributions = legacyQueues.orderedStream().toList()
        val queues = contributions.flatMap { it.legacyQueues }.distinct()
        val exchanges = contributions.flatMap { it.legacyExchanges }.distinct()

        val deletedQueues = mutableListOf<String>()
        val keptQueues = mutableListOf<String>()
        queues.forEach { queue ->
            // Null when the queue does not exist
            val consumers = onChannel { it.queueDeclarePassive(queue).consumerCount } ?: return@forEach
            // Checked first, so that the broker does not log the refusal of the deletion
            if (consumers == 0 && onChannel { it.queueDelete(queue, true, false) } != null) {
                logger.info("Legacy queue deleted: {}", queue)
                deletedQueues += queue
            } else {
                logger.debug("Legacy queue kept, still consumed: {}", queue)
                keptQueues += queue
            }
        }

        val deletedExchanges = mutableListOf<String>()
        val keptExchanges = mutableListOf<String>()
        exchanges.filter { exchange -> onChannel { it.exchangeDeclarePassive(exchange) } != null }.forEach { exchange ->
            // While an old queue is still consumed, a backend of the previous version still binds
            // the old exchanges: not tried, so that the broker does not log the refusal of the
            // deletion. An exchange still bound to a queue none of the contributions knows is
            // tried, and refused, at every run.
            if (keptQueues.isEmpty() && onChannel { it.exchangeDelete(exchange, true) } != null) {
                logger.info("Legacy exchange deleted: {}", exchange)
                deletedExchanges += exchange
            } else {
                logger.debug("Legacy exchange kept, still bound: {}", exchange)
                keptExchanges += exchange
            }
        }

        return LegacyQueueCleanupResult(
            deletedQueues = deletedQueues,
            keptQueues = keptQueues,
            deletedExchanges = deletedExchanges,
            keptExchanges = keptExchanges,
        )
    }

    /**
     * Runs an operation on a channel of its own, since the broker closes the channel of an
     * operation it refuses, an absent queue or exchange, or one still in use.
     *
     * Any other failure, of the connection to the broker, fails the run.
     *
     * @return The result of the operation, null if the broker refused it
     */
    private fun <T : Any> onChannel(operation: (Channel) -> T): T? =
        try {
            connectionFactory.createConnection().createChannel(false).use(operation)
        } catch (e: IOException) {
            val signal = e.cause as? ShutdownSignalException
            if (signal != null && !signal.isHardError) {
                null
            } else {
                throw e
            }
        }

}
