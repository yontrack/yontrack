package net.nemerosa.ontrack.extension.audittrail.ui

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceException
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * A refused evidence operation — like the verification of the evidence while the storage cannot
 * be used — surfaces in GraphQL with its message and its code, in the `code` extension.
 */
@Component
class EvidenceDataFetcherExceptionResolver : DataFetcherExceptionResolverAdapter() {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? =
        if (ex is EvidenceException) {
            GraphqlErrorBuilder.newError(env)
                .errorType(
                    when (ex.error.status) {
                        400, 413, 422 -> ErrorType.BAD_REQUEST
                        403 -> ErrorType.FORBIDDEN
                        else -> ErrorType.INTERNAL_ERROR
                    }
                )
                .message(ex.message)
                .extensions(mapOf("code" to ex.error.code))
                .build()
        } else {
            null
        }
}
