package net.nemerosa.ontrack.extension.github.ingestion.processing.push.tagging

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.ingestion.processing.push.PushPayload
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build

/**
 * Strategy to retrieve a build to tag.
 *
 * @param C Type of the configuration
 */
interface TaggingStrategy<C> {

    /**
     * Given a configuration, looks for a build.
     *
     * @param config Configuration for this strategy (maybe null, if not valid, may return null)
     * @param branch Branch into whick look for the build
     * @param payload Received push payload
     * @return Build to tag (or null if not found)
     */
    fun findBuild(config: C?, branch: Branch, payload: PushPayload): Build?

    /**
     * Parsing and validation of a strategy configuration
     *
     * @param config JSON configuration (or null if not provided)
     * @return Valid configuration or null if this if also valid for this strategy
     */
    fun parseAndValidate(config: JsonNode?): C?

    /**
     * Unique type identified
     */
    val type: String

}
