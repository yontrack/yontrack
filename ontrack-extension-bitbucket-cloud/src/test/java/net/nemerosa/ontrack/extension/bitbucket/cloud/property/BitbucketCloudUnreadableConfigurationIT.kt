package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.extension.bitbucket.cloud.AbstractBitbucketCloudTestSupport
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.git.model.gitRepository
import net.nemerosa.ontrack.extension.git.service.GitService
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A Bitbucket Cloud configuration stored in its V5 shape, without `authType`, cannot be read anymore.
 * A project pointing to it must not break its page, nor the Git jobs of the other projects.
 */
class BitbucketCloudUnreadableConfigurationIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var gitService: GitService

    @Test
    fun `Properties of a project pointing to an unreadable configuration`() {
        withDisabledConfigurationTest {
            asAdmin {
                project {
                    withUnreadableBitbucketCloudConfiguration()
                    run(
                        """
                            {
                                project(id: $id) {
                                    properties(hasValue: true) {
                                        type {
                                            typeName
                                        }
                                        value
                                        error
                                        editable
                                    }
                                }
                            }
                        """
                    ) { data ->
                        val property = data.path("project").path("properties").find {
                            it.path("type").path("typeName").asText() == BitbucketCloudProjectConfigurationPropertyType::class.java.name
                        }
                        assertNotNull(property, "Unreadable property is listed") {
                            assertTrue(it.path("value").isNull, "No value")
                            val error = it.path("error").asText()
                            assertTrue(error.isNotBlank(), "Error is set")
                            assertFalse(error.contains(V5_PASSWORD), "Error does not contain the stored configuration")
                            assertTrue(it.path("editable").asBoolean(), "Property is editable")
                        }
                    }
                    assertTransactionNotRollbackOnly()
                }
            }
        }
    }

    @Test
    fun `Properties of a project pointing to an unreadable configuration do not fail the transaction`() {
        withDisabledConfigurationTest {
            asAdmin {
                project {
                    withUnreadableBitbucketCloudConfiguration()
                    val property = propertyService.getProperties(this).first {
                        it.type is BitbucketCloudProjectConfigurationPropertyType
                    }
                    assertTrue(property.hasError, "Property has an error")
                    assertTransactionNotRollbackOnly()
                }
            }
        }
    }

    @Test
    fun `Git jobs of healthy projects are registered when a project points to an unreadable configuration`() {
        withDisabledConfigurationTest {
            asAdmin {
                project {
                    withUnreadableBitbucketCloudConfiguration()
                }
                val healthy = project {
                    withBitbucketCloudProperty()
                }
                val healthyRepositoryId = gitService.getProjectConfiguration(healthy)?.gitRepository?.id
                assertNotNull(healthyRepositoryId, "Healthy project is configured for Git")

                val jobs = (gitService as JobOrchestratorSupplier).jobRegistrations

                assertTrue(
                    jobs.any { it.job.key.id == healthyRepositoryId },
                    "Indexation job of the healthy project is registered"
                )
                assertTransactionNotRollbackOnly()
            }
        }
    }

    /**
     * The test runs in a transaction which is rolled back anyway: checks that the unreadable configuration has not
     * marked it as rollback-only, which would fail the transaction of an actual request.
     */
    private fun assertTransactionNotRollbackOnly() {
        val holder = TransactionSynchronizationManager.getResource(dataSource) as ConnectionHolder
        assertFalse(holder.isRollbackOnly, "Transaction is not marked as rollback-only")
    }

    /**
     * Points the project to a Bitbucket Cloud configuration, then rewrites the configuration in its V5 shape.
     */
    private fun Project.withUnreadableBitbucketCloudConfiguration() {
        val config = bitbucketCloudTestConfigMock()
        bitbucketCloudConfigurationService.newConfiguration(config)
        setBitbucketCloudProperty(config, repository = "my-repository", indexationInterval = 30)
        namedParameterJdbcTemplate.update(
            "UPDATE CONFIGURATIONS SET CONTENT = CAST(:content AS JSONB) WHERE TYPE = :type AND NAME = :name",
            mapOf(
                "content" to """{"name":"${config.name}","user":"v5-user","password":"$V5_PASSWORD","workspace":"v5-workspace"}""",
                "type" to BitbucketCloudConfiguration::class.java.name,
                "name" to config.name,
            )
        )
    }

    companion object {
        private const val V5_PASSWORD = "v5-app-password"
    }
}
