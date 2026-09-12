package net.nemerosa.ontrack.graphql

import com.fasterxml.jackson.databind.JsonNode
import graphql.ErrorClassification
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractQLKTITSupport : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var graphQLTestSupport: GraphQLTestSupport

    fun run(
        query: String,
        variables: Map<String, Any?> = emptyMap()
    ): JsonNode = graphQLTestSupport.run(query, variables)

    fun run(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        code: (data: JsonNode) -> Unit = {},
    ) {
        graphQLTestSupport.run(query, variables, code)
    }

    fun runWithError(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        errorClassification: ErrorClassification? = null,
        errorMessage: String? = null,
    ) {
        graphQLTestSupport.runWithError(query, variables, errorClassification, errorMessage)
    }

    fun runWithMatchingError(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        errorClassification: ErrorClassification? = null,
        errorMessage: String? = null,
    ) {
        graphQLTestSupport.runWithMatchingError(query, variables, errorClassification, errorMessage)
    }

    protected fun assertNoUserError(data: JsonNode, userNodeName: String): JsonNode {
        return graphQLTestSupport.assertNoUserError(data, userNodeName)
    }

    protected fun assertUserError(
        data: JsonNode,
        userNodeName: String,
        message: String? = null,
        exception: String? = null
    ) {
        graphQLTestSupport.assertUserError(data, userNodeName, message, exception)
    }

    protected fun checkGraphQLUserErrors(data: JsonNode, field: String): JsonNode {
        return graphQLTestSupport.checkGraphQLUserErrors(data, field)
    }

    protected fun checkGraphQLUserErrors(data: JsonNode, field: String, code: (payload: JsonNode) -> Unit) {
        graphQLTestSupport.checkGraphQLUserErrors(data, field, code)
    }

}
