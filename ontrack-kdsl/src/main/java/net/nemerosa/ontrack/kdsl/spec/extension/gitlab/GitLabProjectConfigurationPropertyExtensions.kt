package net.nemerosa.ontrack.kdsl.spec.extension.gitlab

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.spec.Project
import net.nemerosa.ontrack.kdsl.spec.deleteProperty
import net.nemerosa.ontrack.kdsl.spec.getProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty

/**
 * GitLab property on a project. Setting `null` deletes it.
 */
var Project.gitLabConfigurationProperty: GitLabProjectConfigurationProperty?
    get() = getProperty(GITLAB_PROJECT_CONFIGURATION_PROPERTY)?.parse()
    set(value) {
        if (value != null) {
            setProperty(GITLAB_PROJECT_CONFIGURATION_PROPERTY, value)
        } else {
            deleteProperty(GITLAB_PROJECT_CONFIGURATION_PROPERTY)
        }
    }

/**
 * @property configuration Name of the GitLab configuration
 * @property repository Full path of the project, subgroups included, like `group/subgroup/project`
 * @property indexationInterval How often to index the repository, in minutes, 0 to disable indexation
 * @property issueServiceConfigurationIdentifier Identifier of the associated issue service
 */
@JsonDeserialize(using = GitLabProjectConfigurationPropertyDeserializer::class)
class GitLabProjectConfigurationProperty(
    val configuration: String,
    val repository: String,
    val indexationInterval: Int = 0,
    val issueServiceConfigurationIdentifier: String? = null,
)

const val GITLAB_PROJECT_CONFIGURATION_PROPERTY =
    "net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationPropertyType"

/**
 * The property is read back with its configuration as an object, and set with its configuration name.
 */
class GitLabProjectConfigurationPropertyDeserializer : JsonDeserializer<GitLabProjectConfigurationProperty>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): GitLabProjectConfigurationProperty {
        val node: JsonNode = p.readValueAsTree()
        val configuration = node.path("configuration")
        return GitLabProjectConfigurationProperty(
            configuration = if (configuration.isObject) configuration.path("name").asText() else configuration.asText(),
            repository = node.path("repository").asText(),
            indexationInterval = node.path("indexationInterval").asInt(),
            issueServiceConfigurationIdentifier = node.getTextField("issueServiceConfigurationIdentifier"),
        )
    }
}
