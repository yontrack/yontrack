package net.nemerosa.ontrack.extension.notifications.http.channel

import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import dev.cel.common.CelAbstractSyntaxTree
import dev.cel.common.CelOptions
import dev.cel.common.CelValidationException
import dev.cel.common.types.CelType
import dev.cel.common.types.CelTypeProvider
import dev.cel.common.types.MapType
import dev.cel.common.types.SimpleType
import dev.cel.common.types.StructType
import dev.cel.common.values.NullValue
import dev.cel.compiler.CelCompiler
import dev.cel.compiler.CelCompilerFactory
import dev.cel.parser.CelStandardMacro
import dev.cel.runtime.CelEvaluationException
import dev.cel.runtime.CelRuntime
import dev.cel.runtime.CelRuntimeFactory
import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration
import net.nemerosa.ontrack.json.parseAsJson
import tools.jackson.databind.JsonNode
import java.util.*

/**
 * An expression of the `http` channel which cannot be compiled.
 */
class HttpCallCelCompilationException(message: String) : HttpCallConfigException(message)

/**
 * An expression of the `http` channel which cannot be evaluated on a response.
 */
class HttpCallCelEvaluationException(message: String) : RuntimeException(message)

/**
 * What a CEL expression of the `http` channel sees, and nothing else:
 *
 * - `response.status` (int)
 * - `response.headers`: map of the headers by lowercase name, with their first value; the headers which
 *   could carry a secret are left out, as in the output
 * - `response.body`: the parsed JSON body (`dyn`)
 * - `attempt` (int): number of the request, 1 for the initial one
 *
 * @see HttpCallCel
 */
class HttpCallCelContext private constructor(
    private val variables: Map<String, Any>,
) {

    internal fun toVariables(): Map<String, Any> = variables

    companion object {

        /**
         * Context of a response.
         *
         * @throws HttpCallCelEvaluationException When the body is empty, truncated, or not JSON
         */
        fun of(response: HttpCallResponse, configuration: HttpConfiguration, attempt: Int): HttpCallCelContext {
            val body = response.celBody()
            return HttpCallCelContext(
                mapOf(
                    HttpCallCel.RESPONSE to mapOf(
                        HttpCallCel.STATUS to response.status.toLong(),
                        HttpCallCel.HEADERS to response.firstHeaders(configuration),
                        HttpCallCel.BODY to body.toCel(),
                    ),
                    HttpCallCel.ATTEMPT to attempt.toLong(),
                )
            )
        }

        private fun HttpCallResponse.celBody(): JsonNode {
            if (body.isEmpty()) {
                throw HttpCallCelEvaluationException("The response body is empty, conditions cannot be evaluated.")
            }
            if (truncated) {
                throw HttpCallCelEvaluationException("The response body was truncated, conditions cannot be evaluated.")
            }
            val contentType = contentTypeOrNull()
            if (contentType == null || !HttpCallResponse.isJson(contentType)) {
                throw HttpCallCelEvaluationException(
                    "The response body is not JSON (${contentType ?: "no content type"}), conditions cannot be evaluated."
                )
            }
            return try {
                String(body, contentType.charset ?: Charsets.UTF_8).parseAsJson()
            } catch (_: Exception) {
                throw HttpCallCelEvaluationException("The response body is not valid JSON, conditions cannot be evaluated.")
            }
        }

        /**
         * JSON as the native values CEL works with.
         */
        private fun JsonNode.toCel(): Any = when {
            isObject -> properties().associate { (name, value) -> name to value.toCel() }
            isArray -> values().map { it.toCel() }
            isString -> asString()
            isBoolean -> booleanValue()
            isIntegralNumber && canConvertToLong() -> longValue()
            isNumber -> doubleValue()
            else -> NullValue.NULL_VALUE
        }
    }
}

/**
 * Compiled CEL expression of the `http` channel.
 *
 * @param T Type of the result
 */
abstract class HttpCallCelExpression<T>(
    private val label: String,
    val expression: String,
    private val program: CelRuntime.Program,
) {

    /**
     * Evaluates the expression.
     *
     * @throws HttpCallCelEvaluationException When it fails, or exceeds its cost limit
     */
    fun evaluate(context: HttpCallCelContext): T {
        var cost = 0
        val result = try {
            program.trace(context.toVariables()) { _, _ ->
                if (++cost > HttpCallCel.COST_LIMIT) {
                    throw HttpCallCelCostException()
                }
            }
        } catch (_: HttpCallCelCostException) {
            throw HttpCallCelEvaluationException("$label `$expression` exceeded the evaluation cost limit.")
        } catch (ex: CelEvaluationException) {
            if (ex.findCause<HttpCallCelCostException>() != null) {
                throw HttpCallCelEvaluationException("$label `$expression` exceeded the evaluation cost limit.")
            }
            throw HttpCallCelEvaluationException("$label `$expression` could not be evaluated: ${ex.message}")
        }
        return convert(result)
            ?: throw HttpCallCelEvaluationException("$label `$expression` did not return a $resultTypeName: $result")
    }

    protected abstract val resultTypeName: String

    protected abstract fun convert(result: Any?): T?

    override fun toString(): String = expression

    private inline fun <reified E : Throwable> Throwable.findCause(): E? {
        var current: Throwable? = this
        while (current != null) {
            if (current is E) return current
            current = current.cause
        }
        return null
    }
}

