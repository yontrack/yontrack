package net.nemerosa.ontrack.extension.queue

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty
import org.springframework.stereotype.Component
import kotlin.math.abs

@Component
@ConfigurationProperties(prefix = QueueConfigProperties.PREFIX)
@APIName("Queues configuration")
@APIDescription("General configuration for the RabbitMQ queues.")
class QueueConfigProperties {

    /**
     * General properties
     */
    var general = GeneralProperties()

    /**
     * Specific properties
     */
    val specific = mutableMapOf<String, SpecificProperties>()

    /**
     * Processing properties
     */
    abstract class ProcessingProperties {
        var async: Boolean = true
    }

    /**
     * General properties
     */
    class GeneralProperties : ProcessingProperties() {
        @APIDescription("Emits a warning if the queues are not asynchronous")
        var warnIfSync: Boolean = true

        /**
         * Former, misnamed, name of [warnIfSync], kept as an alias until V7 and reported as deprecated
         * by [QueueDeprecatedConfigurationProperties].
         */
        @APIDescription("Deprecated alias of warnIfSync")
        @Deprecated("Removed in V7. Use warnIfSync instead. See #1923")
        @get:DeprecatedConfigurationProperty(
            replacement = "ontrack.extension.queue.general.warn-if-sync",
            since = "6.0",
        )
        var warnIfAsync: Boolean
            get() = warnIfSync
            set(value) {
                warnIfSync = value
            }
    }

    /**
     * Specific properties
     */
    class SpecificProperties : ProcessingProperties() {
        @APIDescription("Number of queues")
        var scale: Int = 1
    }

    /**
     * Gets thr routing key for a message.
     */
    fun <T : Any> getRoutingKey(
        queueProcessor: QueueProcessor<T>,
        payload: T
    ): String =
        queueProcessor.getSpecificRoutingKey(payload)
            ?: getGeneralRoutingKey(queueProcessor, payload)

    private fun <T : Any> getGeneralRoutingKey(
        queueProcessor: QueueProcessor<T>,
        payload: T
    ): String {
        val prefix = queueProcessor.queueRoutingPrefix
        val scale = getQueueProcessorScale(queueProcessor)
        return if (scale > 1) {
            val identifier = queueProcessor.getRoutingIdentifier(payload)
            val code = abs(identifier.hashCode()) % scale
            "$prefix.$code"
        } else {
            "$prefix.0"
        }
    }

    fun getQueueProcessorScale(queueProcessor: QueueProcessor<*>) =
        specific[queueProcessor.id]?.scale ?: queueProcessor.defaultScale ?: 1

    companion object {
        const val PREFIX = "ontrack.extension.queue"
    }
}