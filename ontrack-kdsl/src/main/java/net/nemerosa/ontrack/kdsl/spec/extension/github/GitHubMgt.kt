package net.nemerosa.ontrack.kdsl.spec.extension.github

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLMissingDataException
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.CreateGitHubConfigurationMutation
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * Interface for the management of GitHub in Ontrack.
 */
class GitHubMgt(connector: Connector) : Connected(connector) {

    /**
     * Creates a configuration
     *
     * @param config Configuration to save
     * @return Saved configuration, with obfuscated values but for the name & URL.
     */
    @Suppress("DEPRECATION")
    fun createConfig(config: GitHubConfiguration): GitHubConfiguration = graphqlConnector.mutate(
        mutation = config.run {
            CreateGitHubConfigurationMutation(
                name,
                Optional.presentIfNotNull(url),
                Optional.presentIfNotNull(user),
                Optional.presentIfNotNull(password),
                Optional.presentIfNotNull(oauth2Token),
                Optional.presentIfNotNull(appId),
                Optional.presentIfNotNull(appPrivateKey),
                Optional.presentIfNotNull(appInstallationAccountName),
                Optional.presentIfNotNull(autoMergeToken),
            )
        }
    ) {
        it?.createGitHubConfiguration?.payloadUserErrors?.convert()
    }?.checkData {
        it.createGitHubConfiguration?.configuration
    }?.let {
        GitHubConfiguration(
            name = config.name,
            url = it.url,
            user = null,
            password = null,
            oauth2Token = null,
            appId = null,
            appPrivateKey = null,
            appInstallationAccountName = null,
            autoMergeToken = null,
        )
    } ?: throw GraphQLMissingDataException("Did not get back the created GitHub configuration.")

}