package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.model.FindingObservation
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.query.FindingObservationView
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationRun
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import org.springframework.stereotype.Component

/**
 * One sighting of a finding by one scan of one build.
 */
@Component
class GQLTypeFindingObservation(
    private val gqlTypeFinding: GQLTypeFinding,
    private val gqlTypeFindingAcceptance: GQLTypeFindingAcceptance,
) : GQLType {

    override fun getTypeName(): String = FindingObservation::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("One sighting of a security finding by one scan of one build.")
            .field {
                it.name("finding")
                    .description("Observed finding")
                    .type(GraphQLNonNull(gqlTypeFinding.typeRef))
                    .dataFetcher { env -> env.view.finding }
            }
            .field {
                it.name("validationRun")
                    .description("Validation run of the scan")
                    .type(GraphQLNonNull(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN)))
                    .dataFetcher { env -> env.view.validationRun }
            }
            .field {
                it.name(FindingObservation::time.name)
                    .description("Time of the observation")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
                    .dataFetcher { env -> env.view.observation.time }
            }
            .field {
                it.name(FindingObservation::severity.name)
                    .description("Severity asserted by the scanner in this observation")
                    .type(GraphQLNonNull(GraphQLTypeReference(FindingSeverity::class.java.simpleName)))
                    .dataFetcher { env -> env.view.observation.severity }
            }
            .field {
                it.name(FindingObservation::rawSeverity.name)
                    .description("Severity as the scanner gave it, for provenance")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.observation.rawSeverity }
            }
            .field {
                it.name(FindingObservation::installedVersion.name)
                    .description("Version of the component in which the finding was observed")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.observation.installedVersion }
            }
            .field {
                it.name(FindingObservation::fixedVersion.name)
                    .description("Version of the component fixing the finding, if any")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.observation.fixedVersion }
            }
            .field {
                it.name(FindingObservation::acceptance.name)
                    .description("Acceptance of the finding at the time of the observation, if any")
                    .type(gqlTypeFindingAcceptance.typeRef)
                    .dataFetcher { env -> env.view.observation.acceptance }
            }
            .build()

    private val DataFetchingEnvironment.view: FindingObservationView
        get() = getSource()!!
}
