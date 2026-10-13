package net.nemerosa.ontrack.extension.github.ingestion.queue

import net.nemerosa.ontrack.extension.github.ingestion.IngestionConfigProperties
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Repository
import org.springframework.amqp.core.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.math.abs

/**
 * Configuration of the queues.
 *
 * Each queue is declared with a single active consumer, so that the events of a repository are
 * processed in order, whatever the number of backends.
 */
@Configuration
class AsyncIngestionHookQueueConfig(
    private val ingestionConfigProperties: IngestionConfigProperties,
) {

    @Bean
    fun gitHubIngestionTopicBindings(): Declarables {
        val declarables = mutableListOf<Declarable>()
        // Topic exchange for the ingestion
        val exchange = DirectExchange(TOPIC).apply {
            declarables += this
        }
        // Repository-specific queues
        ingestionConfigProperties.processing.repositories.forEach { (name, _) ->
            // The queue
            val queue = queue("$QUEUE_PREFIX.$name")
            // The binding
            val binding = BindingBuilder
                .bind(queue)
                .to(exchange)
                .with(name)
            // Adding them all
            declarables += queue
            declarables += binding
        }
        // Default queues
        if (ingestionConfigProperties.processing.scale > 1) {
            (1..ingestionConfigProperties.processing.scale).forEach { no ->
                val index = no - 1 // Starting at 0
                val queue = queue("$QUEUE_PREFIX.$DEFAULT.$index")
                val binding = BindingBuilder
                    .bind(queue)
                    .to(exchange)
                    .with("$DEFAULT.$index")
                declarables += queue
                declarables += binding
            }
        } else {
            val defaultQueue = queue("$QUEUE_PREFIX.$DEFAULT").apply {
                declarables += this
            }
            // Catch-all binding
            declarables += BindingBuilder
                .bind(defaultQueue)
                .to(exchange)
                .with(DEFAULT)
        }
        // OK
        return Declarables(declarables)
    }

    /**
     * Durable queue (kept alive for reprocessing), with one consumer at a time
     */
    private fun queue(name: String): Queue = QueueBuilder.durable(name).singleActiveConsumer().build()

    companion object {

        /**
         * Getting the routing key for a given payload
         */
        fun getRoutingKey(ingestionConfigProperties: IngestionConfigProperties, repository: Repository?): String =
            getRepositorySpecificRoutingKey(ingestionConfigProperties, repository)
                ?: if (ingestionConfigProperties.processing.scale > 1) {
                    getRepositoryDefaultRoutingKey(ingestionConfigProperties, repository)
                } else {
                    DEFAULT
                }

        /**
         * Computing the specific queue to use for a given repository name
         */
        private fun getRepositorySpecificRoutingKey(
            ingestionConfigProperties: IngestionConfigProperties,
            repository: Repository?,
        ): String? = repository?.let {
            ingestionConfigProperties.processing.repositories.entries.find { (name, config) ->
                config.matches(owner = repository.owner.login, repository = repository.name)
            }?.key
        }

        /**
         * Computing the index of the default queue to use for a given repository name
         */
        private fun getRepositoryDefaultRoutingKey(
            ingestionConfigProperties: IngestionConfigProperties,
            repository: Repository?,
        ): String =
            if (repository != null) {
                val code = abs(repository.fullName.hashCode()) % ingestionConfigProperties.processing.scale
                "$DEFAULT.$code"
            } else {
                "$DEFAULT.0"
            }

        /**
         * Topic exchange name.
         *
         * Distinct from the [LEGACY_TOPIC] of the queues without a single active consumer, so
         * that, during a rolling upgrade, a message is published either to the old queues or to
         * the new ones, never to both.
         */
        const val TOPIC = "github.ingestion.v2"

        /**
         * Former topic exchange name, also prefix of the former queues
         */
        @Deprecated("Removed in V7. No replacement. See #2091")
        const val LEGACY_TOPIC = "github.ingestion"

        /**
         * Prefix for the queue names
         */
        const val QUEUE_PREFIX = TOPIC

        /**
         * Default queuing
         */
        const val DEFAULT = "default"

    }

}