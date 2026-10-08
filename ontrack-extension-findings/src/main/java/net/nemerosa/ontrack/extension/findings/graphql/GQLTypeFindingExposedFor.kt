package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLBoolean
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.query.FindingExposedFor
import net.nemerosa.ontrack.extension.findings.query.FindingExposedForView
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeBranch
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationStamp
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import net.nemerosa.ontrack.graphql.support.GQLScalarLong
import org.springframework.stereotype.Component

/**
 * How long a finding has been exposed, as the project findings table shows it.
 */
@Component
class GQLTypeFindingExposedFor : GQLType {

    override fun getTypeName(): String = FindingExposedFor::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "How long a security finding has been exposed on a set of branches: its longest ongoing period on one branch for one stamp, " +
                        "and the length of its last exposure episode — the union of its periods, which merge when they overlap or touch."
            )
            .field {
                it.name("ongoing")
                    .description("Whether a period is ongoing")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .dataFetcher { env -> env.view.exposedFor.ongoing != null }
            }
            .field {
                it.name("ongoingSeconds")
                    .description("Duration of the longest ongoing period in seconds, until now. Null when none is ongoing.")
                    .type(GQLScalarLong.INSTANCE)
                    .dataFetcher { env -> env.view.ongoingSeconds }
            }
            .field {
                it.name("since")
                    .description("Start of the longest ongoing period. Null when none is ongoing.")
                    .type(GQLScalarLocalDateTime.INSTANCE)
                    .dataFetcher { env -> env.view.exposedFor.ongoing?.startedAt }
            }
            .field {
                it.name("branch")
                    .description("Branch of the longest ongoing period. Null when none is ongoing.")
                    .type(GraphQLTypeReference(GQLTypeBranch.BRANCH))
                    .dataFetcher { env -> env.view.branch }
            }
            .field {
                it.name("validationStamp")
                    .description("Validation stamp of the longest ongoing period. Null when none is ongoing.")
                    .type(GraphQLTypeReference(GQLTypeValidationStamp.VALIDATION_STAMP))
                    .dataFetcher { env -> env.view.validationStamp }
            }
            .field {
                it.name(FindingExposedFor::accepted.name)
                    .description("Whether the exposure of the longest ongoing period is accepted")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .dataFetcher { env -> env.view.exposedFor.accepted }
            }
            .field {
                it.name(FindingExposedFor::reopened.name)
                    .description("Whether the longest ongoing period follows an earlier period of the same branch and stamp")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .dataFetcher { env -> env.view.exposedFor.reopened }
            }
            .field {
                it.name("lastEpisodeSeconds")
                    .description(
                        "Length of the last exposure episode in seconds, until now while it is ongoing: " +
                                "for a fixed finding, how long its last fix took. Null when the finding has no period."
                    )
                    .type(GQLScalarLong.INSTANCE)
                    .dataFetcher { env -> env.view.lastEpisodeSeconds }
            }
            .build()

    private val DataFetchingEnvironment.view: FindingExposedForView
        get() = getSource()!!
}
