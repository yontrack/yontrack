package net.nemerosa.ontrack.graphql.schema

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.deprecatedUsages
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountGroup
import net.nemerosa.ontrack.model.security.AccountGroupManagement
import net.nemerosa.ontrack.model.security.AccountManagement
import net.nemerosa.ontrack.model.security.PermissionTargetType
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.execution.ErrorType
import kotlin.test.assertEquals

/**
 * Global roles add up (ADR 0023): the GraphQL mutations granting and revoking one global role of
 * an account or a group, the reads listing them, and the deprecated mutations and fields.
 */
class AccountPermissionsMutationsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun `Granting two global roles to an account keeps both`() {
        asAdmin {
            val account = doCreateAccount()
            grantToAccount(account, Roles.GLOBAL_AUTOMATION)
            grantToAccount(account, Roles.GLOBAL_PARTICIPANT)
            assertEquals(listOf(Roles.GLOBAL_AUTOMATION, Roles.GLOBAL_PARTICIPANT), accountRoles(account))
        }
    }

    @Test
    fun `Granting two global roles to an account group keeps both`() {
        asAdmin {
            val group = doCreateAccountGroup()
            grantToGroup(group, Roles.GLOBAL_READ_ONLY)
            grantToGroup(group, Roles.GLOBAL_CREATOR)
            // Granting a role twice changes nothing
            grantToGroup(group, Roles.GLOBAL_READ_ONLY)
            assertEquals(listOf(Roles.GLOBAL_CREATOR, Roles.GLOBAL_READ_ONLY), groupRoles(group))
        }
    }

    @Test
    fun `Revoking one global role from an account keeps the other`() {
        asAdmin {
            val account = doCreateAccount()
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_AUTOMATION)
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_PARTICIPANT)
            run(
                """
                    mutation {
                        revokeGlobalRoleFromAccount(input: {
                            accountId: ${account.id},
                            globalRole: "${Roles.GLOBAL_AUTOMATION}"
                        }) {
                            errors { message }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "revokeGlobalRoleFromAccount")
            }
            assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), accountRoles(account))
        }
    }

    @Test
    fun `Revoking one global role from an account group keeps the other`() {
        asAdmin {
            val group = doCreateAccountGroup()
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_READ_ONLY)
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
            run(
                """
                    mutation {
                        revokeGlobalRoleFromAccountGroup(input: {
                            accountGroupId: ${group.id},
                            globalRole: "${Roles.GLOBAL_CREATOR}"
                        }) {
                            errors { message }
                        }
                    }
                """
            ) { data ->
                checkGraphQLUserErrors(data, "revokeGlobalRoleFromAccountGroup")
            }
            assertEquals(listOf(Roles.GLOBAL_READ_ONLY), groupRoles(group))
        }
    }

    @Test
    fun `Granting an unknown global role is a user error`() {
        asAdmin {
            val group = doCreateAccountGroup()
            run(
                """
                    mutation {
                        grantGlobalRoleToAccountGroup(input: {
                            accountGroupId: ${group.id},
                            globalRole: "UNKNOWN_ROLE"
                        }) {
                            errors { message exception }
                        }
                    }
                """
            ) { data ->
                assertUserError(
                    data, "grantGlobalRoleToAccountGroup",
                    message = "Role not found: UNKNOWN_ROLE",
                    exception = "net.nemerosa.ontrack.model.exceptions.RoleNotFoundException",
                )
            }
            assertEquals(emptyList(), groupRoles(group))
        }
    }

    @Test
    fun `Granting and revoking global roles needs the management of accounts or groups`() {
        val account = asAdmin { doCreateAccount() }
        val group = asAdmin { doCreateAccountGroup() }
        val mutations = listOf(
            "grantGlobalRoleToAccount" to "accountId: ${account.id}",
            "revokeGlobalRoleFromAccount" to "accountId: ${account.id}",
            "grantGlobalRoleToAccountGroup" to "accountGroupId: ${group.id}",
            "revokeGlobalRoleFromAccountGroup" to "accountGroupId: ${group.id}",
        )
        asUser().call {
            mutations.forEach { (name, arguments) ->
                runWithError(
                    """
                        mutation {
                            $name(input: { $arguments, globalRole: "${Roles.GLOBAL_PARTICIPANT}" }) {
                                errors { message }
                            }
                        }
                    """,
                    errorClassification = ErrorType.FORBIDDEN,
                )
            }
        }
        asUser().with(AccountManagement::class.java).with(AccountGroupManagement::class.java).call {
            mutations.filter { it.first.startsWith("grant") }.forEach { (name, arguments) ->
                run(
                    """
                        mutation {
                            $name(input: { $arguments, globalRole: "${Roles.GLOBAL_PARTICIPANT}" }) {
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, name)
                }
            }
        }
        asAdmin {
            assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), accountRoles(account))
            assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), groupRoles(group))
        }
    }

    @Test
    fun `Deprecated - deleting all the global roles of an account`() {
        asAdmin {
            val account = doCreateAccount()
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_AUTOMATION)
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_PARTICIPANT)
            val usages = meterRegistry.deprecatedUsages(DeprecationSurface.GRAPHQL, "Mutation.deleteGlobalRoleFromAccount") {
                run(
                    """
                        mutation {
                            deleteGlobalRoleFromAccount(input: {
                                accountId: ${account.id}
                            }) {
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, "deleteGlobalRoleFromAccount")
                }
            }
            assertEquals(1.0, usages, "Deprecated usage is reported")
            assertEquals(emptyList(), accountRoles(account))
        }
    }

    @Test
    fun `Deprecated - deleting all the global roles of an account group`() {
        asAdmin {
            val group = doCreateAccountGroup()
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_READ_ONLY)
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
            val usages = meterRegistry.deprecatedUsages(DeprecationSurface.GRAPHQL, "Mutation.deleteGlobalRoleFromAccountGroup") {
                run(
                    """
                        mutation {
                            deleteGlobalRoleFromAccountGroup(input: {
                                accountGroupId: ${group.id}
                            }) {
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, "deleteGlobalRoleFromAccountGroup")
                }
            }
            assertEquals(1.0, usages, "Deprecated usage is reported")
            assertEquals(emptyList(), groupRoles(group))
        }
    }

    @Test
    fun `Deprecated - the single global role of an account and of a group is the first of their roles`() {
        asAdmin {
            val account = doCreateAccount()
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_PARTICIPANT)
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_AUTOMATION)
            val group = doCreateAccountGroup()
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_READ_ONLY)
            val accountUsages = meterRegistry.deprecatedUsages(DeprecationSurface.GRAPHQL, "Account.globalRole") {
                val groupUsages = meterRegistry.deprecatedUsages(DeprecationSurface.GRAPHQL, "AccountGroup.globalRole") {
                    run(
                        """{
                            accounts(id: ${account.id}) { globalRole { id } }
                            accountGroups(id: ${group.id}) { globalRole { id } }
                        }"""
                    ) { data ->
                        assertEquals(
                            Roles.GLOBAL_AUTOMATION,
                            data.path("accounts").first().path("globalRole").path("id").asText()
                        )
                        assertEquals(
                            Roles.GLOBAL_READ_ONLY,
                            data.path("accountGroups").first().path("globalRole").path("id").asText()
                        )
                    }
                }
                assertEquals(1.0, groupUsages, "Deprecated usage is reported")
            }
            assertEquals(1.0, accountUsages, "Deprecated usage is reported")
        }
    }

    private fun grantToAccount(account: Account, role: String) {
        run(
            """
                mutation {
                    grantGlobalRoleToAccount(input: {
                        accountId: ${account.id},
                        globalRole: "$role"
                    }) {
                        errors { message }
                    }
                }
            """
        ) { data ->
            checkGraphQLUserErrors(data, "grantGlobalRoleToAccount")
        }
    }

    private fun grantToGroup(group: AccountGroup, role: String) {
        run(
            """
                mutation {
                    grantGlobalRoleToAccountGroup(input: {
                        accountGroupId: ${group.id},
                        globalRole: "$role"
                    }) {
                        errors { message }
                    }
                }
            """
        ) { data ->
            checkGraphQLUserErrors(data, "grantGlobalRoleToAccountGroup")
        }
    }

    /**
     * Global roles of an account, read through `Account.globalRoles`.
     */
    private fun accountRoles(account: Account): List<String> =
        run("""{ accounts(id: ${account.id}) { globalRoles { id } } }""")
            .path("accounts").first().path("globalRoles").values().map { it.path("id").asText() }

    /**
     * Global roles of a group, read through `AccountGroup.globalRoles`.
     */
    private fun groupRoles(group: AccountGroup): List<String> =
        run("""{ accountGroups(id: ${group.id}) { globalRoles { id } } }""")
            .path("accountGroups").first().path("globalRoles").values().map { it.path("id").asText() }
}
