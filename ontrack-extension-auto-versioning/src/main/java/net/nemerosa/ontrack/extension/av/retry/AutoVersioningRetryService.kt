package net.nemerosa.ontrack.extension.av.retry

import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntry
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder

/**
 * Automatic retries of the auto-versioning orders failing on a transient error.
 *
 * The retry of an order is decided once, by [onError], when its processing has failed. The
 * processing defers the error notification of a transient failure (see [AutoVersioningDeferredErrorException])
 * so that [onError] sends it only if the order is not retried.
 */
interface AutoVersioningRetryService {

    /**
     * Gets the attempt number of this [order] if it's an automatic retry, `0` otherwise.
     */
    fun getRetryAttempt(order: AutoVersioningOrder): Int

    /**
     * Records the failure of an [order] and schedules its automatic retry when:
     *
     * * the [error] has an [AutoVersioningRetryableException] in its cause chain
     * * automatic retries are enabled in the settings
     * * the order has not reached the maximum number of automatic retries
     * * no other order for the same target is running, which would supersede the retry
     *
     * If the order is not retried and its error notification was [deferred][AutoVersioningDeferredErrorException],
     * the notification is sent.
     */
    fun onError(order: AutoVersioningOrder, error: Throwable)

    /**
     * Gets the retries & reschedules this [entry] is part of.
     */
    fun getLineage(entry: AutoVersioningAuditEntry): AutoVersioningAuditEntryLineage

}
