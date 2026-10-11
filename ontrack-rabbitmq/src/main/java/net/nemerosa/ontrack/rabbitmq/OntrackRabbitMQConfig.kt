package net.nemerosa.ontrack.rabbitmq

import jakarta.annotation.PostConstruct
import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.amqp.rabbit.config.ContainerCustomizer
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


/**
 * Enabling Rabbit MQ
 */
@Configuration
@EnableRabbit
class OntrackRabbitMQConfig(
    private val rabbitTemplate: RabbitTemplate,
    private val properties: OntrackRabbitMQConfigProperties,
) {

    @PostConstruct
    protected fun init() {
        // make rabbit template to support transactions
        rabbitTemplate.isChannelTransacted = properties.transactional
    }

    /**
     * Draining of the listener containers, applied by Spring Boot to all the containers of its
     * listener container factory, when Yontrack stops - typically on a `SIGTERM`, during a rolling
     * upgrade:
     *
     * - a container stops after the message in hand, and leaves the messages prefetched from the
     *   broker to it, which delivers them to another consumer, instead of processing them first;
     * - it waits for this message for `ontrack.config.rabbitmq.listener-shutdown-timeout`, longer
     *   than the 5 seconds of Spring AMQP, and shorter than `spring.lifecycle.timeout-per-shutdown-phase`.
     */
    @Bean
    fun ontrackListenerContainerCustomizer() = ContainerCustomizer<SimpleMessageListenerContainer> { container ->
        container.setForceStop(true)
        container.setShutdownTimeout(properties.listenerShutdownTimeout.toMillis())
    }

}
