package net.nemerosa.ontrack.graphql.exceptions

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import net.nemerosa.ontrack.model.exceptions.NotFoundException
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * Without this resolver, the message of a [NotFoundException] would be replaced by a generic
 * `INTERNAL_ERROR` message. These messages are written for the users and are already returned
 * as such by the mutations.
 */
@Component
class NotFoundDataFetcherExceptionResolver : DataFetcherExceptionResolverAdapter() {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? =
        if (ex is NotFoundException) {
            GraphqlErrorBuilder.newError()
                .errorType(ErrorType.NOT_FOUND)
                .message(ex.message)
                .build()
        } else {
            null
        }

}
