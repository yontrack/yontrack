package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.extension.bitbucket.cloud.AbstractBitbucketCloudTestSupport
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BitbucketCloudProjectConfigurationPropertyMutationProviderIT : AbstractBitbucketCloudTestSupport() {

    @Test
    fun `Setting a Bitbucket Cloud configuration on a project identified by ID without any issue service`() {
        asAdmin {
            project {
                val cfg = bitbucketCloudTestConfigMock()
                withDisabledConfigurationTest {
                    bitbucketCloudConfigurationService.newConfiguration(cfg)
                }
                run("""
                    mutation {
                        setProjectBitbucketCloudConfigurationPropertyById(input: {
                            id: $id,
                            configuration: "${cfg.name}",
                            workspace: "nemerosa",
                            repository: "test"
                        }) {
                            project {
                                id
                            }
                            errors {
                                message
                            }
                        }
                    }
                """).let { data ->
                    val node = assertNoUserError(data, "setProjectBitbucketCloudConfigurationPropertyById")
                    assertEquals(id(), node.path("project").path("id").asInt())
                    assertNotNull(getProperty(this, BitbucketCloudProjectConfigurationPropertyType::class.java)) { property ->
                        assertEquals(cfg.name, property.configuration.name)
                        assertEquals("nemerosa", property.workspace)
                        assertEquals("test", property.repository)
                        assertEquals(0, property.indexationInterval)
                        assertEquals(null, property.issueServiceConfigurationIdentifier)
                    }
                }
            }
        }
    }

    @Test
    fun `Setting a Bitbucket Cloud configuration on a project identified by ID with indexation`() {
        asAdmin {
            project {
                val cfg = bitbucketCloudTestConfigMock()
                withDisabledConfigurationTest {
                    bitbucketCloudConfigurationService.newConfiguration(cfg)
                }
                run("""
                    mutation {
                        setProjectBitbucketCloudConfigurationPropertyById(input: {
                            id: $id,
                            configuration: "${cfg.name}",
                            workspace: "nemerosa",
                            repository: "test",
                            indexationInterval: 30
                        }) {
                            project {
                                id
                            }
                            errors {
                                message
                            }
                        }
                    }
                """).let { data ->
                    val node = assertNoUserError(data, "setProjectBitbucketCloudConfigurationPropertyById")
                    assertEquals(id(), node.path("project").path("id").asInt())
                    assertNotNull(getProperty(this, BitbucketCloudProjectConfigurationPropertyType::class.java)) { property ->
                        assertEquals(cfg.name, property.configuration.name)
                        assertEquals("nemerosa", property.workspace)
                        assertEquals("test", property.repository)
                        assertEquals(30, property.indexationInterval)
                        assertEquals(null, property.issueServiceConfigurationIdentifier)
                    }
                }
            }
        }
    }

    @Test
    fun `Setting a Bitbucket Cloud configuration on a project identified by ID with an issue service`() {
        asAdmin {
            project {
                val cfg = bitbucketCloudTestConfigMock()
                withDisabledConfigurationTest {
                    bitbucketCloudConfigurationService.newConfiguration(cfg)
                }
                run("""
                    mutation {
                        setProjectBitbucketCloudConfigurationPropertyById(input: {
                            id: $id,
                            configuration: "${cfg.name}",
                            workspace: "nemerosa",
                            repository: "test",
                            issueServiceConfigurationIdentifier: "jira//my-jira"
                        }) {
                            project {
                                id
                            }
                            errors {
                                message
                            }
                        }
                    }
                """).let { data ->
                    val node = assertNoUserError(data, "setProjectBitbucketCloudConfigurationPropertyById")
                    assertEquals(id(), node.path("project").path("id").asInt())
                    assertNotNull(getProperty(this, BitbucketCloudProjectConfigurationPropertyType::class.java)) { property ->
                        assertEquals(cfg.name, property.configuration.name)
                        assertEquals("nemerosa", property.workspace)
                        assertEquals("test", property.repository)
                        assertEquals(0, property.indexationInterval)
                        assertEquals("jira//my-jira", property.issueServiceConfigurationIdentifier)
                    }
                }
            }
        }
    }

}