package net.nemerosa.ontrack.extension.github.scm

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.git.GitExtensionFeature
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.github.GitHubExtensionFeature
import net.nemerosa.ontrack.extension.github.catalog.GitHubSCMCatalogSettings
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClient
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationProperty
import net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.stale.StaleExtensionFeature
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * [SCM.getBranchesForCommit] for GitHub goes through the GitHub API, not through a local clone.
 */
class GitHubSCMExtensionBranchesForCommitTest {

    private val repository = "nemerosa/ontrack"
    private val commit = "215b979b3e1f4c484e3e3d1af4330baf8e95f4c4"

    private lateinit var client: OntrackGitHubClient
    private lateinit var propertyService: PropertyService
    private lateinit var structureService: StructureService
    private lateinit var project: Project
    private lateinit var scm: SCM

    @BeforeEach
    fun init() {
        client = mockk()
        propertyService = mockk()
        structureService = mockk()

        val clientFactory = mockk<OntrackGitHubClientFactory>()
        every { clientFactory.create(any()) } returns client

        val cachedSettingsService = mockk<CachedSettingsService>()
        every {
            cachedSettingsService.getCachedSettings(GitHubSCMCatalogSettings::class.java)
        } returns GitHubSCMCatalogSettings()

        project = Project.of(NameDescription.nd("P", "")).withId(ID.of(1))

        val projectProperty = mockk<Property<GitHubProjectConfigurationProperty>>()
        every { projectProperty.value } returns GitHubProjectConfigurationProperty(
            configuration = GitHubEngineConfiguration("GitHub", null),
            repository = repository,
            indexationInterval = 0,
            issueServiceConfigurationIdentifier = null,
        )
        every {
            propertyService.getProperty(project, GitHubProjectConfigurationPropertyType::class.java)
        } returns projectProperty

        val extension = GitHubSCMExtension(
            gitHubExtensionFeature = GitHubExtensionFeature(
                GitExtensionFeature(SCMExtensionFeature(), StaleExtensionFeature())
            ),
            propertyService = propertyService,
            clientFactory = clientFactory,
            cachedSettingsService = cachedSettingsService,
            gitHubConfigurationService = mockk(),
            issueServiceRegistry = mockk(),
            issueServiceExtension = mockk(),
            structureService = structureService,
            gitRepositoryClientFactory = mockk(),
            gitHubConfigurator = mockk(),
            gitConfigService = mockk(),
        )
        scm = extension.getSCM(project) ?: error("GitHub SCM expected")
    }

    private fun branches(vararg scmBranches: String?) {
        val branches = scmBranches.mapIndexed { index, scmBranch ->
            val branch = Branch.of(project, NameDescription.nd("B$index", "")).withId(ID.of(10 + index))
            every {
                propertyService.getPropertyValue(branch, GitBranchConfigurationPropertyType::class.java)
            } returns scmBranch?.let {
                GitBranchConfigurationProperty(
                    branch = it,
                    buildCommitLink = null,
                    override = false,
                    buildTagInterval = 0,
                )
            }
            branch
        }
        every { structureService.getBranchesForProject(project.id) } returns branches
    }

    @Test
    fun `Branches containing the commit are asked to the GitHub API`() {
        branches("main", "release/1.0", "feature/other")
        every { client.isCommitInBranch(repository, commit, "main") } returns true
        every { client.isCommitInBranch(repository, commit, "release/1.0") } returns true
        every { client.isCommitInBranch(repository, commit, "feature/other") } returns false

        assertEquals(
            listOf("main", "release/1.0"),
            scm.getBranchesForCommit(project, commit)
        )
    }

    @Test
    fun `Branches are returned sorted, each SCM branch asked once`() {
        branches("release/1.0", "main", "main")
        every { client.isCommitInBranch(repository, commit, any()) } returns true

        assertEquals(
            listOf("main", "release/1.0"),
            scm.getBranchesForCommit(project, commit)
        )
        verify(exactly = 1) { client.isCommitInBranch(repository, commit, "main") }
    }

    @Test
    fun `Branches not associated with a Git branch are ignored`() {
        branches("main", null)
        every { client.isCommitInBranch(repository, commit, "main") } returns true

        assertEquals(
            listOf("main"),
            scm.getBranchesForCommit(project, commit)
        )
        verify(exactly = 1) { client.isCommitInBranch(any(), any(), any()) }
    }

    @Test
    fun `No branch at all`() {
        branches()

        assertEquals(
            emptyList(),
            scm.getBranchesForCommit(project, commit)
        )
        verify(exactly = 0) { client.isCommitInBranch(any(), any(), any()) }
    }

}
