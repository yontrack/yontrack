package net.nemerosa.ontrack.graphql.schema

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.ListRef
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.exceptions.BranchNotFoundException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class PromotionLevelMutations(
    private val structureService: StructureService,
    private val securityService: SecurityService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation>
        get() = listOf(
            /**
             * Setting up a promotion level
             */
            simpleMutation(
                name = "setupPromotionLevel",
                description = "Creates or updates a promotion level for a branch",
                input = SetupPromotionLevelInput::class,
                outputName = "promotionLevel",
                outputDescription = "Created or updated promotion level",
                outputType = PromotionLevel::class,
                fetcher = this::setupPromotionLevel
            ),
            /**
             * Creating a promotion level from a branch ID
             */
            simpleMutation(
                name = "createPromotionLevelById",
                description = "Creates a new promotion level from a branch ID",
                input = CreatePromotionLevelByIdInput::class,
                outputName = "promotionLevel",
                outputDescription = "Created promotion level",
                outputType = PromotionLevel::class
            ) { input ->
                val branch = structureService.getBranch(ID.of(input.branchId))
                val newPL = structureService.newPromotionLevel(
                    PromotionLevel.of(
                        branch,
                        NameDescription.nd(input.name, input.description)
                    )
                )
                if (!input.fields.isNullOrEmpty()) {
                    val fields = input.fields.mapIndexed { index, fieldInput ->
                        PromotionLevelField(
                            id = 0,
                            name = fieldInput.name,
                            displayName = fieldInput.displayName,
                            description = fieldInput.description,
                            type = fieldInput.type,
                            required = fieldInput.required,
                            options = fieldInput.options ?: emptyList(),
                            position = index,
                        )
                    }
                    structureService.setPromotionLevelFields(newPL.id, fields)
                }
                newPL
            },
            /**
             * Updating an existing promotion level
             */
            simpleMutation(
                name = "updatePromotionLevelById",
                description = "Updates an existing promotion level",
                input = UpdatePromotionLevelByIdInput::class,
                outputName = "promotionLevel",
                outputDescription = "Updated promotion level",
                outputType = PromotionLevel::class
            ) { input ->
                val pl = structureService.getPromotionLevel(ID.of(input.id))
                structureService.savePromotionLevel(
                    PromotionLevel(
                        id = pl.id,
                        name = input.name,
                        description = input.description,
                        branch = pl.branch,
                        isImage = pl.isImage,
                        signature = securityService.currentSignature,
                    )
                )
                structureService.getPromotionLevel(ID.of(input.id))
            },
            /**
             * Deleting an existing promotion level
             */
            unitMutation<DeletePromotionLevelByIdInput>(
                "deletePromotionLevelById",
                "Deletes an existing promotion level"
            ) { input ->
                structureService.deletePromotionLevel(ID.of(input.id))
            },
            /**
             * Bulk update of promotion level
             */
            unitMutation<BulkUpdatePromotionLevelByIdInput>(
                "bulkUpdatePromotionLevelById",
                "Bulk update of a promotion level"
            ) { input ->
                structureService.bulkUpdatePromotionLevels(ID.of(input.id))
            },
            /**
             * Reordering promotion levels
             */
            unitMutation<ReorderPromotionLevelByIdInput>(
                name = "reorderPromotionLevelById",
                description = "Reordering the promotion levels in a branch"
            ) { input ->
                val branchId = ID.of(input.branchId)
                // Loads the current promotion levels
                val list = structureService.getPromotionLevelListForBranch(branchId).toMutableList()
                // Gets the indexes
                val oldIndex = list.indexOfFirst { it.name == input.oldName }
                val newIndex = list.indexOfFirst { it.name == input.newName }
                // If indexes are equal of not valid, skip
                if (oldIndex >= 0 && newIndex >= 0 && oldIndex != newIndex) {
                    val item = list.removeAt(oldIndex)
                    list.add(newIndex, item)
                    // Collecting the IDs
                    val ids = list.map { it.id() }
                    structureService.reorderPromotionLevels(
                        branchId,
                        Reordering(ids)
                    )
                }
            },
            /**
             * Setting configurable fields on a promotion level
             */
            unitMutation<SetPromotionLevelFieldsInput>(
                name = "setPromotionLevelFields",
                description = "Replaces all configurable field definitions on a promotion level"
            ) { input ->
                val fields = input.fields.mapIndexed { index, fieldInput ->
                    PromotionLevelField(
                        id = 0,
                        name = fieldInput.name,
                        displayName = fieldInput.displayName,
                        description = fieldInput.description,
                        type = fieldInput.type,
                        required = fieldInput.required,
                        options = fieldInput.options ?: emptyList(),
                        position = index,
                    )
                }
                structureService.setPromotionLevelFields(ID.of(input.promotionLevelId), fields)
            }
        )

    private fun setupPromotionLevel(input: SetupPromotionLevelInput): PromotionLevel {
        val existing =
            structureService.findPromotionLevelByName(input.project, input.branch, input.promotion).getOrNull()
        return if (existing != null) {
            // Updates the promotion if need be
            updatePromotionLevel(existing, input)
        } else {
            createPromotionLevel(input)
        }
    }

    private fun updatePromotionLevel(existing: PromotionLevel, input: SetupPromotionLevelInput): PromotionLevel {
        val updated = existing.update(
            NameDescription.nd(input.promotion, input.description)
        )
        // Saves in repository
        structureService.savePromotionLevel(updated)
        // As resource
        return updated
    }

    private fun createPromotionLevel(input: SetupPromotionLevelInput): PromotionLevel {
        val branch =
            structureService.findBranchByName(input.project, input.branch).getOrNull()
                ?: throw BranchNotFoundException(input.project, input.branch)
        val promotionLevel = PromotionLevel.of(
            branch,
            NameDescription.nd(input.promotion, input.description)
        )
        // Saves it into the repository
        return structureService.newPromotionLevel(promotionLevel)
    }
}

