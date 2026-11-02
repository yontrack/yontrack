package net.nemerosa.ontrack.graphql.security

import graphql.ExecutionResult
import graphql.execution.AbortExecutionException
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.language.OperationDefinition
import net.nemerosa.ontrack.model.security.ReadOnlyTokenException
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Component

/**
 * Refuses to a read-only token every document naming a mutation, before any of its fields runs.
 *
 * Working on the document covers the `MutationProvider` beans and the `@MutationMapping` controllers
 * alike, with no exception, and whatever the operation the call selects among several.
 */
@Component
class ReadOnlyTokenInstrumentation(
    private val securityService: SecurityService,
) : SimplePerformantInstrumentation() {

    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters,
        state: InstrumentationState?,
    ): InstrumentationContext<ExecutionResult>? {
        val token = securityService.currentUser?.readOnlyTokenName
        if (token != null) {
            val mutation = parameters.executionContext.document
                .getDefinitionsOfType(OperationDefinition::class.java)
                .any { it.operation == OperationDefinition.Operation.MUTATION }
            if (mutation) {
                throw AbortExecutionException(ReadOnlyTokenException.mutation(token).message)
            }
        }
        return super.beginExecuteOperation(parameters, state)
    }

}
