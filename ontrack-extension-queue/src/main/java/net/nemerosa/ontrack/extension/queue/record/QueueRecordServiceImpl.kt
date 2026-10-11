package net.nemerosa.ontrack.extension.queue.record

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.queue.QueuePayload
import net.nemerosa.ontrack.extension.queue.source.QueueSource
import net.nemerosa.ontrack.extension.recordings.RecordingsService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.SecurityService
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class QueueRecordServiceImpl(
    private val queueRecordingsExtension: QueueRecordingsExtension,
    private val recordingsService: RecordingsService,
    private val securityService: SecurityService,
) : QueueRecordService {

    override fun start(queuePayload: QueuePayload, source: QueueSource?) {
        val record = QueueRecord.create(
            queuePayload = queuePayload,
            source = source,
            username = obfuscateUsername(),
        )
        recordingsService.record(queueRecordingsExtension, record)
    }

    private fun obfuscateUsername() = securityService.currentUser?.name?.let {
        if (it == "admin") it else "user"
    }

    override fun setRouting(queuePayload: QueuePayload, routingKey: String) {
        recordingsService.updateRecord(queueRecordingsExtension, queuePayload.id) {
            it.withRoutingKey(routingKey).withState(QueueRecordState.ROUTING_READY)
        }
    }

    override fun sent(queuePayload: QueuePayload) {
        // A consumer may already have received the message: its state is kept
        recordingsService.updateLockedRecord(queueRecordingsExtension, queuePayload.id) {
            if (it.state == QueueRecordState.STARTED || it.state == QueueRecordState.ROUTING_READY) {
                it.withState(QueueRecordState.SENT)
            } else {
                null
            }
        }
    }

    override fun received(queuePayload: QueuePayload, queue: String?): Boolean =
        transition(queuePayload) {
            it.withState(QueueRecordState.RECEIVED).withQueue(queue)
        }

    override fun parsed(queuePayload: QueuePayload, payload: Any) {
        transition(queuePayload) {
            it.withActualPayload(payload.asJson()).withState(QueueRecordState.PARSED)
        }
    }

    override fun cancelled(queuePayload: QueuePayload, cancelReason: String) {
        transition(queuePayload) {
            it.withException(cancelReason).withState(QueueRecordState.CANCELLED)
        }
    }

    override fun processing(queuePayload: QueuePayload) {
        transition(queuePayload) {
            it.withState(QueueRecordState.PROCESSING)
        }
    }

    override fun completed(queuePayload: QueuePayload) {
        transition(queuePayload) {
            it.withState(QueueRecordState.COMPLETED)
                .withEndTime(Time.now())
        }
    }

    override fun errored(queuePayload: QueuePayload, exception: Exception) {
        transition(queuePayload) {
            it.withState(QueueRecordState.ERRORED)
                .withEndTime(Time.now())
                .withException(reducedStackTrace(exception))
        }
    }

    /**
     * Transition of the record of a message on the consumer side, under a lock on the record.
     *
     * A record in a final state is left unchanged: the message was redelivered after its
     * processing was over, or this is the late outcome of an attempt superseded by a redelivery.
     *
     * @return `true` if the record was changed
     */
    private fun transition(queuePayload: QueuePayload, change: (QueueRecord) -> QueueRecord): Boolean =
        recordingsService.updateLockedRecord(queueRecordingsExtension, queuePayload.id) { record ->
            if (record.state.isFinal) {
                null
            } else {
                change(record)
            }
        } != null

    companion object {
        private const val MAX_STACK_HEIGHT = 20

        fun reducedStackTrace(error: Throwable) =
            ExceptionUtils.getStackFrames(error).take(MAX_STACK_HEIGHT).joinToString("\n")
    }

}