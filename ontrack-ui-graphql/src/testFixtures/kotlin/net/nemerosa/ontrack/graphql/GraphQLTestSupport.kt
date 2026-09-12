package net.nemerosa.ontrack.graphql

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import graphql.ErrorClassification
import net.nemerosa.ontrack.graphql.schema.UserError
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.isNullOrNullNode
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.graphql.ExecutionGraphQlResponse
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.ResponseError
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest
import org.springframework.stereotype.Component
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail

@Component
class GraphQLTestSupport(
    private val executionGraphQlService: ExecutionGraphQlService
) {


    fun run(
        query: String,
        variables: Map<String, Any?> = emptyMap()
    ): JsonNode = internalRun(query, variables, ::assertNoErrors)

    fun run(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        code: (data: JsonNode) -> Unit = {},
    ) {
        code(internalRun(query, variables, ::assertNoErrors))
    }

    /**
     * Runs the [query] and expects exactly one error, matching the given [errorClassification] and
     * [errorMessage].
     *
     * A failing non-nullable field cannot satisfy "exactly one": GraphQL adds a second
     * `NullValueInNonNullableField` error as the null bubbles up to its parent. Use
     * [runWithMatchingError] there.
     */
    fun runWithError(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        errorClassification: ErrorClassification? = null,
        errorMessage: String? = null,
    ) {
        internalRun(query, variables) { response ->
            val errors = response.errors
            if (errors.size > 1) {
                fail("Expected one error but got ${errors.size}.\n\n${errors.render()}")
            }
            assertMatchingError(errors, errorClassification, errorMessage)
        }
    }

    /**
     * Runs the [query] and expects *at least one* of the returned errors to match the given
     * [errorClassification] and [errorMessage], ignoring the others.
     *
     * This is [runWithError] without its exactly-one requirement - the variant to use when the field
     * under test is non-nullable, and the null bubbling up adds an error of its own.
     */
    fun runWithMatchingError(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        errorClassification: ErrorClassification? = null,
        errorMessage: String? = null,
    ) {
        internalRun(query, variables) { response ->
            assertMatchingError(response.errors, errorClassification, errorMessage)
        }
    }

    private fun assertMatchingError(
        errors: List<ResponseError>,
        errorClassification: ErrorClassification?,
        errorMessage: String?,
    ) {
        if (errors.isEmpty()) {
            fail("Expected some errors")
        }
        val matching = errors.any { error ->
            (errorClassification == null || errorClassification == error.errorType) &&
                    (errorMessage == null || errorMessage == error.message)
        }
        if (!matching) {
            val expectation = listOfNotNull(
                errorMessage?.let { "message = $it" },
                errorClassification?.let { "type = $it" },
            ).joinToString(" and ").ifEmpty { "any type or message" }
            fail("Expected at least one error with $expectation\n\nbut errors were:\n\n${errors.render()}")
        }
    }

    private fun List<ResponseError>.render() =
        joinToString("\n") { "* [type = ${it.errorType}] ${it.message}" }

    fun assertNoUserError(data: JsonNode, userNodeName: String): JsonNode {
        val userNode = data.path(userNodeName)
        val errors = userNode.path("errors")
        if (!errors.isNullOrNullNode() && errors.isArray && errors.size() > 0) {
            errors.forEach { error: JsonNode ->
                error.path("exception")
                    .takeIf { !it.isNullOrNullNode() }
                    ?.let { println("Error exception: ${it.asText()}") }
                error.path("location")
                    .takeIf { !it.isNullOrNullNode() }
                    ?.let { println("Error location: ${it.asText()}") }
                fail(error.path("message").asText())
            }
        }
        return userNode
    }

    fun assertUserError(
        data: JsonNode,
        userNodeName: String,
        message: String? = null,
        exception: String? = null
    ) {
        val errors = data.path(userNodeName).path("errors")
        if (errors.isNullOrNullNode()) {
            fail("Excepted the `errors` user node.")
        } else if (!errors.isArray) {
            fail("Excepted the `errors` user node to be an array.")
        } else if (errors.isEmpty) {
            fail("Excepted the `errors` user node to be a non-empty array.")
        } else {
            val error = errors.first()
            if (message != null) {
                assertEquals(message, error.path("message").asText())
            }
            if (exception != null) {
                assertEquals(exception, error.path("exception").asText())
            }
        }
    }

    fun checkGraphQLUserErrors(data: JsonNode, field: String): JsonNode {
        val payload = data.path(field)
        val node = payload.path("errors")
        if (node != null && node.isArray && node.size() > 0) {
            val error = node.first().parse<UserError>()
            throw IllegalStateException(error.toString())
        }
        return payload
    }

    fun checkGraphQLUserErrors(data: JsonNode, field: String, code: (payload: JsonNode) -> Unit) {
        val payload = checkGraphQLUserErrors(data, field)
        code(payload)
    }

    private fun <T> internalRun(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        responseProcessing: (response: ExecutionGraphQlResponse) -> T,
    ): T {
        val result = executionGraphQlService.execute(
            DefaultExecutionGraphQlRequest(
                /* document = */ query,
                /* operationName = */ null,
                /* variables = */ variables,
                /* extensions = */ null,
                /* id = */ uid("gql_"),
                /* locale = */ null,
            )
        ).block()
        assertNotNull(result)
        return responseProcessing(result)
    }

    private fun assertNoErrors(response: ExecutionGraphQlResponse): JsonNode {
        if (response.errors.isNotEmpty()) {
            fail(
                response.errors.joinToString("\n") { it.message ?: "Unknown error" }
            )
        }
        val data = response.getData<Any>().asJson()
        return assertIs<ObjectNode>(data)
    }
}