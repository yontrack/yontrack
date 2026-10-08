package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.query.FindingSighting
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeBranch
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationRun
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationStamp
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import org.springframework.stereotype.Component

/**
 * Where and when a finding was seen at a given moment.
 */
@Component
class GQLTypeFindingSighting : GQLType {

    override fun getTypeName(): String = FindingSighting::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Where and when a security finding was seen at a given moment, like its discovery or its resolution: branch, stamp, run and build.")
            .field {
                it.name(FindingSighting::time.name)
                    .description("Time of the run")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
                    .dataFetcher { env -> env.view.time }
            }
            .field {
                it.name(FindingSighting::branch.name)
                    .description("Branch of the run")
                    .type(GraphQLNonNull(GraphQLTypeReference(GQLTypeBranch.BRANCH)))
                    .dataFetcher { env -> env.view.branch }
            }
            .field {
                it.name(FindingSighting::validationStamp.name)
                    .description("Validation stamp of the run")
                    .type(GraphQLNonNull(GraphQLTypeReference(GQLTypeValidationStamp.VALIDATION_STAMP)))
                    .dataFetcher { env -> env.view.validationStamp }
            }
            .field {
                it.name(FindingSighting::validationRun.name)
                    .description("Run, null once its build is purged, or for a period older than the periods themselves")
                    .type(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN))
                    .dataFetcher { env -> env.view.validationRun }
            }
            .field {
                it.name(FindingSighting::build.name)
                    .description("Display name of the build of the run, null when unknown")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.build }
            }
            .build()

    private val DataFetchingEnvironment.view: FindingSighting
        get() = getSource()!!
}
