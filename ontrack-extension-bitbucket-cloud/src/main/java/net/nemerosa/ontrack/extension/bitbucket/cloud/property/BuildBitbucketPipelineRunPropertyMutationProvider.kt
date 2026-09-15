package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import graphql.schema.GraphQLInputObjectField
import net.nemerosa.ontrack.graphql.schema.*
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyType
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class BuildBitbucketPipelineRunPropertyMutationProvider :
    PropertyMutationProvider<BuildBitbucketPipelineRunProperty> {

    override val propertyType: KClass<out PropertyType<BuildBitbucketPipelineRunProperty>> =
        BuildBitbucketPipelineRunPropertyType::class

    override val mutationNameFragment: String = "BitbucketPipelineRun"

    override val inputFields: List<GraphQLInputObjectField> = listOf(
        requiredStringInputField("workspace", "Slug of the Bitbucket Cloud workspace"),
        requiredStringInputField("repository", "Slug of the repository in the workspace"),
        requiredIntInputField("buildNumber", "Number of the pipeline run"),
        optionalStringInputField("uuid", "UUID of the pipeline run"),
    )

    override fun readInput(entity: ProjectEntity, input: MutationInput): BuildBitbucketPipelineRunProperty {
        val workspace: String = input.getRequiredInput("workspace")
        val repository: String = input.getRequiredInput("repository")
        val buildNumber: Int = input.getRequiredInput("buildNumber")
        return BuildBitbucketPipelineRunProperty(
            workspace = workspace,
            repository = repository,
            buildNumber = buildNumber,
            uuid = input.getInput("uuid"),
            url = BuildBitbucketPipelineRunProperty.runUrl(workspace, repository, buildNumber),
        )
    }
}
