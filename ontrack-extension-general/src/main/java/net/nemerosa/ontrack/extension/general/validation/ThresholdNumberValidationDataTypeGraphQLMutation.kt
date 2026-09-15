package net.nemerosa.ontrack.extension.general.validation

import graphql.schema.GraphQLInputObjectField
import net.nemerosa.ontrack.graphql.schema.AbstractTypedValidationStampMutationProvider
import net.nemerosa.ontrack.graphql.schema.MutationInput
import net.nemerosa.ontrack.graphql.schema.optionalBooleanInputField
import net.nemerosa.ontrack.graphql.schema.optionalIntInputField
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationDataTypeConfig
import net.nemerosa.ontrack.model.structure.config
import org.springframework.stereotype.Component

/**
 * Provides the `setupNumberValidationStamp` mutation to setup a validation stamp
 */
@Component
class ThresholdNumberValidationDataTypeGraphQLMutation(
    structureService: StructureService,
    private val thresholdNumberValidationDataType: ThresholdNumberValidationDataType
) : AbstractTypedValidationStampMutationProvider<ThresholdConfig>(structureService) {

    override val mutationFragmentName: String = "Number"

    override val dataTypeInputFields: List<GraphQLInputObjectField> = listOf(
        optionalIntInputField(
            ThresholdConfig::warningThreshold.name,
            "Threshold value for a warning"
        ),
        optionalIntInputField(
            ThresholdConfig::failureThreshold.name,
            "Threshold value for a failure"
        ),
        optionalBooleanInputField(
            ThresholdConfig::okIfGreater.name,
            "Direction of the value scale"
        )
    )

    override fun readInput(input: MutationInput): ValidationDataTypeConfig<ThresholdConfig> {
        return thresholdNumberValidationDataType.config(
            ThresholdConfig(
                warningThreshold = input.getInput(ThresholdConfig::warningThreshold.name),
                failureThreshold = input.getInput(ThresholdConfig::failureThreshold.name),
                okIfGreater = input.getInput<Boolean>(ThresholdConfig::okIfGreater.name) ?: true
            )
        )
    }
}
