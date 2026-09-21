package net.nemerosa.ontrack.extension.general.validation

import graphql.schema.*
import net.nemerosa.ontrack.graphql.schema.*
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.structure.RunInfoService
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationDataType
import net.nemerosa.ontrack.model.structure.ValidationRunStatusService
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class MetricsValidationDataTypeRunGraphQLMutation(
    structureService: StructureService,
    validationRunStatusService: ValidationRunStatusService,
    gqlInputMetricsEntry: GQLInputMetricsEntry,
    runInfoService: RunInfoService,
) : AbstractTypedValidationRunMutationProvider<MetricsValidationData>(
    structureService,
    validationRunStatusService,
    runInfoService,
) {

    override val mutationFragmentName: String = "Metrics"

    override val dataType: KClass<out ValidationDataType<*, MetricsValidationData>> =
        MetricsValidationDataType::class

    override val dataInputFields: List<GraphQLInputObjectField> = listOf(
        GraphQLInputObjectField.newInputObjectField()
            .name("metrics")
            .description("List of metrics")
            .type(
                GraphQLNonNull(
                    GraphQLList(
                        GraphQLNonNull(
                            gqlInputMetricsEntry.typeRef
                        )
                    )
                )
            )
            .build()
    )

    override fun readInput(input: EnvMutationInput): MetricsValidationData {
        return input.getRequiredInput<Any>("metrics")
            .asJson()
            .values().map { entry ->
                entry.parse<MetricsEntryInput>()
            }
            .let { entries ->
                MetricsValidationData(
                    metrics = entries.associate { it.name to it.value }
                )
            }
    }
}

@Component
class GQLInputMetricsEntry : GQLInputType<MetricsEntryInput> {

    override fun createInputType(dictionary: MutableSet<GraphQLType>): GraphQLInputType = GraphQLInputObjectType.newInputObject()
        .name(MetricsEntryInput::class.java.simpleName)
        .description("One metric: a name and a value")
        .field(
            requiredStringInputField(MetricsEntryInput::name.name, "Metric name")
        )
        .field(
            requiredFloatInputField(MetricsEntryInput::value.name, "Metric value"),
        )
        .build()

    override fun convert(argument: Any?): MetricsEntryInput? = argument?.asJson()?.parse()

    override fun getTypeRef() = GraphQLTypeReference(
        MetricsEntryInput::class.java.simpleName
    )

}

data class MetricsEntryInput(
    val name: String,
    val value: Double,
)