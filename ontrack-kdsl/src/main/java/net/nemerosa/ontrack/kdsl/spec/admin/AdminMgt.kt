package net.nemerosa.ontrack.kdsl.spec.admin

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.AccountGroupByNameQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.CreateAccountGroupMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.CreateUserMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.DeleteAccountMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GrantGlobalRoleToAccountMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GrantProjectRoleToAccountGroupMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GrantProjectRoleToAccountMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.RevokeProjectRoleFromAccountGroupMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.RevokeProjectRoleFromAccountMutation
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.connector.parse
import net.nemerosa.ontrack.kdsl.spec.Project

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

    /**
     * Creating an account group
     */
    fun createAccountGroup(
        name: String,
        description: String? = null,
    ): AccountGroup =
        graphqlConnector.mutate(
            CreateAccountGroupMutation(
                name,
                Optional.presentIfNotNull(description),
            )
        ) { it?.createAccountGroup?.payloadUserErrors?.convert() }
            ?.checkData { it.createAccountGroup?.accountGroup }
            ?.run {
                AccountGroup(
                    id = id.toInt(),
                    name = name ?: error("account group without a name"),
                )
            }
            ?: error("could not create account group")

    /**
     * Finding an account group using its name
     *
     * @return Account group or null if not found
     */
    fun findAccountGroupByName(name: String): AccountGroup? =
        graphqlConnector.query(
            AccountGroupByNameQuery(name)
        )?.accountGroupByName?.run {
            AccountGroup(
                id = id.toInt(),
                name = this.name ?: name,
            )
        }

    /**
     * Grants a project role to an account, keeping the other roles it holds on the project.
     * Does nothing when the account already holds the role.
     */
    fun grantProjectRoleToAccount(project: Project, account: Account, projectRole: String) {
        graphqlConnector.mutate(
            GrantProjectRoleToAccountMutation(
                project.id.toInt(),
                account.id,
                projectRole,
            )
        ) { it?.grantProjectRoleToAccount?.payloadUserErrors?.convert() }
    }

    /**
     * Grants a project role to an account group, keeping the other roles it holds on the project.
     * Does nothing when the group already holds the role.
     */
    fun grantProjectRoleToAccountGroup(project: Project, group: AccountGroup, projectRole: String) {
        graphqlConnector.mutate(
            GrantProjectRoleToAccountGroupMutation(
                project.id.toInt(),
                group.id,
                projectRole,
            )
        ) { it?.grantProjectRoleToAccountGroup?.payloadUserErrors?.convert() }
    }

    /**
     * Revokes one project role from an account, keeping the other roles it holds on the project.
     */
    fun revokeProjectRoleFromAccount(project: Project, account: Account, projectRole: String) {
        graphqlConnector.mutate(
            RevokeProjectRoleFromAccountMutation(
                project.id.toInt(),
                account.id,
                projectRole,
            )
        ) { it?.revokeProjectRoleFromAccount?.payloadUserErrors?.convert() }
    }

    /**
     * Revokes one project role from an account group, keeping the other roles it holds on the project.
     */
    fun revokeProjectRoleFromAccountGroup(project: Project, group: AccountGroup, projectRole: String) {
        graphqlConnector.mutate(
            RevokeProjectRoleFromAccountGroupMutation(
                project.id.toInt(),
                group.id,
                projectRole,
            )
        ) { it?.revokeProjectRoleFromAccountGroup?.payloadUserErrors?.convert() }
    }

}
