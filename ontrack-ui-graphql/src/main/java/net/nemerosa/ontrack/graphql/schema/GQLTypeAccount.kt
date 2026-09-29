package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLString
import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.support.idField
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.graphql.support.toNotNull
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.TokensService
import org.springframework.stereotype.Component

@Component
class GQLTypeAccount(
    private val accountService: AccountService,
    private val securityService: SecurityService,
    private val tokensService: TokensService,
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
            .field {
                it.name("name")
                    .description("Unique name for the account")
                    .deprecate("Removed in V6. Use email instead. See #1921")
                    .type(GraphQLString.toNotNull())
                    .dataFetcher { env ->
                        val account: Account = env.getSource()!!
                        account.email
                    }
            }
            .stringField(Account::fullName, "Full name of the account")
            .stringField(Account::email, "Email of the account")
            .stringField(Account::role.name, "Security role (admin or none)")
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
                    .description("List of authentication tokens linked to this account.")
                    .type(listType(token.typeRef))
                    .dataFetcher { env ->
                        val account: Account = env.getSource()!!
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