package net.nemerosa.ontrack.graphql.schema

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.settings.PredefinedValidationStampService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

@Component
class PredefinedValidationStampMutations(
    private val predefinedValidationStampService: PredefinedValidationStampService,
    private val validationDataTypeService: ValidationDataTypeService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "createPredefinedValidationStamp",
            description = "Creates a new predefined validation stamp",
            input = CreatePredefinedValidationStampInput::class,
            outputName = "predefinedValidationStamp",
            outputDescription = "Created predefined validation stamp",
            outputType = PredefinedValidationStamp::class
        ) { input ->
            val config: ValidationDataTypeConfig<*>? =
                validationDataTypeService.validateValidationDataTypeConfig<Any>(input.dataType, input.dataTypeConfig)
            predefinedValidationStampService.newPredefinedValidationStamp(
                PredefinedValidationStamp.of(
                    NameDescription.nd(
                        name = input.name,
                        description = input.description,
                    )
                ).withDataType(config)
            )
        },
        simpleMutation(
            name = "updatePredefinedValidationStamp",
            description = "Updates an existing predefined validation stamp",
            input = UpdatePredefinedValidationStampInput::class,
            outputName = "predefinedValidationStamp",
            outputDescription = "Updated predefined validation stamp",
            outputType = PredefinedValidationStamp::class
        ) { input ->
            val id = ID.of(input.id)
            val existing = predefinedValidationStampService.getPredefinedValidationStamp(id)
            val config: ValidationDataTypeConfig<*>? =
                validationDataTypeService.validateValidationDataTypeConfig<Any>(input.dataType, input.dataTypeConfig)
            val ppl = PredefinedValidationStamp(
                id = id,
                name = input.name,
                description = input.description,
                isImage = existing.isImage,
                dataType = config,
            )
            predefinedValidationStampService.savePredefinedValidationStamp(ppl)
            ppl
        },
        unitMutation(
            name = "deletePredefinedValidationStamp",
            description = "Deletes an existing predefined validation stamp",
            input = DeletePredefinedValidationStampInput::class,
        ) { input ->
            val id = ID.of(input.id)
            predefinedValidationStampService.deletePredefinedValidationStamp(id)
        },
    )
}

data class CreatePredefinedValidationStampInput(
    @APIDescription("Unique name for the predefined validation stamp")
    val name: String,
    @APIDescription("Description for the predefined validation stamp")
    val description: String,
    @APIDescription("FQCN of the data type")
    val dataType: String?,
    @APIDescription("Configuration of the data type")
    val dataTypeConfig: JsonNode?,
)

data class UpdatePredefinedValidationStampInput(
    @APIDescription("ID of the predefined validation stamp")
    val id: Int,
    @APIDescription("Unique name for the predefined validation stamp")
    val name: String,
    @APIDescription("Description for the predefined validation stamp")
    val description: String,
    @APIDescription("FQCN of the data type")
    val dataType: String?,
    @APIDescription("Configuration of the data type")
    val dataTypeConfig: JsonNode?,
)

data class DeletePredefinedValidationStampInput(
    @APIDescription("ID of the predefined validation stamp")
    val id: Int,
)
