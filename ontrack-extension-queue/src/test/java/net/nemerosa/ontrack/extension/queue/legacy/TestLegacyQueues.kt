package net.nemerosa.ontrack.extension.queue.legacy

import net.nemerosa.ontrack.rabbitmq.LegacyQueues
import org.springframework.stereotype.Component

/**
 * Old queues and exchanges set by the tests.
 */
@Suppress("DEPRECATION")
@Component
class TestLegacyQueues : LegacyQueues {

    override var legacyQueues: Set<String> = emptySet()

    override var legacyExchanges: Set<String> = emptySet()

}
