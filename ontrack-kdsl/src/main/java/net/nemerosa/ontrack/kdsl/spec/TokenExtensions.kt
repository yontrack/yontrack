package net.nemerosa.ontrack.kdsl.spec

import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GenerateTokenMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.RevokeTokenMutation
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * Generates an API token for the account this client is connected as.
 *
 * @param name Name of the token, unique for the account
 * @return Value of the token — the only time it can be read
 */
fun Ontrack.generateToken(name: String): String =
    graphqlConnector.mutate(
        GenerateTokenMutation(name)
    ) {
        it?.generateToken?.payloadUserErrors?.convert()
    }
        ?.checkData { it.generateToken?.token?.value }
        ?: error("Did not get back the value of the generated token $name")

/**
 * Revokes an API token of the account this client is connected as. Nothing happens when the
 * account has no token of that name.
 *
 * @param name Name of the token
 */
fun Ontrack.revokeToken(name: String) {
    graphqlConnector.mutate(
        RevokeTokenMutation(name)
    ) {
        it?.revokeToken?.payloadUserErrors?.convert()
    }
}
