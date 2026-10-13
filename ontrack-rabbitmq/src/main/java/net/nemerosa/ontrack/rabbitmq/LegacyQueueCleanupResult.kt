package net.nemerosa.ontrack.rabbitmq

/**
 * Outcome of a run of the [LegacyQueueCleanupJob].
 *
 * @property deletedQueues Old queues deleted by this run
 * @property keptQueues Old queues kept because a consumer still uses them
 * @property deletedExchanges Old exchanges deleted by this run
 * @property keptExchanges Old exchanges kept because a queue is still bound to them
 */
@Deprecated("Removed in V7. No replacement. See #2091")
data class LegacyQueueCleanupResult(
    val deletedQueues: List<String>,
    val keptQueues: List<String>,
    val deletedExchanges: List<String>,
    val keptExchanges: List<String>,
)
