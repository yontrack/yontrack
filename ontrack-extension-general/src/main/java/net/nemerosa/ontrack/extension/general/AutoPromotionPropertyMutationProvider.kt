package net.nemerosa.ontrack.extension.general

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import net.nemerosa.ontrack.graphql.schema.MutationInput
import net.nemerosa.ontrack.graphql.schema.PropertyMutationProvider
import net.nemerosa.ontrack.graphql.schema.optionalBooleanInputField
import net.nemerosa.ontrack.graphql.schema.optionalStringInputField
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PropertyType
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass

@Component
class AutoPromotionPropertyMutationProvider(
    private val structureService: StructureService,
) : PropertyMutationProvider<AutoPromotionProperty> {

    override val propertyType: KClass<out PropertyType<AutoPromotionProperty>> = AutoPromotionPropertyType::class

    override val mutationNameFragment: String = "AutoPromotion"

    override val inputFields: List<GraphQLInputObjectField> = listOf(
        GraphQLInputObjectField.newInputObjectField()
            .name(AutoPromotionProperty::validationStamps.name)
            .description("List of needed validation stamps")
            .type(GraphQLList(GraphQLNonNull(GraphQLString)))
            .build(),
        optionalStringInputField(AutoPromotionProperty::include.name,
            "Regular expression to include validation stamps by name"),
        optionalStringInputField(AutoPromotionProperty::exclude.name,
            "Regular expression to exclude validation stamps by name"),
        GraphQLInputObjectField.newInputObjectField()
            .name(AutoPromotionProperty::promotionLevels.name)
            .description("List of needed promotion levels")
            .type(GraphQLList(GraphQLNonNull(GraphQLString)))
            .build(),
        optionalBooleanInputField(
            AutoPromotionProperty::autoRevoke.name,
            "When enabled, the promotion is revoked as soon as one of its prerequisites - a required " +
                    "validation stamp or a required promotion - is no longer valid. Revoking a promotion " +
                    "deletes it, but does not undo its effects: any notification or workflow already " +
                    "triggered by the promotion remains fired. Defaults to false when absent."
        ),
    )

    override fun readInput(entity: ProjectEntity, input: MutationInput): AutoPromotionProperty {
        if (entity is PromotionLevel) {
            val validationStamps = input.getInput<List<String>>(AutoPromotionProperty::validationStamps.name)
                ?.mapNotNull {
                    structureService.findValidationStampByName(entity.project.name, entity.branch.name, it).getOrNull()
                }
                ?: emptyList()
            val promotionLevels = input.getInput<List<String>>(AutoPromotionProperty::promotionLevels.name)
                ?.mapNotNull {
                    structureService.findPromotionLevelByName(entity.project.name, entity.branch.name, it).getOrNull()
                }
                ?: emptyList()
            return AutoPromotionProperty(
                validationStamps = validationStamps,
                include = input.getInput<String>(AutoPromotionProperty::include.name) ?: "",
                exclude = input.getInput<String>(AutoPromotionProperty::exclude.name) ?: "",
                promotionLevels = promotionLevels,
                // Absent means false, matching the full-replace semantics of this mutation - an omitted
                // `include` already becomes "". Preserving one field on absence would make the contract
                // inconsistent with every other property mutation.
                autoRevoke = input.getInput<Boolean>(AutoPromotionProperty::autoRevoke.name) ?: false,
            )
        } else {
            throw IllegalStateException("Parent entity must be a promotion level")
        }
    }
}
