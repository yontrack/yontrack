package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder

interface AutoVersioningProcessingService {

    /**
     * Processes an auto-versioning order.
     *
     * @param order Order to process
     * @param automaticRetries `true` when the caller handles the automatic retries of the transient failures:
     * their error notification is then deferred to the caller by throwing an
     * [AutoVersioningDeferredErrorException][net.nemerosa.ontrack.extension.av.retry.AutoVersioningDeferredErrorException].
     * @return Outcome of the processing
     */
    fun process(order: AutoVersioningOrder, automaticRetries: Boolean = false): AutoVersioningProcessingOutcome

}