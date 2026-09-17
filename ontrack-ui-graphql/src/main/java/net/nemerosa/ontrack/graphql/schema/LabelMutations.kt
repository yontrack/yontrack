package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.ListRef
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.labels.Label
import net.nemerosa.ontrack.model.labels.LabelForm
import net.nemerosa.ontrack.model.labels.LabelManagementService
import net.nemerosa.ontrack.model.labels.ProjectLabelForm
import net.nemerosa.ontrack.model.labels.ProjectLabelManagementService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * Mutations on labels and on the labels of a project.
 *
 * Security checks are performed by the services.
 */
@Component
class LabelMutations(
    private val labelManagementService: LabelManagementService,
    private val projectLabelManagementService: ProjectLabelManagementService,
    private val structureService: StructureService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "createLabel",
            description = "Creates a new label",
            input = CreateLabelInput::class,
            outputName = "label",
            outputDescription = "Created label",
            outputType = Label::class,
        ) { input ->
            labelManagementService.newLabel(
                LabelForm(
                    category = input.category,
                    name = input.name,
                    description = input.description,
                    color = input.color,
                )
            )
        },
        simpleMutation(
            name = "updateLabel",
            description = "Updates an existing label",
            input = UpdateLabelInput::class,
            outputName = "label",
            outputDescription = "Updated label",
            outputType = Label::class,
        ) { input ->
            labelManagementService.updateLabel(
                input.id,
                LabelForm(
                    category = input.category,
                    name = input.name,
                    description = input.description,
                    color = input.color,
                )
            )
        },
        unitMutation(
            name = "deleteLabel",
            description = "Deletes an existing label",
            input = DeleteLabelInput::class,
        ) { input ->
            labelManagementService.deleteLabel(input.id)
        },
        simpleMutation(
            name = "setProjectLabels",
            description = "Sets the labels of a project, replacing all the existing ones",
            input = SetProjectLabelsInput::class,
            outputName = "project",
            outputDescription = "Updated project",
            outputType = Project::class,
        ) { input ->
            val project = structureService.getProject(ID.of(input.projectId))
            projectLabelManagementService.associateProjectToLabels(
                project,
                ProjectLabelForm(input.labelIds)
            )
            project
        },
    )
}

data class CreateLabelInput(
    @APIDescription("Category of the label, optional")
    val category: String?,
    @APIDescription("Name of the label")
    val name: String,
    @APIDescription("Description of the label")
    val description: String?,
    @APIDescription("Color of the label, in the #RRGGBB format")
    val color: String,
)

data class UpdateLabelInput(
    @APIDescription("ID of the label to update")
    val id: Int,
    @APIDescription("Category of the label, optional")
    val category: String?,
    @APIDescription("Name of the label")
    val name: String,
    @APIDescription("Description of the label")
    val description: String?,
    @APIDescription("Color of the label, in the #RRGGBB format")
    val color: String,
)

data class DeleteLabelInput(
    @APIDescription("ID of the label to delete")
    val id: Int,
)

data class SetProjectLabelsInput(
    @APIDescription("ID of the project")
    val projectId: Int,
    @APIDescription("IDs of the labels to set on the project. Any other label is removed from the project.")
    @ListRef
    val labelIds: List<Int>,
)
