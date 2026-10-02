package net.nemerosa.ontrack.extension.github.ui.graphql

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.github.app.GitHubAppTokenService
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.model.GitHubAuthenticationType
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.model.getAppInstallationTokenInformation
import net.nemerosa.ontrack.graphql.schema.GQLFieldContributor
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.graphQLFieldContributions
import net.nemerosa.ontrack.graphql.support.booleanField
import net.nemerosa.ontrack.graphql.support.stringField
import org.springframework.stereotype.Component

@Component
class GQLTypeGitHubEngineConfiguration(
    private val fieldContributors: List<GQLFieldContributor>,
    private val gqlTypeGitHubRateLimit: GQLTypeGitHubRateLimit,
    private val gqlTypeGitHubAppToken: GQLTypeGitHubAppToken,
    private val gitHubClientFactory: OntrackGitHubClientFactory,
    private val gitHubAppTokenService: GitHubAppTokenService,
) : GQLType {

    override fun getTypeName(): String = GitHubEngineConfiguration::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType = GraphQLObjectType.newObject()
        .name(typeName)
        .description("Configuration to connect to GitHub")
        .stringField(
            "name",
            "Name of the configuration"
        )
        .stringField(
            GitHubEngineConfiguration::url,
            "URL to GitHub"
        )
        .stringField(
            "user",
            "User used to connect to GitHub. Can be used with a password or an OAuth2 token. Not needed if using only the OAUth2 token or a GitHub App."
        )
        .stringField(
            GitHubEngineConfiguration::appId,
            "ID of the GitHub App used for authentication"
        )
        .stringField(
            GitHubEngineConfiguration::appInstallationAccountName,
            "Name of the account where the GitHub App is installed."
        )
        .booleanField(
            GitHubEngineConfiguration::workflowSendId,
            "Whether to pass the id input to the dispatched workflows, to find their run when GitHub does not return it"
        )
        .field {
            it.name("rateLimits")
                .description("Rate limits for this configuration")
                .type(gqlTypeGitHubRateLimit.typeRef)
                .dataFetcher { env ->
                    val config: GitHubEngineConfiguration = env.getSource()!!
                    val client = gitHubClientFactory.create(config)
                    client.getRateLimit()
                }
        }
        .field {
            it.name("authenticationType")
                .description("Authentication type")
                .type(GraphQLString)
                .dataFetcher { env ->
                    val config: GitHubEngineConfiguration = env.getSource()!!
                    config.authenticationType.name
                }
        }
        .field {
            it.name("appToken")
                .description("GitHub App token information")
                .type(gqlTypeGitHubAppToken.typeRef)
                .dataFetcher { env ->
                    val config: GitHubEngineConfiguration = env.getSource()!!
                    if (config.authenticationType == GitHubAuthenticationType.APP) {
                        gitHubAppTokenService.getAppInstallationTokenInformation(config)
                    } else {
                        null
                    }
                }
        }
        .fields(GitHubEngineConfiguration::class.java.graphQLFieldContributions(fieldContributors))
        .build()
}