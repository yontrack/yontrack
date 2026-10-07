package net.nemerosa.ontrack.kdsl.spec.admin

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.CreateUserMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.DeleteAccountMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GrantGlobalRoleToAccountMutation
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.connector.parse

/**
 * Admin interface to Ontrack.
 */
class AdminMgt(connector: Connector) : Connected(connector) {

    /**
     * Gets a list of log entries
     *
     * @param text Filter on text message
     * @param count Number of entries to return
     */
    fun logEntries(text: String = "", count: Int = 1): List<LogEntry> =
        connector.get(
            path = "/rest/admin/logs",
            query = mapOf(
                "count" to count.toString(),
                "text" to text,
            ),
        )
            .body
            .parse<LogEntries>()
            .resources

    /**
     * Mgt. of predefined promotion levels
     */
    val predefinedPromotionLevels: PredefinedPromotionLevelsMgt by lazy {
        PredefinedPromotionLevelsMgt(connector)
    }

    /**
     * Creating a user
     */
    fun createUser(
        email: String,
        fullName: String = "Test $email",
    ): Account =
        graphqlConnector.mutate(
            CreateUserMutation(
                fullName,
                email,
            )
        ) { it?.createTestAccount?.payloadUserErrors?.convert() }
            ?.checkData { it.createTestAccount?.account }
            ?.run {
                Account(
                    id = id.toInt(),
                    email = email,
                )
            }
            ?: error("could not create user")

    /**
     * Deleting an account. The agents it owns are deleted with it.
     */
    fun deleteAccount(account: Account) {
        graphqlConnector.mutate(
            DeleteAccountMutation(account.id)
        ) { it?.deleteAccount?.payloadUserErrors?.convert() }
    }

    fun grantGlobalRoleToAccount(account: Account, globalRole: String) {
        graphqlConnector.mutate(
            GrantGlobalRoleToAccountMutation(
                account.id,
                globalRole
            )
        ) { it?.grantGlobalRoleToAccount?.payloadUserErrors?.convert() }
    }

}