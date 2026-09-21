package net.nemerosa.ontrack.extension.tfc.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import net.nemerosa.ontrack.extension.support.client.restTemplateBuilder
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject

class TFCClientImpl(
    private val url: String,
    private val token: String,
) : TFCClient {

    private val logger: Logger = LoggerFactory.getLogger(TFCClientImpl::class.java)

    private val client: RestTemplate by lazy {
        restTemplateBuilder()
            .requestFactory(HttpComponentsClientHttpRequestFactory::class.java)
            .rootUri("$url/api/v2")
            .defaultHeader("Authorization", "Bearer $token")
            .defaultHeader("Content-Type", "application/vnd.api+json")
            .build()
    }

    override val organizations: List<TFCOrganization>
        get() = client.getForObject<TOrganizations>("/organizations")!!
            .data.map { data ->
                TFCOrganization(
                    id = data.attributes.externalId,
                    name = data.attributes.name,
                )
            }

    override fun getWorkspaceVariables(workspaceId: String): List<TFCVariable> =
        client.getForObject<TWorkspaceVariables>("/workspaces/$workspaceId/vars")!!
            .data.map { data ->
                TFCVariable(
                    id = data.id,
                    key = data.attributes.key,
                    value = data.attributes.value,
                    sensitive = data.attributes.sensitive,
                    description = data.attributes.description,
                )
            }.filter { !it.sensitive }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TOrganizations(
        val data: List<TOrganizationData>,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TOrganizationData(
        val attributes: TOrganizationAttributes,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TOrganizationAttributes(
        @JsonProperty("external-id")
        val externalId: String,
        val name: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TWorkspaceVariables(
        val data: List<TWorkspaceVariableData>,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TWorkspaceVariableData(
        val id: String,
        val attributes: TWorkspaceVariableAttributes,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class TWorkspaceVariableAttributes(
        val key: String,
        val value: String?,
        val sensitive: Boolean,
        val description: String?,
    )
}