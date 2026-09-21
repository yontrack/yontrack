package net.nemerosa.ontrack.extension.jira.servicedesk

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.jira.model.JIRAIssueStub
import net.nemerosa.ontrack.extension.jira.notifications.JiraCustomField
import net.nemerosa.ontrack.json.getRequiredTextField
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.springframework.web.client.postForObject

class JiraServiceDeskImpl(
    private val restTemplate: RestTemplate,
) : JiraServiceDesk {

    /**
     * See https://docs.atlassian.com/jira-servicedesk/REST/5.15.1/
     */
    override fun searchRequest(
        serviceDeskId: Int,
        requestTypeId: Int,
        searchTerm: String,
        requestStatus: JiraServiceDeskRequestStatus,
    ): List<JIRAIssueStub> =
        try {
            val node =
                restTemplate.getForObject<JsonNode>("/rest/servicedeskapi/request?serviceDeskId=$serviceDeskId&requestTypeId=$requestTypeId&requestStatus=${requestStatus.requestStatus}&searchTerm=$searchTerm")!!
            node.path("values").values().map {
                JIRAIssueStub(
                    key = it.getRequiredTextField("issueKey"),
                    url = it.path("_links").getRequiredTextField("web")
                )
            }
        } catch (ex: HttpClientErrorException.Forbidden) {
            // The issues cannot be accessed
            // For the moment, ignoring silently
            emptyList()
        } catch (ex: HttpClientErrorException.NotFound) {
            emptyList()
        }

    override fun createRequest(serviceDeskId: Int, requestTypeId: Int, fields: List<JiraCustomField>): JIRAIssueStub {
        val payload = mapOf(
            "serviceDeskId" to serviceDeskId,
            "requestTypeId" to requestTypeId,
            "requestFieldValues" to fields.associate { it.name to it.value }
        )

        val node = restTemplate.postForObject<JsonNode>(
            "/rest/servicedeskapi/request",
            payload
        )!!

        return JIRAIssueStub(
            key = node.getRequiredTextField("issueKey"),
            url = node.path("_links").getRequiredTextField("web")
        )
    }
}