package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.stereotype.Component
import kotlin.test.fail

@Component
class MockSCMTester(
    private val mockSCMExtension: MockSCMExtension,
    private val propertyService: PropertyService,
) {

    fun registerRepository(repositoryName: String) {
        mockSCMExtension.registerRepository(repositoryName)
    }

    fun deleteRepository(repoName: String) {
        mockSCMExtension.deleteRepository(repoName)
    }

    fun <T> withMockSCMRepository(
        name: String = uid(""),
        code: MockSCMRepositoryContext.() -> T,
    ): T {
        val context = MockSCMRepositoryContext(name)
        return context.code()
    }

    fun withIssuePattern(
        pattern: String,
        code: () -> Unit,
    ) {
        val oldIssueRegex = MockSCMExtension.issueRegex
        MockSCMExtension.issueRegex = pattern.toRegex()
        try {
            code()
        } finally {
            MockSCMExtension.issueRegex = oldIssueRegex
        }
    }

    inner class MockSCMRepositoryContext(
        val repositoryName: String,
    ) {

        init {
            mockSCMExtension.registerRepository(repositoryName)
        }

        fun Project.configureMockSCMProject(
            issueServiceIdentifier: String? = null
        ) {
            propertyService.editProperty(
                this,
                MockSCMProjectPropertyType::class.java,
                MockSCMProjectProperty(
                    name = this@MockSCMRepositoryContext.repositoryName,
                    issueServiceIdentifier = issueServiceIdentifier,
                )
            )
        }

        fun Branch.configureMockSCMBranch(
            scmBranch: String = "main",
            issueServiceIdentifier: String? = null,
        ) {
            project.configureMockSCMProject(
                issueServiceIdentifier = issueServiceIdentifier,
            )
            propertyService.editProperty(
                this,
                MockSCMBranchPropertyType::class.java,
                MockSCMBranchProperty(scmBranch)
            )
        }

        fun repositoryIssue(key: String, message: String, type: String? = null) {
            mockSCMExtension
                .repository(repositoryName)
                .registerIssue(
                    key = key,
                    message = message,
                    types = if (type.isNullOrBlank()) {
                        emptyArray()
                    } else {
                        arrayOf(type)
                    },
                )
        }

        fun repositoryFile(branch: String = "main", path: String, content: String) {
            mockSCMExtension.repository(repositoryName).registerFile(
                scmBranch = branch,
                path = path,
                content = content
            )
        }

        /**
         * Gets the content of a file on a branch, null if not existing.
         */
        fun getRepositoryFile(branch: String = "main", path: String): String? =
            mockSCMExtension.repository(repositoryName).getFile(branch, path)

        /**
         * Gets a branch of the repository, null if not existing.
         *
         * @param namePattern Exact branch name or prefix followed by a `*`
         */
        fun getRepositoryBranch(namePattern: String): MockBranch? =
            mockSCMExtension.repository(repositoryName).getBranch(namePattern)

        fun Build.withRepositoryCommit(message: String, property: Boolean = true): String {
            val branchProperty = propertyService.getPropertyValue(branch, MockSCMBranchPropertyType::class.java)
            return if (branchProperty != null) {
                val id = mockSCMExtension.repository(repositoryName).registerCommit(
                    scmBranch = branchProperty.name,
                    message = message
                )
                if (property) {
                    propertyService.editProperty(
                        this,
                        MockSCMBuildCommitPropertyType::class.java,
                        MockSCMBuildCommitProperty(id)
                    )
                }
                id
            } else {
                fail("Branch not configured for Mock SCM")
            }
        }

    }
}