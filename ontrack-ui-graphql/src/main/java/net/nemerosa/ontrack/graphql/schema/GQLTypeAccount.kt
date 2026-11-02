package net.nemerosa.ontrack.graphql.schema

import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.support.enumField
import net.nemerosa.ontrack.graphql.support.field
import net.nemerosa.ontrack.graphql.support.idField
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.TokensService
import org.springframework.stereotype.Component

@Component
class GQLTypeAccount(
    private val accountService: AccountService,
    private val securityService: SecurityService,
    private val tokensService: TokensService,
    private val agentService: AgentService,
    private val globalRole: GQLTypeGlobalRole,
    private val authorizedProject: GQLTypeAuthorizedProject,
    private val token: GQLTypeToken,
    private val fieldContributors: List<GQLFieldContributor>,
) : GQLType {

    override fun getTypeName(): String = ACCOUNT

    override fun createType(cache: GQLTypeCache): GraphQLObjectType {
        return GraphQLObjectType.newObject()
            .name(ACCOUNT)
            .field(idField())
            .stringField(Account::fullName, "Full name of the account")
            .stringField(Account::email, "Email of the account")
            .stringField(Account::role.name, "Security role (admin or none)")
            .enumField(Account::kind, "Kind of the account: a person or a registered agent")
            .field(
                Account::owner,
                ACCOUNT,
                "For an agent, the person accountable for it - only its ID, full name and email. Null for a person.",
            )
            .stringField(Account::agentTool, "For an agent, the tool behind it (Claude Code, Codex, Copilot, Devin, ...)")
            .stringField(Account::agentDescription, "For an agent, its optional description")
            .field {
                it.name("agents")
                    .description("Agents owned by this account. Only visible to the account itself and to administrators.")
                    .type(listType(GraphQLTypeReference(ACCOUNT)))
                    .dataFetcher { env ->
                        val account: Account = env.getSource()!!
                        if (account.isAgent) {
                            emptyList()
                        } else {
                            agentService.getAgentsOwnedBy(account)
                        }
                    }
            }
            .field {
                it.name("groups")
                    .description("List of groups the account belongs to")
                    .type(listType(GraphQLTypeReference(GQLTypeAccountGroup.ACCOUNT_GROUP)))
                    .dataFetcher(accountAccountGroupsFetcher())
            }
            .field {
                it.name("globalRole")
                    .description("Global role for the account")
                    .type(globalRole.typeRef)
                    .dataFetcher(accountGlobalRoleFetcher())
            }
            .field {
                it.name("authorizedProjects")
                    .description("List of authorized projects")
                    .type(listType(authorizedProject.typeRef))
                    .dataFetcher(accountAuthorizedProjectsFetcher())
            }
            .field {
                it.name("tokens")
                    .description("List of authentication tokens linked to this account. Readable by the account itself, by the owner of an agent, and by the holders of the account management function.")
                    .type(listType(token.typeRef))
                    .dataFetcher { env ->
                        val account: Account = env.getSource()!!
                        checkTokensAccess(account)
                        securityService.asAdmin {
                            tokensService.getTokens(account.id()).map { t ->
                                t.obfuscate()
                            }
                        }
                    }
            }
            // Links
            .fields(Account::class.java.graphQLFieldContributions(fieldContributors))
            // OK
            .build()
    }

    /**
     * An account reads its own tokens and those of its agents, and only the holder of
     * [AccountManagement] those of any other account - which can be reached through its groups,
     * by anybody. The fetcher runs as an administrator, so the check belongs here.
     */
    private fun checkTokensAccess(account: Account) {
        val current = securityService.currentUser?.account?.id
        if (current == null || (account.id != current && account.owner?.id != current)) {
            securityService.checkGlobalFunction(AccountManagement::class.java)
        }
    }

    private fun accountAuthorizedProjectsFetcher() = DataFetcher<Collection<ProjectRoleAssociation>> { env ->
        val account: Account = env.getSource()!!
        accountService.getProjectPermissionsForAccount(account)
    }

    private fun accountGlobalRoleFetcher() = DataFetcher { env ->
        val account: Account = env.getSource()!!
        accountService.getGlobalRoleForAccount(account).orElse(null)
    }

    private fun accountAccountGroupsFetcher() = DataFetcher<List<AccountGroup>> { env ->
        val id = env.getSource<Account>()!!.id
        accountService.getGroupsForAccount(id)
    }

    companion object {
        const val ACCOUNT = "Account"
    }

}