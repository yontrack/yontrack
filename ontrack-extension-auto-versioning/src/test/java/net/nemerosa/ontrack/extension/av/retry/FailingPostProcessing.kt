package net.nemerosa.ontrack.extension.av.retry

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parse
import org.springframework.stereotype.Component

/**
 * Post-processing which always fails, on a transient error or not.
 */
@Component
class FailingPostProcessing(
    extensionFeature: AutoVersioningExtensionFeature,
) : AbstractExtension(extensionFeature), PostProcessing<FailingPostProcessingConfig> {

    override val id: String = ID

    override val name: String = "Failing post processing"

    override fun parseAndValidate(config: JsonNode?): FailingPostProcessingConfig =
        config?.takeIf { !it.isNull }?.parse() ?: FailingPostProcessingConfig()

    override fun postProcessing(
        config: FailingPostProcessingConfig,
        autoVersioningOrder: AutoVersioningOrder,
        repositoryURI: String,
        repository: String,
        upgradeBranch: String,
        scm: SCM,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
        onPostProcessingInfo: (info: PostProcessingInfo) -> Unit,
    ) {
        if (config.transient) {
            throw TransientPostProcessingException()
        } else {
            throw IllegalStateException("Post-processing workflow failed")
        }
    }

    companion object {
        const val ID = "test-failing"
    }
}

data class FailingPostProcessingConfig(
    val transient: Boolean = true,
)

class TransientPostProcessingException : RuntimeException("Post-processing service unavailable"),
    AutoVersioningRetryableException
