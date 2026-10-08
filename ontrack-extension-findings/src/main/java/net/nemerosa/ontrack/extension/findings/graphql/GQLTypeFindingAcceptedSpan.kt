package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.history.FindingAcceptedSpan
import net.nemerosa.ontrack.extension.findings.query.FindingAcceptedSpanView
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationRun
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import org.springframework.stereotype.Component

/**
 * Stretch of a period of exposure under an acceptance.
 */
@Component
class GQLTypeFindingAcceptedSpan(
    private val gqlTypeFindingAcceptance: GQLTypeFindingAcceptance,
) : GQLType {

    override fun getTypeName(): String = FindingAcceptedSpan::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Stretch of a period of exposure during which the finding was reported under an acceptance holding on the day of each scan.")
            .field {
                it.name(FindingAcceptedSpan::from.name)
                    .description("Time of the first observation under the acceptance")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
                    .dataFetcher { env -> env.view.span.from }
            }
            .field {
                it.name(FindingAcceptedSpan::to.name)
                    .description("End of the stretch: the observation without the acceptance, the day after its last day, or the end of the period. Null while it holds.")
                    .type(GQLScalarLocalDateTime.INSTANCE)
                    .dataFetcher { env -> env.view.span.to }
            }
            .field {
                it.name(FindingAcceptedSpan::acceptance.name)
                    .description("Acceptance of the first observation")
                    .type(GraphQLNonNull(gqlTypeFindingAcceptance.typeRef))
                    .dataFetcher { env -> env.view.span.acceptance }
            }
            .field {
                it.name("fromValidationRun")
                    .description("Run of the first observation")
                    .type(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN))
                    .dataFetcher { env -> env.view.fromValidationRun }
            }
            .field {
                it.name("fromBuild")
                    .description("Display name of the build of the first observation")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.fromBuild }
            }
            .build()

    private val DataFetchingEnvironment.view: FindingAcceptedSpanView
        get() = getSource()!!
}
