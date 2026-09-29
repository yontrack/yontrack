package net.nemerosa.ontrack.graphql.deprecation

import graphql.execution.instrumentation.FieldFetchingInstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.GraphQLNamedType
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import org.springframework.stereotype.Component

/**
 * Reports every deprecated GraphQL field, and every deprecated argument given explicitly,
 * which is actually queried.
 */
@Component
class DeprecationInstrumentation(
    private val deprecationService: DeprecationService,
) : SimplePerformantInstrumentation() {

    override fun beginFieldFetching(
        parameters: InstrumentationFieldFetchParameters,
        state: InstrumentationState?,
    ): FieldFetchingInstrumentationContext? {
        val environment = parameters.environment
        val field = environment.fieldDefinition
        val typeName = (environment.parentType as? GraphQLNamedType)?.name ?: "?"
        if (field.isDeprecated) {
            deprecationService.deprecatedUsage(
                surface = DeprecationSurface.GRAPHQL,
                item = "$typeName.${field.name}",
                message = field.deprecationReason ?: "",
            )
        }
        if (field.arguments.any { it.isDeprecated }) {
            val given = environment.mergedField.arguments.map { it.name }.toSet()
            field.arguments
                .filter { it.isDeprecated && it.name in given }
                .forEach { argument ->
                    deprecationService.deprecatedUsage(
                        surface = DeprecationSurface.GRAPHQL,
                        item = "$typeName.${field.name}(${argument.name})",
                        message = argument.deprecationReason ?: "",
                    )
                }
        }
        return super.beginFieldFetching(parameters, state)
    }
}
