package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.queue.QueueProcessor
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatchResult
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcherImpl
import net.nemerosa.ontrack.extension.queue.source.QueueSource
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Spy on the messages dispatched to the queues, in the integration tests of this module: they run the
 * queue of the assisted changes synchronously, and record neither its messages nor its metrics.
 *
 * Every message is dispatched as usual.
 */
@Component
@Primary
class QueueDispatcherSpy(
    private val queueDispatcherImpl: QueueDispatcherImpl,
) : QueueDispatcher {

    private val dispatched = CopyOnWriteArrayList<Pair<String, Any>>()

    override fun <T : Any> dispatch(
        queueProcessor: QueueProcessor<T>,
        payload: T,
        source: QueueSource?,
        routingFeedback: (routingKey: String) -> Unit,
    ): QueueDispatchResult {
        dispatched += queueProcessor.id to payload
        return queueDispatcherImpl.dispatch(queueProcessor, payload, source, routingFeedback)
    }

    /**
     * Payloads dispatched so far to the queue of a processor
     */
    fun dispatched(processorId: String): List<Any> =
        dispatched.filter { it.first == processorId }.map { it.second }
}
