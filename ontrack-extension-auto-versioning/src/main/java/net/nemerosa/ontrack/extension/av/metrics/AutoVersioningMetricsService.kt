package net.nemerosa.ontrack.extension.av.metrics

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningProcessingOutcome

interface AutoVersioningMetricsService {

    fun onScheduled(order: AutoVersioningOrder)
    fun onScheduledCancelled(order: AutoVersioningOrder)

    fun onQueuing(order: AutoVersioningOrder, routingKey: String)
    fun onReceiving(order: AutoVersioningOrder, queue: String?)

    fun processingTiming(
        order: AutoVersioningOrder,
        queue: String?,
        code: () -> AutoVersioningProcessingOutcome,
    ): AutoVersioningProcessingOutcome

    fun onProcessingCompleted(order: AutoVersioningOrder, outcome: AutoVersioningProcessingOutcome)
    fun onProcessingError()

    fun onPostProcessingStarted(order: AutoVersioningOrder, postProcessing: PostProcessing<*>)
    fun postProcessingTiming(order: AutoVersioningOrder, postProcessing: PostProcessing<*>, code: () -> Unit)
    fun onPostProcessingSuccess(order: AutoVersioningOrder, postProcessing: PostProcessing<*>)
    fun onPostProcessingError(order: AutoVersioningOrder, postProcessing: PostProcessing<*>)

    /**
     * The [order] failed on a transient error and has been rescheduled automatically.
     */
    fun onRetryScheduled(order: AutoVersioningOrder)

    /**
     * The [order] failed after at least one automatic retry, and is not retried any longer.
     */
    fun onRetryExhausted(order: AutoVersioningOrder)

}