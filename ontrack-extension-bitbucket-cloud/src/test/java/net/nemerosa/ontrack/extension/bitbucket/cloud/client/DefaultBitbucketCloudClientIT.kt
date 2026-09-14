package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import net.nemerosa.ontrack.extension.bitbucket.cloud.TestOnBitbucketCloud
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestEnv
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestOnBitbucketCloud
class DefaultBitbucketCloudClientIT {

    private lateinit var client: BitbucketCloudClient

    @BeforeEach
    fun init() {
        val env = bitbucketCloudTestEnv
        client = DefaultBitbucketCloudClient(
            workspace = env.workspace,
            user = env.bot.email,
            token = env.bot.token,
        )
    }

    @Test
    fun `Getting the list of projects`() {
        val expectedProject = bitbucketCloudTestEnv.project
        val projects = client.projects
        assertNotNull(
            projects.find { it.key == expectedProject },
            "Expecting $expectedProject in the list of projects"
        )
    }

    @Test
    fun `Getting the list of repositories`() {
        val repositories = client.repositories
        val expectedProject = bitbucketCloudTestEnv.project
        val expectedRepository = bitbucketCloudTestEnv.repository
        val repository = repositories.find {
            it.slug == expectedRepository
        }
        assertNotNull(repository, "Expected repository has been found") {
            assertNotNull(client.getRepositoryLastModified(it), "Last modified date is set")
            assertEquals(expectedProject, it.project.key, "Project is read")
        }
    }

}