package net.nemerosa.ontrack.extension.general

import graphql.schema.GraphQLInputObjectField
import net.nemerosa.ontrack.graphql.schema.MutationInput
import net.nemerosa.ontrack.graphql.schema.PropertyMutationProvider
import net.nemerosa.ontrack.graphql.schema.booleanInputField
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyType
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class NotGrantedMedalsPropertyMutationProvider : PropertyMutationProvider<NotGrantedMedalsProperty> {

    override val propertyType: KClass<out PropertyType<NotGrantedMedalsProperty>> = NotGrantedMedalsPropertyType::class

    override val mutationNameFragment: String = "NotGrantedMedals"

    override val inputFields: List<GraphQLInputObjectField> = listOf(
        booleanInputField(NotGrantedMedalsProperty::enabled),
    )

    override fun readInput(entity: ProjectEntity, input: MutationInput) = NotGrantedMedalsProperty(
        enabled = input.getRequiredInput(NotGrantedMedalsProperty::enabled.name),
    )
}
