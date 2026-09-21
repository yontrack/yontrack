package net.nemerosa.ontrack.extension.artifactory.client

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.artifactory.model.ArtifactoryStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.springframework.web.client.postForObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ArtifactoryClientImpl(
    override val restTemplate: RestTemplate,
) : ArtifactoryClient {

    override fun aql(query: String): JsonNode {
        return restTemplate.postForObject<JsonNode>(
            "/api/search/aql",
            query,
        )!!
    }

    override val buildNames: List<String>
        get() {
            val node = restTemplate.getForObject<JsonNode>("/api/build")!!
            val names = mutableListOf<String>()
            node.path("build")
                .forEach { numberNode ->
                    val name = numberNode.path("uri").asText().trimStart('/')
                    if (name.isNotBlank()) {
                        names.add(name)
                    }
                }
            return names
        }

    override fun getBuildNumbers(buildName: String): List<String> {
        try {
            val node = restTemplate.getForObject<JsonNode>("/api/build/${buildName}")!!
            val numbers = mutableListOf<String>()
            node.path("buildsNumbers").forEach { numberNode ->
                val number = numberNode.path("uri").asText().trimStart('/')
                if (number.isNotBlank()) {
                    numbers.add(number)
                }
            }
            return numbers
        } catch (_: HttpClientErrorException.NotFound) {
            // When the build is not defined, returns no build number
            return emptyList()
        }
    }

    override fun getBuildInfo(buildName: String, buildNumber: String): JsonNode {
        return restTemplate.getForObject<JsonNode>("/api/build/${buildName}/${buildNumber}")!!.path("buildInfo")
    }

    override fun getStatuses(buildInfo: JsonNode): List<ArtifactoryStatus> {
        val statuses = mutableListOf<ArtifactoryStatus>()
        buildInfo.path("statuses").forEach { statusNode ->
            statuses.add(
                ArtifactoryStatus(
                    statusNode.path("status").asText(),
                    statusNode.path("user").asText(),
                    LocalDateTime.parse(
                        statusNode.path("timestamp").asText(),
                        DateTimeFormatter.ofPattern(
                            "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
                        )
                    )
                )
            )
        }
        return statuses
    }
}
