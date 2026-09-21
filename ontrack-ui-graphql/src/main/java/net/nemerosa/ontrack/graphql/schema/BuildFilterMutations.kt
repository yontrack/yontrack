package net.nemerosa.ontrack.graphql.schema

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.buildfilter.BuildFilterService
import net.nemerosa.ontrack.model.structure.ID
import org.springframework.stereotype.Component
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

@Component
class BuildFilterMutations(
    private val buildFilterService: BuildFilterService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        unitMutation<SaveBuildFilterInput>(
            name = "saveBuildFilter",
            description = "Saves a build filter in the user preferences",
        ) { input ->
            buildFilterService.saveFilter(
                ID(input.branchId),
                false,
                input.name,
                input.type,
                input.data,
            )
        },
        unitMutation<ShareBuildFilterInput>(
            name = "shareBuildFilter",
            description = "Shares a build filter for all users",
        ) { input ->
            buildFilterService.saveFilter(
                ID(input.branchId),
                true,
                input.name,
                input.type,
                input.data,
            )
        },
        unitMutation<DeleteBuildFilterInput>(
            name = "deleteBuildFilter",
            description = "Deletes a build filter for current user and for all users if shared",
        ) { input ->
            buildFilterService.deleteFilter(
                ID(input.branchId),
                input.name,
            )
        }
    )
}

data class DeleteBuildFilterInput(

    /**
     * ID of the branch for which to delete the filter.
     */
    val branchId: Int,
    @NotEmpty(message = "The build filter name is required.")
    @Size(min = 1, message = "The build filter name is required.")
    val name: String,
)

data class SaveBuildFilterInput(

    /**
     * ID of the branch for which to save the filter.
     */
    val branchId: Int,

    @NotEmpty(message = "The build filter name is required.")
    @Size(min = 1, message = "The build filter name is required.")
    val name: String,

    @NotEmpty(message = "The build filter type is required.")
    val type: String,

    val data: JsonNode,
)

data class ShareBuildFilterInput(

    /**
     * ID of the branch for which to save the filter.
     */
    val branchId: Int,

    @NotEmpty(message = "The build filter name is required.")
    @Size(min = 1, message = "The build filter name is required.")
    val name: String,

    @NotEmpty(message = "The build filter type is required.")
    val type: String,

    val data: JsonNode,
)