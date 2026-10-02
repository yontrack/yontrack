package net.nemerosa.ontrack.extension.github.ui.graphql

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import org.springframework.stereotype.Component

@Component
class GitHubConfigurationsMutations(
    private val gitHubConfigurationService: GitHubConfigurationService,
) : TypedMutationProvider() {
    override val mutations: List<Mutation> = listOf(
        /**
         * Creating a configuration
         */
        simpleMutation(
            name = "createGitHubConfiguration",
            description = "Creates a new GitHub configuration.",
            input = CreateGitHubConfigurationInput::class,
            outputName = "configuration",
            outputDescription = "Saved configuration",
            outputType = GitHubEngineConfiguration::class
        ) { input ->
            gitHubConfigurationService.newConfiguration(
                input.run {
                    GitHubEngineConfiguration(
                        name = name,
                        url = url,
                        user = user,
                        password = password,
                        oauth2Token = oauth2Token,
                        appId = appId,
                        appPrivateKey = appPrivateKey,
                        appInstallationAccountName = appInstallationAccountName,
                        autoMergeToken = autoMergeToken,
                        workflowSendId = workflowSendId ?: GitHubEngineConfiguration.DEFAULT_WORKFLOW_SEND_ID,
                    )
                }
            )
        }
    )
}

@APIDescription("Input for the creation of a GitHub configuration.")
data class CreateGitHubConfigurationInput(
    @APIDescription("Name of this configuration")
    val name: String,
    @APIDescription("GitHub server URL. Null will be replaced by https://github.com")
    val url: String?,
    @APIDescription("User name for authentication")
    val user: String? = null,
    @APIDescription("Password for authentication")
    @Deprecated("Prefer using token or GitHub App based authentication")
    val password: String? = null,
    @APIDescription("Personal Access Token")
    val oauth2Token: String? = null,
    @APIDescription("ID of the GitHub App to use for authentication")
    val appId: String? = null,
    @APIDescription("GitHub App private key")
    val appPrivateKey: String? = null,
    @APIDescription("Account name of the GitHub App installation (used when more than 1 installation for the app)")
    val appInstallationAccountName: String? = null,
    @APIDescription("Token for an account used to approve pull requests for auto approval processes")
    val autoMergeToken: String? = null,
    @APIDescription("Whether to pass the id input to the dispatched workflows, to find their run when GitHub does not return it. Defaults to true.")
    val workflowSendId: Boolean? = null,
)