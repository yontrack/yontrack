package net.nemerosa.ontrack.extension.github.scm

import net.nemerosa.ontrack.extension.github.AbstractGitHubTestSupport
import net.nemerosa.ontrack.extension.github.TestOnGitHub
import net.nemerosa.ontrack.extension.github.githubTestEnv
import net.nemerosa.ontrack.extension.scm.service.SCMExtension
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import kotlin.test.assertEquals

/**
 * The branches of a commit, read from the GitHub API.
 */
@TestOnGitHub
class GitHubSCMBranchesForCommitIT : AbstractGitHubTestSupport() {

    @Autowired
    @Qualifier("gitHubSCMExtension")
    private lateinit var scmExtension: SCMExtension

    @Test
    fun `Branches containing a commit, among the branches of the project`() {
        asAdmin {
            project {
                gitHubRealConfig()
                val scm = scmExtension.getSCM(this)
                    ?: error("No SCM available for the project")

                // A branch with a commit of its own, on top of the base branch
                val baseBranch = githubTestEnv.branch
                val featureBranch = uid("feature-")
                scm.createBranch(baseBranch, featureBranch)
                try {
                    val content = scm.download(featureBranch, githubTestEnv.readme, true)
                        ?: error("Cannot download ${githubTestEnv.readme} from $featureBranch")
                    val marker = uid("branches-for-commit-")
                    scm.upload(
                        featureBranch,
                        "",
                        githubTestEnv.readme,
                        (String(content) + "\n<!-- $marker -->").toByteArray(),
                        "Test branches for commit $marker",
                    )

                    val baseCommit = scm.getBranchLastCommit(baseBranch)
                        ?: error("No last commit for $baseBranch")
                    val featureCommit = scm.getBranchLastCommit(featureBranch)
                        ?: error("No last commit for $featureBranch")

                    // Both branches known to Yontrack
                    branch { gitRealConfig(baseBranch) }
                    branch { gitRealConfig(featureBranch) }

                    // The base commit is in both branches
                    assertEquals(
                        listOf(baseBranch, featureBranch).sorted(),
                        scm.getBranchesForCommit(this, baseCommit)
                    )
                    // The feature commit is only in the feature branch
                    assertEquals(
                        listOf(featureBranch),
                        scm.getBranchesForCommit(this, featureCommit)
                    )
                } finally {
                    scm.deleteBranch(featureBranch)
                }
            }
        }
    }

}
