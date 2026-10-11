package net.nemerosa.ontrack.extension.av.queue

import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parse
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.util.concurrent.ConcurrentHashMap

/**
 * Post-processing which takes some time and records when it ran for each order, so that a test
 * can tell whether two orders were processed concurrently.
 */
@Component
class TrackingPostProcessing(
    extensionFeature: AutoVersioningExtensionFeature,
) : AbstractExtension(extensionFeature), PostProcessing<TrackingPostProcessingConfig> {

    private val intervals = ConcurrentHashMap<String, Interval>()

    /**
     * When the post-processing of the [order] ran, if it ran.
     */
    fun interval(order: AutoVersioningOrder): Interval? = intervals[order.uuid]

    override val id: String = ID

    override val name: String = "Tracking post processing"

    override fun parseAndValidate(config: JsonNode?): TrackingPostProcessingConfig =
        config?.takeIf { !it.isNull }?.parse() ?: TrackingPostProcessingConfig()

    override fun postProcessing(
        config: TrackingPostProcessingConfig,
        autoVersioningOrder: AutoVersioningOrder,
        repositoryURI: String,
        repository: String,
        upgradeBranch: String,
        scm: SCM,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
        onPostProcessingInfo: (info: PostProcessingInfo) -> Unit,
    ) {
        val start = System.nanoTime()
        Thread.sleep(config.durationMs)
        intervals[autoVersioningOrder.uuid] = Interval(start = start, end = System.nanoTime())
    }

    /**
     * @property start Start time, in nanoseconds of the JVM clock
     * @property end End time, in nanoseconds of the JVM clock
     */
    data class Interval(
        val start: Long,
        val end: Long,
    ) {
        fun overlaps(other: Interval): Boolean = start < other.end && other.start < end
    }

    companion object {
        const val ID = "test-tracking"
    }
}

/**
 * @property durationMs How long the post-processing takes
 */
data class TrackingPostProcessingConfig(
    val durationMs: Long = 1_000,
)
