package net.nemerosa.ontrack.extension.artifactory.client

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.artifactory.model.ArtifactoryStatus
import org.springframework.web.client.RestTemplate

interface ArtifactoryClient {

    val buildNames: List<String>

    fun getBuildNumbers(buildName: String): List<String>

    fun getBuildInfo(buildName: String, buildNumber: String): JsonNode

    /**
     * Gets the statuses (i.e. promotions) of a given build info in Artifactory.
     *
     * @param buildInfo Build info as returned by Artifactory
     * @return List of statuses
     */
    fun getStatuses(buildInfo: JsonNode): List<ArtifactoryStatus>

    /**
     * Access to the underlying REST template
     */
    val restTemplate: RestTemplate

    /**
     * AQL query
     */
    fun aql(query: String): JsonNode
}
