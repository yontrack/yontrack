package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.model.docs.DocumentationIgnore
import net.nemerosa.ontrack.model.docs.DocumentationType
import net.nemerosa.ontrack.model.json.schema.JsonSchemaString
import net.nemerosa.ontrack.model.support.ConfigurationProperty

/**
 * Link between a project and a Bitbucket Cloud repository.
 *
 * @property configuration Link to the Bitbucket Cloud configuration
 * @property workspace Slug of the Bitbucket Cloud workspace
 * @property repository Repository slug in the workspace
 * @property indexationInterval Indexation interval
 * @property issueServiceConfigurationIdentifier ID to the [net.nemerosa.ontrack.extension.issues.model.IssueServiceConfiguration] associated
 * with this repository.
 */
class BitbucketCloudProjectConfigurationProperty(
    @DocumentationType("String", "Name of the Bitbucket Cloud configuration")
    @JsonSchemaString
    override val configuration: BitbucketCloudConfiguration,
    @APIDescription("Slug of the Bitbucket Cloud workspace")
    val workspace: String,
    @APIDescription("Slug of the repository in the workspace")
    val repository: String,
    @APIDescription("How often to index the repository, in minutes. Use 0 to disable indexation.")
    val indexationInterval: Int,
    @APIDescription("Identifier for the issue service")
    val issueServiceConfigurationIdentifier: String?
) : ConfigurationProperty<BitbucketCloudConfiguration> {

    /**
     * `workspace/repository`
     */
    @DocumentationIgnore
    val fullName: String get() = "$workspace/$repository"

    /**
     * Gets the URL to the repository
     */
    @DocumentationIgnore
    val repositoryUrl: String get() = "https://bitbucket.org/$fullName"

}
