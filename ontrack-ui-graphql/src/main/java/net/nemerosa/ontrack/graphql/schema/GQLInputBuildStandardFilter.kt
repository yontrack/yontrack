package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars
import graphql.language.IntValue
import graphql.schema.*
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.buildfilter.BuildFilterProviderData
import net.nemerosa.ontrack.model.buildfilter.BuildFilterService
import org.springframework.stereotype.Component

@Component
class GQLInputBuildStandardFilter(
    private val buildFilterService: BuildFilterService
) : GQLInputType<BuildFilterProviderData<*>> {

    override fun getTypeRef(): GraphQLTypeReference {
        return GraphQLTypeReference(STANDARD_BUILD_FILTER)
    }

    override fun createInputType(dictionary: MutableSet<GraphQLType>): GraphQLInputType {
        return GraphQLInputObjectType.newInputObject()
            .name(STANDARD_BUILD_FILTER)
            .field(
                GraphQLInputObjectField.newInputObjectField()
                    .name("count")
                    .description("Maximum number of builds to display")
                    .type(Scalars.GraphQLInt)
                    .defaultValueLiteral(IntValue.of(10))
                    .build()
            )
            .field(formField("sincePromotionLevel", "Builds since the last one which was promoted to this level"))
            .field(formField("withPromotionLevel", "Builds with this promotion level"))
            .field(formField("afterDate", "Build created after or on this date"))
            .field(formField("beforeDate", "Build created before or on this date"))
            .field(formField("sinceValidationStamp", "Builds since the last one which had this validation stamp"))
            .field(formField("sinceValidationStampStatus", "... with status"))
            .field(formField("withValidationStamp", "Builds with this validation stamp"))
            .field(formField("withValidationStampStatus", "... with status"))
            .field(formField("withProperty", "With property"))
            .field(formField("withPropertyValue", "...with value"))
            .field(formField("sinceProperty", "Since property"))
            .field(formField("sincePropertyValue", "...with value"))
            .field(
                formField(
                    "linkedFrom",
                    "The build must be linked FROM the builds selected by the pattern.\n" +
                            "Syntax: PRJ:BLD where PRJ is a project name and BLD a build expression - " +
                            "with * as placeholder"
                )
            )
            .field(
                formField(
                    "linkedFromPromotion",
                    "The build must be linked FROM a build having this promotion (requires \"linkedFrom\")"
                )
            )
            .field(
                formField(
                    "linkedTo",
                    "The build must be linked TO the builds selected by the pattern.\n" +
                            "Syntax: PRJ:BLD where PRJ is a project name and BLD a build expression - " +
                            "with * as placeholder"
                )
            )
            .field(
                formField(
                    "linkedToPromotion",
                    "The build must be linked TO a build having this promotion (requires \"linkedTo\")"
                )
            )
            .field(
                formField(
                    "withDisplayName",
                    "The build display name (its release label when set, its name otherwise) " +
                            "must match this regular expression.\n" +
                            "The matching is case insensitive and partial - use ^ and $ to anchor the pattern."
                )
            )
            .build()
    }

    override fun convert(filter: Any?): BuildFilterProviderData<*> {
        if (filter == null) {
            return buildFilterService.standardFilterProviderData(10).build()
        } else {
            check(filter is MutableMap<*, *>) { "Filter is expected to be a map" }
            val node = filter.asJson()
            return buildFilterService.standardFilterProviderData(node)
        }
    }

    private fun formField(fieldName: String?, description: String?): GraphQLInputObjectField? {
        return GraphQLInputObjectField.newInputObjectField()
            .name(fieldName)
            .description(description)
            .type(Scalars.GraphQLString)
            .build()
    }

    companion object {
        const val STANDARD_BUILD_FILTER: String = "StandardBuildFilter"
    }
}
