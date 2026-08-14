package net.nemerosa.ontrack.model.tx

import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.NestedExceptionUtils
import org.springframework.dao.RecoverableDataAccessException
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Component
import org.springframework.transaction.CannotCreateTransactionException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import kotlin.math.min
import kotlin.random.Random

/**
 * Retries a self-contained unit of work when it fails because of a *transient* database issue,
 * like a connection loss or an exhausted connection pool.
 *
 * Only transient failures are retried - see [isTransient]. Any other failure is a business
 * failure and is rethrown immediately.
 */
@Component
class TransactionRetry(
    private val ontrackConfigProperties: OntrackConfigProperties,
) {

    private val logger: Logger = LoggerFactory.getLogger(TransactionRetry::class.java)

    /**
     * Runs the [code], retrying it on transient database failures.
     *
     * @param code Code to run. It must be safe to run several times.
     * @return Result of the code, as soon as one attempt succeeds
     * @throws Exception the last failure, when the retries are exhausted or the failure is not transient
     */
    fun <T> withRetry(code: () -> T): T {
        val config = ontrackConfigProperties.tx
        val maxAttempts = (config.retries + 1).coerceAtLeast(1)
        val maxDelayMs = config.retryMaxDelay.toMillis().coerceAtLeast(0)
        var delayMs = config.retryDelay.toMillis().coerceIn(0, maxDelayMs)
        var attempt = 1
        while (true) {
            try {
                return code()
            } catch (any: Exception) {
                if (attempt >= maxAttempts || !isTransient(any)) {
                    throw any
                }
                logger.warn(
                    "Transient database failure (attempt {}/{}), retrying in {} ms: {}",
                    attempt,
                    maxAttempts,
                    delayMs,
                    NestedExceptionUtils.getMostSpecificCause(any).toString(),
                )
                sleepBeforeRetry(delayMs, any)
                attempt++
                delayMs = min(delayMs * 2, maxDelayMs)
            }
        }
    }

    /**
     * Waits before the next attempt, adding some jitter so that all the threads which are
     * waiting on the same unavailable database do not retry at the very same moment.
     */
    private fun sleepBeforeRetry(delayMs: Long, failure: Exception) {
        if (delayMs <= 0) return
        val jitterMs = Random.nextLong(0, (delayMs / 4) + 1)
        try {
            Thread.sleep(delayMs + jitterMs)
        } catch (ignored: InterruptedException) {
            // Not retrying an interrupted thread: restoring the flag and giving the original failure back
            Thread.currentThread().interrupt()
            throw failure
        }
    }

    /**
     * Checks the whole cause chain for a failure which is worth retrying.
     */
    private fun isTransient(throwable: Throwable): Boolean {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = throwable
        while (current != null && seen.add(current)) {
            if (current is CannotCreateTransactionException ||
                current is TransientDataAccessException ||
                current is RecoverableDataAccessException ||
                current is SQLTransientException ||
                current is SQLRecoverableException
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
