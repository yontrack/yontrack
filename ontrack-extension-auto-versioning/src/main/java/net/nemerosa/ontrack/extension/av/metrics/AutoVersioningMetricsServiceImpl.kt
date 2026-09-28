package net.nemerosa.ontrack.extension.av.metrics

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningProcessingOutcome
import net.nemerosa.ontrack.model.metrics.increment
import net.nemerosa.ontrack.model.metrics.time
import org.springframework.stereotype.Component

@Component
class AutoVersioningMetricsServiceImpl(
    private val meterRegistry: MeterRegistry,
) : AutoVersioningMetricsService {

    override fun onScheduled(order: AutoVersioningOrder) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.Scheduling.scheduledCount,
        )
    }

    override fun onScheduledCancelled(order: AutoVersioningOrder) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.Scheduling.schedulingCancelledCount,
        )
    }

    override fun onQueuing(order: AutoVersioningOrder, routingKey: String) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.Queue.producedCount,
            AutoVersioningMetrics.Tags.ROUTING_KEY to routingKey,
        )
    }

    override fun onReceiving(order: AutoVersioningOrder, queue: String?) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.Queue.consumedCount,
            AutoVersioningMetrics.Tags.QUEUE to (queue ?: "-"),
        )
    }

    override fun processingTiming(
        order: AutoVersioningOrder,
        queue: String?,
        code: () -> AutoVersioningProcessingOutcome,
    ): AutoVersioningProcessingOutcome = meterRegistry.time(
        AutoVersioningMetrics.Processing.time,
        *getTags(order, AutoVersioningMetrics.Tags.QUEUE to (queue ?: "-")).toTypedArray()
    ) {
        code()
    } ?: error("Could not get the outcome of the processing.")

    override fun onProcessingCompleted(order: AutoVersioningOrder, outcome: AutoVersioningProcessingOutcome) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.Processing.completedCount,
            AutoVersioningMetrics.Tags.OUTCOME to outcome.name,
        )
    }

    override fun onProcessingError() {
        meterRegistry.increment(
            AutoVersioningMetrics.Processing.errorCount
        )
    }

    override fun onPostProcessingStarted(
        order: AutoVersioningOrder,
        postProcessing: PostProcessing<*>,
    ) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.PostProcessing.startedCount,
            AutoVersioningMetrics.Tags.POST_PROCESSING to postProcessing.id
        )
    }

    override fun postProcessingTiming(order: AutoVersioningOrder, postProcessing: PostProcessing<*>, code: () -> Unit) {
        meterRegistry.time(
            AutoVersioningMetrics.PostProcessing.time,
            *getTags(order, AutoVersioningMetrics.Tags.POST_PROCESSING to postProcessing.id).toTypedArray(),
        ) {
            code()
        }
    }

    override fun onPostProcessingSuccess(order: AutoVersioningOrder, postProcessing: PostProcessing<*>) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.PostProcessing.successCount,
            AutoVersioningMetrics.Tags.POST_PROCESSING to postProcessing.id
        )
    }

    override fun onPostProcessingError(order: AutoVersioningOrder, postProcessing: PostProcessing<*>) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.PostProcessing.errorCount,
            AutoVersioningMetrics.Tags.POST_PROCESSING to postProcessing.id
        )
    }

    override fun onRetryScheduled(order: AutoVersioningOrder) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.Retry.scheduledCount,
        )
    }

    override fun onRetryExhausted(order: AutoVersioningOrder) {
        meterRegistry.increment(
            order,
            AutoVersioningMetrics.Retry.exhaustedCount,
        )
    }

    private fun MeterRegistry.increment(
        order: AutoVersioningOrder,
        metric: String,
        vararg extraTags: Pair<String, String>,
    ) {
        val tags = getTags(order, *extraTags)
        increment(
            metric,
            *tags.toTypedArray()
        )
    }

    private fun getTags(
        order: AutoVersioningOrder,
        vararg extraTags: Pair<String, String>,
    ) = listOf(
        "sourceProject" to order.sourceProject,
        "targetProject" to order.branch.project.name,
        "targetBranch" to order.branch.name,
    ) + extraTags.toList()
}