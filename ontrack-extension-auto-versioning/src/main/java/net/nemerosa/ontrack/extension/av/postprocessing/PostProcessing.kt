package net.nemerosa.ontrack.extension.av.postprocessing

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.model.extension.Extension

/**
 * Service responsible to post process a version upgrade in a branch.
 *
 * @param T Configuration type
 */
interface PostProcessing<T> : Extension {

    /**
     * ID of the service
     */
    val id: String

    /**
     * Display name of the service
     */
    val name: String

    /**
     * Post processing call.
     *
     * @param config Configuration of the post processing service
     * @param autoVersioningOrder Auto versioning order being processed
     * @param repositoryURI Full URI to the repository
     * @param repository Path to the repository
     * @param upgradeBranch Remote branch already containing the upgraded version
     * @param scm The target SCM
     * @param avTemplateRenderer Template renderer used to render templates in the auto-versioning context
     * @param onPostProcessingInfo Callback called when the post-processing running info becomes available
     */
    fun postProcessing(
        config: T,
        autoVersioningOrder: AutoVersioningOrder,
        repositoryURI: String,
        repository: String,
        upgradeBranch: String,
        scm: SCM,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
        onPostProcessingInfo: (info: PostProcessingInfo) -> Unit,
    )

    /**
     * Given the configuration as JSON, parses it and validates it.
     */
    fun parseAndValidate(config: JsonNode?): T

}
