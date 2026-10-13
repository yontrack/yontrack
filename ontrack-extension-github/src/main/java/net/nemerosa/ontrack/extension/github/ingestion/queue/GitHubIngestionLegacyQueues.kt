@file:Suppress("DEPRECATION")

package net.nemerosa.ontrack.extension.github.ingestion.queue

import net.nemerosa.ontrack.extension.github.ingestion.IngestionConfigProperties
import net.nemerosa.ontrack.rabbitmq.LegacyQueues
import org.springframework.stereotype.Component

/**
 * Former queues and exchange of the GitHub ingestion, before they had a single active consumer.
 *
 * The scale and the repositories of the previous version are unknown: the old queues are those
 * of the current configuration, plus a probe of the default queues.
 */
@Deprecated("Removed in V7. No replacement. See #2091")
@Component
class GitHubIngestionLegacyQueues(
    ingestionConfigProperties: IngestionConfigProperties,
) : LegacyQueues {

    private val prefix = AsyncIngestionHookQueueConfig.LEGACY_TOPIC
    private val default = AsyncIngestionHookQueueConfig.DEFAULT

    override val legacyQueues: Set<String> =
        setOf("$prefix.$default") +
                (0 until maxOf(LegacyQueues.PROBED_QUEUE_COUNT, ingestionConfigProperties.processing.scale)).map { "$prefix.$default.$it" } +
                ingestionConfigProperties.processing.repositories.keys.map { "$prefix.$it" }

    override val legacyExchanges: Set<String> = setOf(prefix)

}
