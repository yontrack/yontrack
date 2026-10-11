package net.nemerosa.ontrack.extension.queue.redelivery

import net.nemerosa.ontrack.extension.queue.QueueAckMode
import net.nemerosa.ontrack.extension.queue.QueueMetadata
import net.nemerosa.ontrack.extension.queue.QueueProcessor
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * Queue processor which counts the attempts at processing each of its messages, and whose
 * first attempt at a message can be held, to simulate a consumer stopped mid-processing, or
 * made to last, to simulate a long processing.
 */
@Component
class RedeliveryQueueProcessor : QueueProcessor<RedeliveryQueuePayload> {

    private val attempts = ConcurrentHashMap<String, AtomicInteger>()
    private val completions = ConcurrentHashMap<String, AtomicInteger>()
    private val ended = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Holds waiting for the first attempt at a message, then held by that attempt.
     */
    private val holds = ConcurrentHashMap<String, CompletableFuture<Boolean>>()
    private val heldAttempts = ConcurrentHashMap<String, CompletableFuture<Boolean>>()

    /**
     * Durations of the first attempts at some messages
     */
    private val durations = ConcurrentHashMap<String, Duration>()

    /**
     * Number of times the processing of the message identified by [id] was started.
     */
    fun attempts(id: String): Int = attempts[id]?.get() ?: 0

    /**
     * Number of times the processing of the message identified by [id] ran to completion.
     */
    fun completions(id: String): Int = completions[id]?.get() ?: 0

    /**
     * Number of times the processing of the message identified by [id] ended, completed or not.
     */
    fun ended(id: String): Int = ended[id]?.get() ?: 0

    /**
     * The first attempt at the message identified by [id] blocks until [abortHeldAttempt] is called.
     */
    fun holdFirstAttempt(id: String) {
        holds[id] = CompletableFuture()
    }

    /**
     * The first attempt at the message identified by [id] lasts for [duration], unless interrupted.
     */
    fun lastFirstAttempt(id: String, duration: Duration) {
        durations[id] = duration
    }

    /**
     * The held attempt at the message identified by [id] dies, like its pod would have, if it is
     * still running: stopping its listener may already have interrupted it.
     */
    fun abortHeldAttempt(id: String) {
        heldAttempts.remove(id)?.complete(false)
    }

    override val id: String = "test-redelivery"

    override val ackMode: QueueAckMode = QueueAckMode.END

    override val payloadType: KClass<RedeliveryQueuePayload> = RedeliveryQueuePayload::class

    override fun isCancelled(payload: RedeliveryQueuePayload): String? = null

    override fun getRoutingIdentifier(payload: RedeliveryQueuePayload): String = payload.id

    override fun process(payload: RedeliveryQueuePayload, queueMetadata: QueueMetadata?) {
        val attempt = attempts.computeIfAbsent(payload.id) { AtomicInteger() }.incrementAndGet()
        try {
            // Only the attempt which takes the hold blocks
            val hold = holds.remove(payload.id)
            if (hold != null) {
                heldAttempts[payload.id] = hold
                val completed = hold.get(60, TimeUnit.SECONDS)
                if (!completed) {
                    throw IllegalStateException("Attempt $attempt at ${payload.id} was interrupted")
                }
            }
            // Only the attempt which takes the duration lasts
            val duration = durations.remove(payload.id)
            if (duration != null) {
                Thread.sleep(duration.toMillis())
            }
            completions.computeIfAbsent(payload.id) { AtomicInteger() }.incrementAndGet()
        } finally {
            ended.computeIfAbsent(payload.id) { AtomicInteger() }.incrementAndGet()
        }
    }
}

/**
 * @property id ID of the message
 */
data class RedeliveryQueuePayload(
    val id: String,
)
