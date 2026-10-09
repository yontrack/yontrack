package net.nemerosa.ontrack.graphql.limits

import graphql.ExecutionResult
import graphql.analysis.QueryComplexityCalculator
import graphql.analysis.QueryTraverser
import graphql.analysis.QueryVisitorFieldEnvironment
import graphql.execution.AbortExecutionException
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.validation.ValidationError
import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.OntrackConfigProperties.GraphQLLimitsMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Limits the cost of a GraphQL query, as set by `ontrack.config.graphql.limits`.
 *
 * The aliases and the directives are checked on the parsed document, before it is validated; the
 * depth and the complexity once it is, before the operation runs. A query going over a limit is
 * rejected with one error and none of it runs - or, in [GraphQLLimitsMode.WARN], runs after a warning.
 *
 * The limits are read for every query, so that they follow the configuration.
 */
@Component
class GraphQLLimitsInstrumentation(
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val meterRegistry: MeterRegistry,
) : SimplePerformantInstrumentation() {

    private val logger: Logger = LoggerFactory.getLogger(GraphQLLimitsInstrumentation::class.java)

    override fun beginValidation(
        parameters: InstrumentationValidationParameters,
        state: InstrumentationState?,
    ): InstrumentationContext<List<ValidationError>>? {
        val limits = ontrackConfigProperties.graphql.limits
        val schema = parameters.schema
        val breaches = GraphQLDocumentLimits(
            maxAliases = limits.maxAliases,
            maxDirectivesPerLocation = limits.maxDirectivesPerLocation,
            isRepeatable = { name -> schema.getDirective(name)?.isRepeatable == true },
        ).check(parameters.document)
        handle(breaches, limits.mode, parameters.operation)
        return super.beginValidation(parameters, state)
    }

    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters,
        state: InstrumentationState?,
    ): InstrumentationContext<ExecutionResult>? {
        val limits = ontrackConfigProperties.graphql.limits
        val context = parameters.executionContext
        val operation = context.executionInput.operationName

        val depth = QueryTraverser.newQueryTraverser()
            .schema(context.graphQLSchema)
            .document(context.document)
            .operationName(operation)
            .coercedVariables(context.coercedVariables)
            .build()
            .reducePreOrder({ env, acc -> maxOf(depth(env), acc) }, 0)

        val complexity = QueryComplexityCalculator.newCalculator()
            .fieldComplexityCalculator { _, childComplexity -> 1 + childComplexity }
            .schema(context.graphQLSchema)
            .document(context.document)
            .operationName(operation)
            .variables(context.coercedVariables)
            .build()
            .calculate()

        handle(
            listOfNotNull(
                depth.takeIf { it > limits.maxDepth }?.let {
                    GraphQLLimitBreach.depth(it, limits.maxDepth)
                },
                complexity.takeIf { it > limits.maxComplexity }?.let {
                    GraphQLLimitBreach.complexity(it, limits.maxComplexity)
                },
            ),
            limits.mode,
            operation,
        )
        return super.beginExecuteOperation(parameters, state)
    }

    /**
     * Number of nested field levels down to the field of [env], included.
     */
    private fun depth(env: QueryVisitorFieldEnvironment): Int {
        var depth = 0
        var current: QueryVisitorFieldEnvironment? = env
        while (current != null) {
            depth++
            current = current.parentEnvironment
        }
        return depth
    }

    private fun handle(breaches: List<GraphQLLimitBreach>, mode: GraphQLLimitsMode, operation: String?) {
        if (breaches.isEmpty()) return
        breaches.forEach { breach ->
            meterRegistry.counter(
                GraphQLLimitsMetrics.exceeded,
                "limit", breach.limit.id,
                "mode", mode.id,
            ).increment()
        }
        when (mode) {
            GraphQLLimitsMode.ENFORCE -> throw AbortExecutionException(breaches.first().message)
            GraphQLLimitsMode.WARN -> breaches.forEach { breach ->
                logger.warn(
                    "[graphql] Limit not enforced for operation {}: {}",
                    operation ?: "(anonymous)",
                    breach.message,
                )
            }
        }
    }

}
