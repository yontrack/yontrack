package net.nemerosa.ontrack.extension.bitbucket.cloud

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationProperty
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationPropertyType
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractBitbucketCloudTestSupport : AbstractQLKTITSupport() {

    @Autowired
    protected lateinit var bitbucketCloudConfigurationService: BitbucketCloudConfigurationService

    @Autowired
    protected lateinit var bitbucketCloudProjectConfigurationPropertyType: BitbucketCloudProjectConfigurationPropertyType

    protected fun deleteAllConfigs() {
        asAdmin {
            val names = bitbucketCloudConfigurationService.configurations.map { it.name }
            names.forEach { name ->
                bitbucketCloudConfigurationService.deleteConfiguration(name)
            }
        }
    }

    protected fun Project.setBitbucketCloudProperty(
        config: BitbucketCloudConfiguration,
        repository: String,
        workspace: String = "my-workspace",
        indexationInterval: Int = 0,
        issueServiceConfigurationIdentifier: String? = null,
    ) {
        setProperty(
            this,
            BitbucketCloudProjectConfigurationPropertyType::class.java,
            BitbucketCloudProjectConfigurationProperty(
                configuration = config,
                workspace = workspace,
                repository = repository,
                indexationInterval = indexationInterval,
                issueServiceConfigurationIdentifier = issueServiceConfigurationIdentifier,
            )
        )
    }

    protected fun Project.withBitbucketCloudProperty(
        repository: String = "my-repository"
    ): BitbucketCloudConfiguration {
        val config = bitbucketCloudTestConfigMock()
        bitbucketCloudConfigurationService.newConfiguration(config)
        setBitbucketCloudProperty(
            config,
            repository = repository,
            indexationInterval = 30,
            issueServiceConfigurationIdentifier = "jira//my-jira",
        )
        return config
    }

}