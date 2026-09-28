package net.nemerosa.ontrack.extension.av.retry

/**
 * Marks a failure of an auto-versioning order as transient: the same order may succeed if it is
 * run again later, typically because an external system was momentarily unavailable.
 *
 * When the automatic retries are enabled in the auto-versioning settings, an order failing with
 * such an exception anywhere in its cause chain is rescheduled automatically, up to the configured
 * number of times. Any other failure is final.
 */
interface AutoVersioningRetryableException

/**
 * Checks if this failure has an [AutoVersioningRetryableException] in its cause chain.
 */
fun Throwable.isAutoVersioningRetryable(): Boolean {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is AutoVersioningRetryableException) return true
        current = current.cause
    }
    return false
}

/**
 * Message of the error notification for an order, mentioning the automatic retries which preceded it.
 *
 * @param message Message of the failure
 * @param attempt Attempt number of the failed order, `0` if it's not an automatic retry
 */
fun autoVersioningErrorMessage(message: String, attempt: Int): String = when (attempt) {
    0 -> message
    1 -> "$message (failed after 1 automatic retry)"
    else -> "$message (failed after $attempt automatic retries)"
}
