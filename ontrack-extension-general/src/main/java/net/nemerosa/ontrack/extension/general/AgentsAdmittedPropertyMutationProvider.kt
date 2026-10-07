package net.nemerosa.ontrack.extension.general

import graphql.schema.GraphQLInputObjectField
import net.nemerosa.ontrack.graphql.schema.MutationInput
import net.nemerosa.ontrack.graphql.schema.PropertyMutationProvider
import net.nemerosa.ontrack.graphql.schema.optionalBooleanInputField
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyType
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * `setPromotionLevelAgentsAdmittedProperty(ById)` and `deletePromotionLevelAgentsAdmittedProperty(ById)`.
 */
@Component
class AgentsAdmittedPropertyMutationProvider : PropertyMutationProvider<AgentsAdmittedProperty> {

    override val propertyType: KClass<out PropertyType<AgentsAdmittedProperty>> = AgentsAdmittedPropertyType::class

    override val mutationNameFragment: String = "AgentsAdmitted"

    override val inputFields: List<GraphQLInputObjectField> = listOf(
        optionalBooleanInputField(
            AgentsAdmittedProperty::admitted.name,
            "Agents are admitted when the property is set and this flag is true (the default)"
        ),
    )

    override fun readInput(entity: ProjectEntity, input: MutationInput) = AgentsAdmittedProperty(
        admitted = input.getInput<Boolean>(AgentsAdmittedProperty::admitted.name) ?: true,
    )
}
