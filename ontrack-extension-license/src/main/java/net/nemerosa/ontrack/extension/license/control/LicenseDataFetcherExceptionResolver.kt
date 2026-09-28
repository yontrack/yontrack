package net.nemerosa.ontrack.extension.license.control

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * A licensed feature used without the licence surfaces in GraphQL as a `FORBIDDEN` error naming the
 * feature, rather than as an internal error with no message: the schema never changes with the
 * licence, so this error is how a client learns that the feature is not allowed.
 */
@Component
class LicenseDataFetcherExceptionResolver : DataFetcherExceptionResolverAdapter() {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? =
        if (ex is LicenseFeatureException || ex is LicenseFeatureDataException) {
            GraphqlErrorBuilder.newError()
                .errorType(ErrorType.FORBIDDEN)
                .message(ex.message)
                .build()
        } else {
            null
        }
}
