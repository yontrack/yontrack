package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import graphql.schema.GraphQLInputObjectField
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.graphql.schema.*
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyType
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class BitbucketCloudProjectConfigurationPropertyMutationProvider(
    private val bitbucketCloudConfigurationService: BitbucketCloudConfigurationService
) :
    PropertyMutationProvider<BitbucketCloudProjectConfigurationProperty> {

    override val propertyType: KClass<out PropertyType<BitbucketCloudProjectConfigurationProperty>> =
        BitbucketCloudProjectConfigurationPropertyType::class

    override val mutationNameFragment: String = "BitbucketCloudConfiguration"

    override val inputFields: List<GraphQLInputObjectField> = listOf(
        requiredStringInputField("configuration", "Name of the Bitbucket Cloud configuration to use"),
        requiredStringInputField("workspace", "Slug of the Bitbucket Cloud workspace"),
        requiredStringInputField("repository", "Slug of the Bitbucket Cloud repository in the workspace"),
        optionalIntInputField(
            "indexationInterval",
            "Interval (in minutes) between each indexation of the repository by Ontrack"
        ),
        optionalStringInputField(
            "issueServiceConfigurationIdentifier",
            "Issue identifier to use, for example jira//name where name is the name of the JIRA configuration in Ontrack."
        )
    )

    override fun readInput(entity: ProjectEntity, input: MutationInput) = BitbucketCloudProjectConfigurationProperty(
        configuration = bitbucketCloudConfigurationService.getConfiguration(input.getRequiredInput("configuration")),
        workspace = input.getRequiredInput("workspace"),
        repository = input.getRequiredInput("repository"),
        indexationInterval = input.getInput<Int>("indexationInterval") ?: 0,
        issueServiceConfigurationIdentifier = input.getInput("issueServiceConfigurationIdentifier")
    )
}
