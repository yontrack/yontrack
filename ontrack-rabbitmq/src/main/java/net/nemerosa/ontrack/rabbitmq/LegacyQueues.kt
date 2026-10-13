package net.nemerosa.ontrack.rabbitmq

/**
 * Contribution of the old queues and exchanges left behind when queues were renamed, which the
 * [LegacyQueueCleanupJob] deletes once no backend of an older version uses them anymore.
 *
 * A name which no longer exists is harmless: the old names may be a probe of the names which
 * could exist.
 */
@Deprecated("Removed in V7. No replacement. See #2091")
interface LegacyQueues {

    /**
     * Names of the old queues
     */
    val legacyQueues: Set<String>

    /**
     * Names of the old exchanges, deleted once their queues are gone
     */
    val legacyExchanges: Set<String>

    companion object {
        /**
         * Number of indexed queues probed when their actual number is unknown
         */
        const val PROBED_QUEUE_COUNT = 32
    }

}