/**
 * Input for the `setupPromotionLevel` mutation.
 */
data class SetupPromotionLevelInput(
    val project: String,
    val branch: String,
    val promotion: String,
    val description: String?,
)

data class CreatePromotionLevelByIdInput(
    @APIDescription("Branch ID")
    val branchId: Int,
    @get:NotNull(message = "The name is required.")
    @get:Pattern(regexp = NameDescription.NAME, message = "The name ${NameDescription.NAME_MESSAGE_SUFFIX}")
    @APIDescription("Promotion level name")
    val name: String,
    @APIDescription("Promotion level description")
    val description: String,
    @ListRef(embedded = true)
    @APIDescription("Optional field definitions to set at creation time")
    val fields: List<PromotionLevelFieldInput>? = null,
)

data class UpdatePromotionLevelByIdInput(
    @APIDescription("Promotion level ID")
    val id: Int,
    @get:NotNull(message = "The name is required.")
    @get:Pattern(regexp = NameDescription.NAME, message = "The name ${NameDescription.NAME_MESSAGE_SUFFIX}")
    @APIDescription("Promotion level name")
    val name: String,
    @APIDescription("Promotion level description")
    val description: String,
)

data class DeletePromotionLevelByIdInput(
    @APIDescription("Promotion level ID")
    val id: Int,
)

data class BulkUpdatePromotionLevelByIdInput(
    @APIDescription("Promotion level ID")
    val id: Int,
)

data class ReorderPromotionLevelByIdInput(
    @APIDescription("Branch ID")
    val branchId: Int,
    @APIDescription("Old name to swap")
    val oldName: String,
    @APIDescription("New name to swap")
    val newName: String,
)

data class SetPromotionLevelFieldsInput(
    @APIDescription("Promotion level ID")
    val promotionLevelId: Int,
    @ListRef(embedded = true)
    @APIDescription("Field definitions (replaces all existing fields)")
    val fields: List<PromotionLevelFieldInput>,
)

data class PromotionLevelFieldInput(
    @APIDescription("Technical name used as key")
    val name: String,
    @APIDescription("Human-readable label")
    val displayName: String,
    @APIDescription("Optional description")
    val description: String?,
    @APIDescription("Field type")
    val type: PromotionLevelFieldType,
    @APIDescription("Whether the field is required when creating a promotion run")
    val required: Boolean,
    @ListRef
    @APIDescription("Allowed values for CHOICE fields")
    val options: List<String>?,
)
