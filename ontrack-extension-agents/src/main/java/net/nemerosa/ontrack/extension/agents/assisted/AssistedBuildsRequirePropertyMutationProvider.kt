package net.nemerosa.ontrack.extension.agents.assisted

import graphql.schema.GraphQLInputObjectField
import net.nemerosa.ontrack.graphql.schema.MutationInput
import net.nemerosa.ontrack.graphql.schema.PropertyMutationProvider
import net.nemerosa.ontrack.graphql.schema.getStringList
import net.nemerosa.ontrack.graphql.schema.requiredStringListInputField
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyType
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * `setPromotionLevelAssistedBuildsRequireProperty(ById)` and
 * `deletePromotionLevelAssistedBuildsRequireProperty(ById)`.
 */
@Component
class AssistedBuildsRequirePropertyMutationProvider : PropertyMutationProvider<AssistedBuildsRequireProperty> {

    override val propertyType: KClass<out PropertyType<AssistedBuildsRequireProperty>> =
        AssistedBuildsRequirePropertyType::class

    override val mutationNameFragment: String = "AssistedBuildsRequire"

    override val inputFields: List<GraphQLInputObjectField> = listOf(
        requiredStringListInputField(
            AssistedBuildsRequireProperty::validationStamps.name,
            "Names of the validation stamps of the branch which an assisted build must pass before being promoted to this level",
        ),
    )

    override fun readInput(entity: ProjectEntity, input: MutationInput) = AssistedBuildsRequireProperty.normalised(
        AssistedBuildsRequireProperty(
            validationStamps = input.getStringList(AssistedBuildsRequireProperty::validationStamps) ?: emptyList(),
        )
    )
}