/**
 * Condition of the `http` channel.
 */
class HttpCallCelCondition internal constructor(label: String, expression: String, program: CelRuntime.Program) :
    HttpCallCelExpression<Boolean>(label, expression, program) {
    override val resultTypeName: String = "boolean"
    override fun convert(result: Any?): Boolean? = result as? Boolean
}

/**
 * Status URL of the `http` channel.
 */
class HttpCallCelStatusUrl internal constructor(label: String, expression: String, program: CelRuntime.Program) :
    HttpCallCelExpression<String>(label, expression, program) {
    override val resultTypeName: String = "string"
    override fun convert(result: Any?): String? = result as? String
}

/**
 * Cost limit reached, aborting an evaluation.
 */
private class HttpCallCelCostException : RuntimeException("Cost limit exceeded")

/**
 * The CEL environment of the `http` channel (decisions 15 and 16).
 *
 * - Expressions are compiled and type-checked against [HttpCallCelContext].
 * - Their evaluation is bounded by a cost limit of [COST_LIMIT] evaluation steps, which nested
 *   comprehensions over a large body reach long before they could hold a thread. The length of an
 *   expression and the size of its regular expressions are bounded too.
 */
object HttpCallCel {

    internal const val RESPONSE = "response"
    internal const val STATUS = "status"
    internal const val HEADERS = "headers"
    internal const val BODY = "body"
    internal const val ATTEMPT = "attempt"

    /**
     * Maximum number of evaluation steps of an expression.
     */
    const val COST_LIMIT = 50_000

    /**
     * Maximum length of an expression.
     */
    private const val MAX_LENGTH = 4096

    private val responseType: StructType = StructType.create(
        "yontrack.http.Response",
        ImmutableSet.of(STATUS, HEADERS, BODY),
    ) { field ->
        when (field) {
            STATUS -> Optional.of(SimpleType.INT)
            HEADERS -> Optional.of(MapType.create(SimpleType.STRING, SimpleType.STRING))
            BODY -> Optional.of(SimpleType.DYN)
            else -> Optional.empty()
        }
    }

    private val typeProvider = object : CelTypeProvider {
        override fun types(): ImmutableCollection<CelType> = ImmutableList.of(responseType)
        override fun findType(typeName: String): Optional<CelType> =
            if (typeName == responseType.name()) Optional.of(responseType) else Optional.empty()
    }

    private val options: CelOptions = CelOptions.current()
        .maxExpressionCodePointSize(MAX_LENGTH)
        // Backstop only: the cost limit, counting every step, is reached first
        .comprehensionMaxIterations(COST_LIMIT)
        .maxRegexProgramSize(1_000)
        .enableHeterogeneousNumericComparisons(true)
        .build()

    private fun compiler(resultType: CelType): CelCompiler = CelCompilerFactory.standardCelCompilerBuilder()
        .setOptions(options)
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .setTypeProvider(typeProvider)
        .addVar(RESPONSE, responseType)
        .addVar(ATTEMPT, SimpleType.INT)
        .setResultType(resultType)
        .build()

    private val conditionCompiler = compiler(SimpleType.BOOL)
    private val statusUrlCompiler = compiler(SimpleType.STRING)

    private val runtime: CelRuntime = CelRuntimeFactory.standardCelRuntimeBuilder()
        .setOptions(options)
        .build()

    /**
     * Compiles a condition.
     *
     * @param label Name of the condition, for the messages
     * @param expression CEL expression, returning a boolean
     * @throws HttpCallCelCompilationException When it is not valid
     */
    fun compileCondition(label: String, expression: String) = HttpCallCelCondition(
        label,
        expression,
        compile(label, expression, conditionCompiler),
    )

    /**
     * Compiles a status URL.
     *
     * @param expression CEL expression, returning a string
     * @throws HttpCallCelCompilationException When it is not valid
     */
    fun compileStatusUrl(expression: String) = HttpCallCelStatusUrl(
        STATUS_URL_LABEL,
        expression,
        compile(STATUS_URL_LABEL, expression, statusUrlCompiler),
    )

    private const val STATUS_URL_LABEL = "Status URL"

    private fun compile(label: String, expression: String, compiler: CelCompiler): CelRuntime.Program {
        fun invalid(reason: String): Nothing =
            throw HttpCallCelCompilationException("$label `$expression` is not valid: $reason")
        if (expression.isBlank()) {
            invalid("it is empty.")
        }
        val ast: CelAbstractSyntaxTree = try {
            compiler.compile(expression).ast
        } catch (ex: CelValidationException) {
            invalid(ex.errors.joinToString("; ") { it.message }.ifBlank { ex.message ?: "" })
        }
        return try {
            runtime.createProgram(ast)
        } catch (ex: Exception) {
            invalid(ex.message ?: ex::class.java.simpleName)
        }
    }
}
