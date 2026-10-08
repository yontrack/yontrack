package net.nemerosa.ontrack.extension.scm.changelog.assistants

import graphql.schema.GraphQLInputObjectField
import net.nemerosa.ontrack.graphql.schema.MutationInput
import net.nemerosa.ontrack.graphql.schema.PropertyMutationProvider
import net.nemerosa.ontrack.graphql.schema.enumInputField
import net.nemerosa.ontrack.graphql.schema.getInt
import net.nemerosa.ontrack.graphql.schema.getString
import net.nemerosa.ontrack.graphql.schema.getStringList
import net.nemerosa.ontrack.graphql.schema.intInputField
import net.nemerosa.ontrack.graphql.schema.stringInputField
import net.nemerosa.ontrack.graphql.schema.stringListInputField
import net.nemerosa.ontrack.model.exceptions.PropertyValidationException
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PropertyType
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * `setBuildAssistedChangeProperty(ById)` and `deleteBuildAssistedChangeProperty(ById)`, used by the CI
 * to set the assisted change of a build when Yontrack cannot compute it. The basis is `SET_BY_CI` unless
 * given otherwise.
 */
@Component
class AssistedChangePropertyMutationProvider : PropertyMutationProvider<AssistedChangeProperty> {

    override val propertyType: KClass<out PropertyType<AssistedChangeProperty>> = AssistedChangePropertyType::class

    override val mutationNameFragment: String = "AssistedChange"

    override val inputFields: List<GraphQLInputObjectField> = listOf(
        enumInputField(
            AssistedChangeProperty::basis,
            description = "How the value was obtained, SET_BY_CI by default",
            nullable = true,
        ),
        stringInputField(AssistedChangeProperty::unknownReason, nullable = true),
        stringListInputField(AssistedChangeProperty::assistants, nullable = true),
        intInputField(AssistedChangeProperty::assistedCommits, nullable = true),
        intInputField(AssistedChangeProperty::totalCommits, nullable = true),
        stringListInputField(AssistedChangeProperty::sessionLinks, nullable = true),
        intInputField(AssistedChangeProperty::previousBuildId, nullable = true),
    )

    override fun readInput(entity: ProjectEntity, input: MutationInput): AssistedChangeProperty =
        AssistedChangeProperty.validated(
            AssistedChangeProperty(
                basis = input.getInput<Any>(AssistedChangeProperty::basis.name)?.toString()?.let { basis ->
                    AssistedChangeBasis.entries.find { it.name == basis }
                        ?: throw PropertyValidationException("Unknown basis: $basis")
                } ?: AssistedChangeBasis.SET_BY_CI,
                unknownReason = input.getString(AssistedChangeProperty::unknownReason),
                assistants = input.getStringList(AssistedChangeProperty::assistants) ?: emptyList(),
                assistedCommits = input.getInt(AssistedChangeProperty::assistedCommits) ?: 0,
                totalCommits = input.getInt(AssistedChangeProperty::totalCommits) ?: 0,
                sessionLinks = input.getStringList(AssistedChangeProperty::sessionLinks) ?: emptyList(),
                previousBuildId = input.getInt(AssistedChangeProperty::previousBuildId),
            )
        )
}
