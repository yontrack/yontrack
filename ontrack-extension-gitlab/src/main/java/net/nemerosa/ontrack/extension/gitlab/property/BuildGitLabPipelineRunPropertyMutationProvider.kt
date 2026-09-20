package net.nemerosa.ontrack.extension.gitlab.property

import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLNonNull
import net.nemerosa.ontrack.graphql.schema.MutationInput
import net.nemerosa.ontrack.graphql.schema.PropertyMutationProvider
import net.nemerosa.ontrack.graphql.schema.requiredIntInputField
import net.nemerosa.ontrack.graphql.schema.requiredStringInputField
import net.nemerosa.ontrack.graphql.support.GQLScalarLong
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyType
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class BuildGitLabPipelineRunPropertyMutationProvider :
    PropertyMutationProvider<BuildGitLabPipelineRunProperty> {

    override val propertyType: KClass<out PropertyType<BuildGitLabPipelineRunProperty>> =
        BuildGitLabPipelineRunPropertyType::class

    override val mutationNameFragment: String = "GitLabPipelineRun"

    /**
     * The pipeline ID is a [Long]: on gitlab.com it is shared by every project of the instance and is already
     * close to the maximum of a 32-bit integer.
     *
     * The URL is an input rather than being computed from the project path: a self-managed instance is on an
     * arbitrary host, so the project path alone does not give it.
     */
    override val inputFields: List<GraphQLInputObjectField> = listOf(
        requiredStringInputField("projectPath", "Path of the GitLab project, subgroups included"),
        GraphQLInputObjectField.newInputObjectField()
            .name("pipelineId")
            .description("ID of the pipeline, unique across the GitLab instance")
            .type(GraphQLNonNull(GQLScalarLong.INSTANCE))
            .build(),
        requiredIntInputField("pipelineIid", "IID of the pipeline, its number inside the project"),
        requiredStringInputField("url", "Link to the pipeline"),
    )

    override fun readInput(entity: ProjectEntity, input: MutationInput): BuildGitLabPipelineRunProperty =
        BuildGitLabPipelineRunProperty(
            projectPath = input.getRequiredInput("projectPath"),
            pipelineId = input.getRequiredInput("pipelineId"),
            pipelineIid = input.getRequiredInput("pipelineIid"),
            url = input.getRequiredInput("url"),
        )
}
