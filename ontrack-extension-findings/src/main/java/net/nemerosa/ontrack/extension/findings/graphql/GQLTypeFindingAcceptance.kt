package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.model.FindingAcceptance
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import org.springframework.stereotype.Component

@Component
class GQLTypeFindingAcceptance : GQLType {

    override fun getTypeName(): String = FindingAcceptance::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Decision recorded outside Yontrack, read by it, that a security finding is tolerated, possibly until an expiry.")
            .field {
                it.name(FindingAcceptance::statement.name)
                    .description("Why the finding is tolerated")
                    .type(GraphQLString)
            }
            .field {
                it.name(FindingAcceptance::expiresAt.name)
                    .description("Last day the acceptance holds, as an ISO date (YYYY-MM-DD), if any")
                    .type(GraphQLString)
                    .dataFetcher { env ->
                        env.getSource<FindingAcceptance>()?.expiresAt?.toString()
                    }
            }
            .field {
                it.name(FindingAcceptance::source.name)
                    .description("Where the decision is recorded, like a suppressions file")
                    .type(GraphQLString)
            }
            .field {
                it.name("effective")
                    .description("Whether the acceptance holds today: it has no expiry, or its expiry is not past. Evaluated when it is read.")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .dataFetcher { env ->
                        env.getSource<FindingAcceptance>()?.isEffectiveOn(Time.now.toLocalDate())
                    }
            }
            .build()
}
