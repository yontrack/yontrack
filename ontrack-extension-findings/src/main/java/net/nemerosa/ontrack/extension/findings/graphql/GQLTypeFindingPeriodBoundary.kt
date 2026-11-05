package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundary
import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundaryStatus
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeBuild
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import net.nemerosa.ontrack.graphql.support.GQLScalarLong
import org.springframework.stereotype.Component

/**
 * A period of an exposure read against a build filter.
 */
@Component
class GQLTypeFindingPeriodBoundary : GQLType {

    override fun getTypeName(): String = FindingPeriodBoundary::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "The first build at or after the end of a period of an exposure, and before the next period starts, " +
                        "which matches a build filter chosen by the reader. It is a way of reading a period and is never stored."
            )
            .field {
                it.name(FindingPeriodBoundary::status.name)
                    .description("Outcome for this period")
                    .type(GraphQLNonNull(GraphQLTypeReference(FindingPeriodBoundaryStatus::class.java.simpleName)))
            }
            .field {
                it.name(FindingPeriodBoundary::build.name)
                    .description("Matching build, for MATCHED only")
                    .type(GraphQLTypeReference(GQLTypeBuild.BUILD))
            }
            .field {
                it.name(FindingPeriodBoundary::time.name)
                    .description("When the build started to match, and never before the end of the period, for MATCHED only")
                    .type(GQLScalarLocalDateTime.INSTANCE)
            }
            .field {
                it.name(FindingPeriodBoundary::durationSeconds.name)
                    .description("Duration in seconds from the start of the period to the boundary, or to now for NONE_YET. Null otherwise.")
                    .type(GQLScalarLong.INSTANCE)
            }
            .field {
                it.name(FindingPeriodBoundary::reason.name)
                    .description("Why the filter cannot apply, for NOT_APPLICABLE only")
                    .type(GraphQLString)
            }
            .build()
}
