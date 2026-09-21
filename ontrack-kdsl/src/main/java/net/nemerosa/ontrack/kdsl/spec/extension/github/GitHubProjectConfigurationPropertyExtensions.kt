package net.nemerosa.ontrack.kdsl.spec.extension.github

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.annotation.JsonDeserialize
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.deleteProperty
import net.nemerosa.ontrack.kdsl.spec.getProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty

/**
 * Sets a GitHub property on a project.
 */
var Project.gitHubConfigurationProperty: GitHubProjectConfigurationProperty?
    get() = getProperty(GITHUB_PROJECT_CONFIGURATION_PROPERTY)?.parse()
    set(value) {
        if (value != null) {
            setProperty(GITHUB_PROJECT_CONFIGURATION_PROPERTY, value)
        } else {
            deleteProperty(GITHUB_PROJECT_CONFIGURATION_PROPERTY)
        }
    }


@JsonDeserialize(using = GitHubProjectConfigurationPropertyDeserializer::class)
class GitHubProjectConfigurationProperty(
        val configuration: String,
        val repository: String,
        val indexationInterval: Int = 0,
        val issueServiceConfigurationIdentifier: String? = null,
)

const val GITHUB_PROJECT_CONFIGURATION_PROPERTY =
        "net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationPropertyType"

class GitHubProjectConfigurationPropertyDeserializer : ValueDeserializer<GitHubProjectConfigurationProperty>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): GitHubProjectConfigurationProperty {
        val node: JsonNode = p.readValueAsTree()
        return GitHubProjectConfigurationProperty(
                configuration = node.path("configuration").path("name").asText(),
                repository = node.path("repository").asText(),
                indexationInterval = node.path("indexationInterval").asInt(),
                issueServiceConfigurationIdentifier = node.getTextField("issueServiceConfigurationIdentifier"),
        )
    }

}