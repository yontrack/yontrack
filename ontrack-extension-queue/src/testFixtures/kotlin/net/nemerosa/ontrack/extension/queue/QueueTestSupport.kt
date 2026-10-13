package net.nemerosa.ontrack.extension.queue

import net.nemerosa.ontrack.extension.queue.record.QueueRecord
import net.nemerosa.ontrack.extension.queue.record.QueueRecordQueryFilter
import net.nemerosa.ontrack.extension.queue.record.QueueRecordState
import net.nemerosa.ontrack.extension.queue.record.QueueRecordingsExtension
import net.nemerosa.ontrack.extension.recordings.RecordingsQueryService
import net.nemerosa.ontrack.extension.recordings.RecordingsTestSupport
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class QueueTestSupport(
    private val queueConfigProperties: QueueConfigProperties,
    private val recordingsTestSupport: RecordingsTestSupport,
    private val queueRecordingsExtension: QueueRecordingsExtension,
    private val recordingsQueryService: RecordingsQueryService,
    private val securityService: SecurityService,
) {

    fun withSyncQueuing(code: () -> Unit) {
        val old = queueConfigProperties.general.async
        try {
            queueConfigProperties.general.async = false
            code()
        } finally {
            queueConfigProperties.general.async = old
        }
    }

    /**
     * Waits until every message dispatched to a processor has been processed: none of its records is
     * left in a state which is not [final][QueueRecordState.isFinal].
     *
     * A message is recorded when it is dispatched: what the caller dispatched before this call is
     * waited for.
     *
     * @param processor ID of the processor
     */
    fun awaitProcessing(processor: String, timeout: Duration = Duration.ofSeconds(60)) {
        val deadline = System.currentTimeMillis() + timeout.toMillis()
        while (pending(processor) > 0) {
            check(System.currentTimeMillis() < deadline) {
                "Messages of the $processor queue still not processed after $timeout"
            }
            Thread.sleep(100)
        }
    }

    private fun pending(processor: String): Int =
        securityService.asAdmin {
            QueueRecordState.entries.filter { !it.isFinal }.sumOf { state ->
                recordingsQueryService.countByFilter(
                    queueRecordingsExtension,
                    QueueRecordQueryFilter(processor = processor, state = state),
                )
            }
        }

    fun record(
            state: QueueRecordState = QueueRecordState.COMPLETED,
            processor: String = "test",
    ): QueueRecord {
        val record = QueueTestFixtures.queueRecord(
                state = state,
                processor = processor,
        )
        recordingsTestSupport.record(
                queueRecordingsExtension,
                record
        )
        return record
    }

}
