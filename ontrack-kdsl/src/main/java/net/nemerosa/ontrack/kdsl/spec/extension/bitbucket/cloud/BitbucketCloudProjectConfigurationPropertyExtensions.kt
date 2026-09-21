package net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud

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
 * Bitbucket Cloud property on a project. Setting `null` deletes it.
 */
var Project.bitbucketCloudConfigurationProperty: BitbucketCloudProjectConfigurationProperty?
    get() = getProperty(BITBUCKET_CLOUD_PROJECT_CONFIGURATION_PROPERTY)?.parse()
    set(value) {
        if (value != null) {
            setProperty(BITBUCKET_CLOUD_PROJECT_CONFIGURATION_PROPERTY, value)
        } else {
            deleteProperty(BITBUCKET_CLOUD_PROJECT_CONFIGURATION_PROPERTY)
        }
    }

/**
 * @property configuration Name of the Bitbucket Cloud configuration
 * @property workspace Slug of the workspace
 * @property repository Slug of the repository in the workspace
 */
@JsonDeserialize(using = BitbucketCloudProjectConfigurationPropertyDeserializer::class)
class BitbucketCloudProjectConfigurationProperty(
    val configuration: String,
    val workspace: String,
    val repository: String,
    val indexationInterval: Int = 0,
    val issueServiceConfigurationIdentifier: String? = null,
)

const val BITBUCKET_CLOUD_PROJECT_CONFIGURATION_PROPERTY =
    "net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationPropertyType"

/**
 * The property is read back with its configuration as an object, and set with its configuration name.
 */
class BitbucketCloudProjectConfigurationPropertyDeserializer : ValueDeserializer<BitbucketCloudProjectConfigurationProperty>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BitbucketCloudProjectConfigurationProperty {
        val node: JsonNode = p.readValueAsTree()
        val configuration = node.path("configuration")
        return BitbucketCloudProjectConfigurationProperty(
            configuration = if (configuration.isObject) configuration.path("name").asText() else configuration.asText(),
            workspace = node.path("workspace").asText(),
            repository = node.path("repository").asText(),
            indexationInterval = node.path("indexationInterval").asInt(),
            issueServiceConfigurationIdentifier = node.getTextField("issueServiceConfigurationIdentifier"),
        )
    }
}
