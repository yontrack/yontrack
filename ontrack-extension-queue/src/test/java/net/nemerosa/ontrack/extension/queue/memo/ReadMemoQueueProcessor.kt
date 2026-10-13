package net.nemerosa.ontrack.extension.queue.memo

import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.model.support.ReadMemo
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Queue processor which records, for each message, whether it was processed in a unit of work,
 * and what this unit had memorised before the message.
 */
@Component
class ReadMemoQueueProcessor : QueueProcessor<ReadMemoQueuePayload> {

    private val memos = ConcurrentHashMap<String, ReadMemoState>()

    /**
     * State of the memo while the message identified by [id] was processed, if processed.
     */
    fun memo(id: String): ReadMemoState? = memos[id]

    override val id: String = "test-read-memo"

    override val payloadType: KClass<ReadMemoQueuePayload> = ReadMemoQueuePayload::class

    override fun isCancelled(payload: ReadMemoQueuePayload): String? = null

    override fun getRoutingIdentifier(payload: ReadMemoQueuePayload): String = payload.id

    override fun process(payload: ReadMemoQueuePayload, queueMetadata: QueueMetadata?) {
        val memo = ReadMemo.current()
        val previous = memo?.let { ReadMemo.read(LAST, Unit) { null } }
        // Leaves a trace for the next message, which must not find it
        ReadMemo.written(LAST, Unit, payload.id)
        memos[payload.id] = ReadMemoState(open = memo != null, previous = previous)
    }

    companion object {
        private val LAST = ReadMemo.Region<Unit, String?>("test-last-message")
    }
}

/**
 * @property open Whether a memo was open during the processing
 * @property previous ID of the previous message found in the memo, if any
 */
data class ReadMemoState(
    val open: Boolean,
    val previous: String?,
)

data class ReadMemoQueuePayload(
    val id: String,
)
