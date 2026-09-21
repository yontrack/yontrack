package net.nemerosa.ontrack.extension.github.ingestion

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.service.GitService
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClient
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.ingestion.processing.config.INGESTION_CONFIG_FILE_PATH
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationProperty
import net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationPropertyType
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class DefaultFileLoaderServiceTest {

    @Test
    fun `Project not configured returns null`() {
        test(
            projectConfigured = false,
            expectedContent = false,
        )
    }

    @Test
    fun `Branch not configured returns null`() {
        test(
            branchConfigured = false,
            expectedContent = false,
        )
    }

    @Test
    fun `Path not found returns null`() {
        test(
            pathFound = false,
            expectedContent = false,
        )
    }

    @Test
    fun `Loading the file`() {
        test()
    }

    private fun test(
        projectConfigured: Boolean = true,
        branchConfigured: Boolean = true,
        pathFound: Boolean = true,
        expectedContent: Boolean = true,
    ) {
        val project = Project.of(NameDescription.nd("test", ""))
        val branch = Branch.of(project, NameDescription.nd("main", ""))

        val gitService = mockk<GitService>()
        every { gitService.getBranchAsPullRequest(any()) } returns null

        val gitHubClientFactory = mockk<OntrackGitHubClientFactory>()
        val gitHubClient = mockk<OntrackGitHubClient>()
        every { gitHubClientFactory.create(any()) } returns gitHubClient
        if (pathFound) {
            val path = "/ingestion/config.yml"
            every { gitHubClient.getFileContent("org/repo", "release/1.0", INGESTION_CONFIG_FILE_PATH) } returns
                    TestUtils.resourceBytes(path)
        } else {
            every { gitHubClient.getFileContent("org/repo", "release/1.0", INGESTION_CONFIG_FILE_PATH) } returns null
        }

        val propertyService = mockk<PropertyService>()
        if (projectConfigured) {
            every {
                propertyService.getPropertyValue(project, GitHubProjectConfigurationPropertyType::class.java)
            } returns GitHubProjectConfigurationProperty(
                GitHubEngineConfiguration("test", null, oauth2Token = "some-token"),
                "org/repo",
                30,
                issueServiceConfigurationIdentifier = null,
            )
        } else {
            every {
                propertyService.getPropertyValue(project, GitHubProjectConfigurationPropertyType::class.java)
            } returns null
        }
        if (branchConfigured) {
            every { propertyService.getPropertyValue(branch, GitBranchConfigurationPropertyType::class.java) } returns
                    GitBranchConfigurationProperty(
                        branch = "release/1.0",
                        buildCommitLink = null,
                        override = false,
                        buildTagInterval = 0,
                    )
        } else {
            every {
                propertyService.getPropertyValue(branch, GitBranchConfigurationPropertyType::class.java)
            } returns null
        }

        val fileLoaderService = DefaultFileLoaderService(
            gitHubClientFactory = gitHubClientFactory,
            propertyService = propertyService,
            gitService = gitService,
        )
        val config = fileLoaderService.loadFile(branch, INGESTION_CONFIG_FILE_PATH)
        if (expectedContent) {
            assertNotNull(config, "Configuration was loaded")
        } else {
            assertNull(config, "Configuration could not be found")
        }
    }

}