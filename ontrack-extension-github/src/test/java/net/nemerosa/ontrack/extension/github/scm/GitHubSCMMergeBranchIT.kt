package net.nemerosa.ontrack.extension.github.scm

import net.nemerosa.ontrack.extension.github.AbstractGitHubTestSupport
import net.nemerosa.ontrack.extension.github.TestOnGitHub
import net.nemerosa.ontrack.extension.github.githubTestEnv
import net.nemerosa.ontrack.extension.scm.service.SCMExtension
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import kotlin.test.assertContains
import kotlin.test.assertTrue

@TestOnGitHub
class GitHubSCMMergeBranchIT : AbstractGitHubTestSupport() {

    @Autowired
    @Qualifier("gitHubSCMExtension")
    private lateinit var scmExtension: SCMExtension

    @Test
    fun `mergeBranch merges a branch into a base branch`() {
        asAdmin {
            project {
                gitHubRealConfig()
                val scm = scmExtension.getSCM(this)
                    ?: error("No SCM available for the project")

                val baseBranch = uid("base-")
                val headBranch = uid("head-")
                scm.createBranch(githubTestEnv.branch, baseBranch)
                scm.createBranch(baseBranch, headBranch)
                try {
                    // Add a commit to headBranch so it diverges from baseBranch
                    val originalContent = scm.download(headBranch, githubTestEnv.readme, true)
                        ?: error("Cannot download ${githubTestEnv.readme} from $headBranch")
                    val marker = uid("merge-test-")
                    val modifiedContent = (String(originalContent) + "\n<!-- $marker -->").toByteArray()
                    val commitMessage = "Test merge commit $marker"
                    scm.upload(headBranch, "", githubTestEnv.readme, modifiedContent, commitMessage)

                    // Merge headBranch into baseBranch
                    val commit = scm.mergeBranch(head = headBranch, base = baseBranch)

                    assertTrue(commit.id.isNotBlank(), "Merge commit SHA must not be blank")
                    assertTrue(commit.shortId.isNotBlank(), "Merge commit short SHA must not be blank")
                    assertTrue(commit.author.isNotBlank(), "Merge commit author must not be blank")
                    assertTrue(commit.message.isNotBlank(), "Merge commit message must not be blank")
                    assertTrue(commit.link.isNotBlank(), "Merge commit link must not be blank")

                    val newContent = scm.download(baseBranch, githubTestEnv.readme, true)
                        ?: error("Cannot download ${githubTestEnv.readme} from $baseBranch")
                    // Checks the content of the file
                    assertContains(String(newContent), marker)
                } finally {
                    scm.deleteBranch(headBranch)
                    scm.deleteBranch(baseBranch)
                }
            }
        }
    }

    @Test
    fun `mergeBranch returns current HEAD when already up-to-date`() {
        asAdmin {
            project {
                gitHubRealConfig()
                val scm = scmExtension.getSCM(this)
                    ?: error("No SCM available for the project")

                // Create a branch from the base with no additional commits
                val testBranch = uid("up-to-date-")
                scm.createBranch(githubTestEnv.branch, testBranch)
                try {
                    // Merging a branch that has no new commits triggers HTTP 204 (already up-to-date)
                    val commit = scm.mergeBranch(head = testBranch, base = githubTestEnv.branch)

                    assertTrue(commit.id.isNotBlank(), "Commit SHA must not be blank")
                    assertTrue(commit.shortId.isNotBlank(), "Commit short SHA must not be blank")
                    assertTrue(commit.author.isNotBlank(), "Commit author must not be blank")
                } finally {
                    scm.deleteBranch(testBranch)
                }
            }
        }
    }

}
