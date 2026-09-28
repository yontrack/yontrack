package net.nemerosa.ontrack.extension.av.retry

import net.nemerosa.ontrack.common.BaseException

/**
 * Transient failure of an order whose error notification has been deferred to the caller of the
 * processing, because the order may be retried automatically. The caller decides whether the order
 * is retried and, if not, sends the error notification with the [notificationMessage].
 *
 * @param notificationMessage Message for the error notification
 * @param cause Actual failure
 */
class AutoVersioningDeferredErrorException(
    val notificationMessage: String,
    override val cause: Exception,
) : BaseException(cause, cause.message ?: cause::class.java.name)
