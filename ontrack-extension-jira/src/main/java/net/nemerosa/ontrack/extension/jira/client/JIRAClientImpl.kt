package net.nemerosa.ontrack.extension.jira.client

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.jira.JIRAConfiguration
import net.nemerosa.ontrack.extension.jira.model.*
import net.nemerosa.ontrack.extension.jira.notifications.JiraCustomField
import net.nemerosa.ontrack.json.getRequiredTextField
import org.apache.commons.lang3.StringUtils
import org.springframework.web.client.HttpClientErrorException.Forbidden
import org.springframework.web.client.HttpClientErrorException.NotFound
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class JIRAClientImpl(
    override val restTemplate: RestTemplate,
) : JIRAClient {

    private val issues: ConcurrentMap<Pair<String, String>, JIRAIssue> = ConcurrentHashMap()

    private class IssueNotFoundException : RuntimeException()

    override fun getIssue(key: String, configuration: JIRAConfiguration): JIRAIssue? {
        return try {
            issues.getOrPut(configuration.url to key) {
                fetchIssue(key, configuration) ?: throw IssueNotFoundException()
            }
        } catch (_: IssueNotFoundException) {
            null
        }
    }

    override fun getIssueLastCommit(
        key: String,
        configuration: JIRAConfiguration,
        applicationType: String,
        repositoryName: String,
    ): String? {
        // Gets the issue first
        val issue = getIssue(key, configuration) ?: return null
        return try {
            // Uses JIRA "dev-status" integration to get commits linked to this issue.
            // API: /rest/dev-status/1.0/issue/detail?issueId=<id>&applicationType=github|stash|bitbucket&dataType=repository
            val details = restTemplate.getForObject<JsonNode>(
                "/rest/dev-status/1.0/issue/detail?issueId={issueId}&applicationType={applicationType}&dataType={dataType}",
                mapOf(
                    "issueId" to issue.id,
                    "applicationType" to applicationType,
                    "dataType" to "repository"
                )
            )!!
            val repositories = details.path("details").firstOrNull()?.path("repositories")
            if (repositories != null && repositories.isArray) {
                // Gets the matching repository
                val repository = repositories.find { it.path("name").asText() == repositoryName }
                repository?.path("commits")?.firstOrNull()?.path("id")?.asText()
            } else {
                null
            }
        } catch (_: Forbidden) {
            null
        } catch (_: NotFound) {
            null
        }
    }

    private fun fetchIssue(key: String, configuration: JIRAConfiguration): JIRAIssue? {
        try {
            val node = restTemplate.getForObject<JsonNode>("/rest/api/2/issue/$key?expand=names")!!
            return toIssue(configuration, node)
        } catch (_: Forbidden) {
            // The issue cannot be accessed
            // For the moment, ignoring silently
            return null
        } catch (_: NotFound) {
            return null
        }

    }

    override fun searchIssueStubs(jiraConfiguration: JIRAConfiguration, jql: String): List<JIRAIssueStub> =
        try {
            val node = restTemplate.getForObject<JsonNode>("/rest/api/2/search?jql=$jql")!!
            node.path("issues").map {
                it.getRequiredTextField("key")
            }.map { key ->
                JIRAIssueStub(
                    key = key,
                    url = jiraConfiguration.getIssueURL(key),
                )
            }
        } catch (_: Forbidden) {
            // The issues cannot be accessed
            // For the moment, ignoring silently
            emptyList()
        } catch (_: NotFound) {
            emptyList()
        }

    override fun createIssue(
        configuration: JIRAConfiguration,
        project: String,
        issueType: String,
        labels: List<String>,
        fixVersion: String?,
        assignee: String?,
        title: String,
        customFields: List<JiraCustomField>,
        body: String
    ): JIRAIssueStub {
        val fields = mutableMapOf(
            "project" to mapOf(
                "key" to project,
            ),
            "summary" to title,
            "issuetype" to mapOf(
                "name" to issueType
            ),
            "labels" to labels,
            "description" to body,
        )

        if (!assignee.isNullOrBlank()) {
            fields["assignee"] = mapOf(
                "name" to assignee
            )
        }

        if (!fixVersion.isNullOrBlank()) {
            fields["fixVersions"] = listOf(
                mapOf(
                    "name" to fixVersion
                )
            )
        }

        customFields.forEach { (name, json) ->
            fields[name] = json
        }

        val payload = mapOf(
            "fields" to fields,
        )

        val node = restTemplate.postForObject(
            "/rest/api/2/issue",
            payload,
            JsonNode::class.java
        ) ?: error("Could not create the issue (no answer)")

        val key = node.getRequiredTextField("key")

        return JIRAIssueStub(
            key = key,
            url = configuration.getIssueURL(key),
        )
    }

    override fun createLink(jiraConfiguration: JIRAConfiguration, sourceTicket: String, targetTicket: String, linkName: String) {
        restTemplate.postForObject(
            "/rest/api/2/issueLink",
            mapOf(
                "type" to mapOf(
                    "name" to linkName,
                ),
                "inwardIssue" to mapOf(
                    "key" to sourceTicket,
                ),
                "outwardIssue" to mapOf(
                    "key" to targetTicket,
                ),
            ),
            JsonNode::class.java
        )
    }

    override val projects: List<String>
        get() {
            val node = restTemplate.getForObject<JsonNode>("/rest/api/2/project")!!
            return node.map {
                it.getRequiredTextField("key")
            }
        }

    override fun close() {}

    companion object {

        private val JIRA_DATA_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

        @JvmStatic
        fun toIssue(configuration: JIRAConfiguration, node: JsonNode): JIRAIssue {
            // Translation of fields
            val fields = ArrayList<JIRAField>()
            val names = node.path("names")
            val nameFields = names.fields()
            while (nameFields.hasNext()) {
                val nameField = nameFields.next()
                val name = nameField.key
                val displayName = nameField.value.asText()
                // Gets the field node
                val fieldNode = field(node, name)
                // Creates the field
                fields.add(
                    JIRAField(
                        name,
                        displayName,
                        fieldNode
                    )
                )
            }

            // Versions
            val affectedVersions = toVersions(node, "versions")
            val fixVersions = toVersions(node, "fixVersions")

            // Status
            val status = getStatus(node)

            // Key
            val key = node.path("key").asText()

            // Issue links
            val links = ArrayList<JIRALink>()
            for (issueLinkNode in field(node, "issuelinks")) {
                val inwardKey = issueLinkNode.path("inwardIssue").path("key").asText()
                val outwardKey = issueLinkNode.path("outwardIssue").path("key").asText()
                if (StringUtils.isNotBlank(inwardKey)) {
                    links.add(
                        JIRALink(
                            inwardKey,
                            configuration.getIssueURL(inwardKey),
                            getStatus(issueLinkNode.path("inwardIssue")),
                            issueLinkNode.path("type").path("name").asText(),
                            issueLinkNode.path("type").path("inward").asText()
                        )
                    )
                } else if (StringUtils.isNotBlank(outwardKey)) {
                    links.add(
                        JIRALink(
                            outwardKey,
                            configuration.getIssueURL(outwardKey),
                            getStatus(issueLinkNode.path("outwardIssue")),
                            issueLinkNode.path("type").path("name").asText(),
                            issueLinkNode.path("type").path("outward").asText()
                        )
                    )
                }
            }

            // JIRA issue
            return JIRAIssue(
                id = node.path("id").asText(),
                url = configuration.getIssueURL(key),
                key = key,
                summary = fieldValue(node, "summary"),
                status = status,
                assignee = field(node, "assignee").path("name").asText(),
                updateTime = parseFromJIRA(fieldValue(node, "updated")),
                fields = fields,
                affectedVersions = affectedVersions,
                fixVersions = fixVersions,
                issueType = field(node, "issuetype").path("name").asText(),
                links = links
            )
        }

        private fun getStatus(node: JsonNode): JIRAStatus {
            val statusNode = field(node, "status")
            return JIRAStatus(
                statusNode.path("name").asText(),
                statusNode.path("iconUrl").asText()
            )
        }

        @JvmStatic
        fun parseFromJIRA(value: String): LocalDateTime {
            return LocalDateTime.ofInstant(
                ZonedDateTime.parse(value, JIRA_DATA_TIME).toInstant(),
                ZoneOffset.UTC
            )
        }

        private fun toVersions(node: JsonNode, versionFieldName: String): List<JIRAVersion> {
            val versionField = field(node, versionFieldName)
            val versions = ArrayList<JIRAVersion>()
            for (versionNode in versionField) {
                versions.add(
                    JIRAVersion(
                        versionNode.path("name").asText(),
                        versionNode.path("released").asBoolean()
                    )
                )
            }
            return versions
        }

        private fun fieldValue(node: JsonNode, name: String): String {
            return field(node, name).asText()
        }

        private fun field(node: JsonNode, name: String): JsonNode {
            return node.path("fields").path(name)
        }
    }
}
