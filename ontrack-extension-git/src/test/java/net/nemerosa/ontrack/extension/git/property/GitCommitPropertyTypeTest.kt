package net.nemerosa.ontrack.extension.git.property

import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.scm.index.SCMBuildCommitIndexService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GitCommitPropertyTypeTest {

    private lateinit var scmBuildCommitIndexService: SCMBuildCommitIndexService
    private lateinit var propertyType: GitCommitPropertyType

    @BeforeEach
    fun setUp() {
        scmBuildCommitIndexService = mockk(relaxed = true)
        propertyType = GitCommitPropertyType(
            extensionFeature = mockk(relaxed = true),
            scmBuildCommitIndexService = scmBuildCommitIndexService,
        )
    }

    @Test
    fun `Setting the commit of a build indexes it in the SCM build commit index`() {
        val build = mockk<Build>()
        propertyType.onPropertyChanged(build, GitCommitProperty(commit = "abcdef"))
        verify(exactly = 1) { scmBuildCommitIndexService.indexBuildCommit(build, "abcdef") }
        confirmVerified(scmBuildCommitIndexService)
    }

    @Test
    fun `Setting the commit on something else than a build indexes nothing`() {
        propertyType.onPropertyChanged(mockk<Branch>(), GitCommitProperty(commit = "abcdef"))
        confirmVerified(scmBuildCommitIndexService)
    }

}
