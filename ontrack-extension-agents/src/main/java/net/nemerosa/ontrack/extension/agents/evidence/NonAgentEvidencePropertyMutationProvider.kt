package net.nemerosa.ontrack.extension.agents.evidence

import graphql.schema.GraphQLInputObjectField
import net.nemerosa.ontrack.graphql.schema.MutationInput
import net.nemerosa.ontrack.graphql.schema.PropertyMutationProvider
import net.nemerosa.ontrack.graphql.schema.optionalBooleanInputField
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyType
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * `setValidationStampNonAgentEvidenceProperty(ById)` and
 * `deleteValidationStampNonAgentEvidenceProperty(ById)`.
 */
@Component
class NonAgentEvidencePropertyMutationProvider : PropertyMutationProvider<NonAgentEvidenceProperty> {

    override val propertyType: KClass<out PropertyType<NonAgentEvidenceProperty>> =
        NonAgentEvidencePropertyType::class

    override val mutationNameFragment: String = "NonAgentEvidence"

    override val inputFields: List<GraphQLInputObjectField> = listOf(
        optionalBooleanInputField(
            NonAgentEvidenceProperty::enabled.name,
            "The restriction applies when the property is set and this flag is true (the default)"
        ),
    )

    override fun readInput(entity: ProjectEntity, input: MutationInput) = NonAgentEvidenceProperty(
        enabled = input.getInput<Boolean>(NonAgentEvidenceProperty::enabled.name) ?: true,
    )
}
