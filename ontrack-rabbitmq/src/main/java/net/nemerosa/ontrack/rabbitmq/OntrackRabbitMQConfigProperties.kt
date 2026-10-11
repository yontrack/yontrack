package net.nemerosa.ontrack.rabbitmq

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Specific static configuration for RabbitMQ.
 */
@Component
@ConfigurationProperties(prefix = OntrackRabbitMQConfigProperties.PREFIX)
@APIName("RabbitMQ configuration")
@APIDescription("Configuration of the client from Ontrack to Rabbit MQ. Note that basic connection parameters are handled using Spring Boot configuration.")
class OntrackRabbitMQConfigProperties {

    @APIDescription("True (default) to make the sending of messages part of the current transaction.")
    var transactional = true

    @DurationUnit(ChronoUnit.SECONDS)
    @APIDescription("When Yontrack stops, time given to each queue listener to complete the processing of the message in hand (in seconds by default, or as `30s`, `1m`...). The listener takes no new message meanwhile. Past this time, the processing is interrupted, and the message is left to the broker, which delivers it again. Keep it below `spring.lifecycle.timeout-per-shutdown-phase`.")
    var listenerShutdownTimeout: Duration = Duration.ofSeconds(30)

    companion object {
        /**
         * Prefix for the properties
         */
        const val PREFIX = "ontrack.config.rabbitmq"
    }

}
