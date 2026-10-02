package net.nemerosa.ontrack.extension.general.validation

import graphql.schema.*
import net.nemerosa.ontrack.graphql.schema.*
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationDataTypeConfig
import org.springframework.stereotype.Component

/**
 * Provides the `setupCHMLValidationStamp` mutation to setup a validation stamp
 */
@Component
class CHMLValidationDataTypeGraphQLMutation(
    structureService: StructureService,
    gqlInputCHMLLevel: GQLInputCHMLLevel,
    private val chmlValidationDataType: CHMLValidationDataType
) : AbstractTypedValidationStampMutationProvider<CHMLValidationDataTypeConfig>(structureService) {

    override val mutationFragmentName: String = "CHML"

    override val dataTypeInputFields: List<GraphQLInputObjectField> = listOf(
        requiredRefInputField(
            CHMLValidationDataTypeConfig::warningLevel.name,
            "Level needed to raise a warning",
            gqlInputCHMLLevel.typeRef
        ),
        requiredRefInputField(
            CHMLValidationDataTypeConfig::failedLevel.name,
            "Level needed to raise a failure",
            gqlInputCHMLLevel.typeRef
        ),
        optionalBooleanInputField(
            CHMLValidationDataTypeConfig::warningPassesAutoPromotion.name,
            "If true, a run in WARNING status counts as passed for the auto promotion. Defaults to false."
        ),
    )

    override fun readInput(input: MutationInput): ValidationDataTypeConfig<CHMLValidationDataTypeConfig> {
        val warningLevel: CHMLLevel = input.getRequiredInput<Any>(CHMLValidationDataTypeConfig::warningLevel.name)
            .asJson().parse()
        val failedLevel: CHMLLevel = input.getRequiredInput<Any>(CHMLValidationDataTypeConfig::failedLevel.name)
            .asJson().parse()
        return ValidationDataTypeConfig(
            descriptor = chmlValidationDataType.descriptor,
            config = CHMLValidationDataTypeConfig(
                warningLevel = warningLevel,
                failedLevel = failedLevel,
                warningPassesAutoPromotion = input.getInput<Boolean>(
                    CHMLValidationDataTypeConfig::warningPassesAutoPromotion.name
                ) ?: false,
            )
        )
    }
}

@Component
class GQLInputCHMLLevel(
    private val gqlEnumCHML: GQLEnumCHML
) : GQLInputType<CHMLLevel> {

    override fun createInputType(dictionary: MutableSet<GraphQLType>): GraphQLInputType = GraphQLInputObjectType.newInputObject()
        .name(INPUT_NAME)
        .description("CHML level, associated a given CHML type and a value")
        .field(
            requiredRefInputField(
                CHMLLevel::level.name,
                "Type of level to count",
                gqlEnumCHML.getTypeRef()
            )
        )
        .field(
            requiredIntInputField(
                CHMLLevel::value.name,
                "Count to reach to raise this level"
            )
        )
        .build()

    override fun convert(argument: Any?): CHMLLevel =
        argument.asJson().parse()

    override fun getTypeRef() = GraphQLTypeReference(INPUT_NAME)

    companion object {
        private val INPUT_NAME = CHMLLevel::class.java.simpleName
    }

}

@Component
class GQLEnumCHML : AbstractGQLEnum<CHML>(
    type = CHML::class,
    values = CHML.values(),
    description = "CHML values"
)
