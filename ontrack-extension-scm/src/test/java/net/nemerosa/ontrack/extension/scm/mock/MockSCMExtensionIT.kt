package net.nemerosa.ontrack.extension.scm.mock

import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Testing the mocking itself...
 */
class MockSCMExtensionIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var scmDetector: SCMDetector

    @Test
    fun `Commits across branches`() {
        asAdmin {
            mockSCMTester.withMockSCMRepository {
                project {
                    val build0 = branch<Build> {
                        configureMockSCMBranch("release/1.26")
                        val build0 = build("1.26.0") {
                            repositoryIssue("ISS-20", "Last issue before the change log")
                            withRepositoryCommit("ISS-20 Last commit before the change log")
                        }
                        build("1.26.1") {
                            repositoryIssue("ISS-21", "Some new feature")
                            withRepositoryCommit("ISS-21 Some commits for a feature", property = false)
                            withRepositoryCommit("ISS-21 Some fixes for a feature")
                        }
                        build0
                    }
                    branch {
                        configureMockSCMBranch("release/1.27")
                        build("1.27.0") {
                            repositoryIssue("ISS-10", "Old ticket")
                            withRepositoryCommit("ISS-10 Fixing some bugs", property = false)
                            withRepositoryCommit("ISS-10 Implementation of additional fixes")
                        }
                    }
                    val build1 = branch<Build> {
                        configureMockSCMBranch("release/1.28")
                        build("1.28.0") {
                            repositoryIssue("ISS-22", "Some fixes are needed")
                            withRepositoryCommit("ISS-22 Fixing some bugs")
                        }
                        build("1.28.1") {
                            repositoryIssue("ISS-23", "Some nicer UI")
                            withRepositoryCommit("ISS-23 Fixing some CSS")
                        }
                    }

                    val scm = scmDetector.getSCM(this) ?: fail("Cannot find the SCM for the project")
                    assertTrue(scm is SCMChangeLogEnabled, "SCM is enabled for change logs")

                    val commit0 = scm.getBuildCommit(build0) ?: fail("Cannot find build commit")
                    val commit1 = scm.getBuildCommit(build1) ?: fail("Cannot find build commit")

                    val commits = runBlocking {
                        scm.getCommits(commit0, commit1)
                    }

                    assertEquals(
                        listOf(
                            "ISS-23 Fixing some CSS",
                            "ISS-22 Fixing some bugs",
                            "ISS-10 Implementation of additional fixes",
                            "ISS-10 Fixing some bugs",
                            "ISS-21 Some fixes for a feature",
                            "ISS-21 Some commits for a feature",
                        ),
                        commits.map { it.message }
                    )
                }
            }
        }
    }

    @Test
    fun `Commits for one branch`() {
        asAdmin {
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch("release/1.26")
                        val build0 = build("1.26.0") {
                            repositoryIssue("ISS-20", "Last issue before the change log")
                            withRepositoryCommit("ISS-20 Last commit before the change log")
                        }
                        build("1.26.1") {
                            repositoryIssue("ISS-21", "Some new feature")
                            withRepositoryCommit("ISS-21 Some commits for a feature", property = false)
                            withRepositoryCommit("ISS-21 Some fixes for a feature")
                        }
                        build("1.26.2") {
                            repositoryIssue("ISS-22", "Some fixes are needed")
                            withRepositoryCommit("ISS-22 Fixing some bugs")
                        }
                        val build1 = build("1.26.3") {
                            repositoryIssue("ISS-23", "Some nicer UI")
                            withRepositoryCommit("ISS-23 Fixing some CSS")
                        }


                        val scm = scmDetector.getSCM(project) ?: fail("Cannot find the SCM for the project")
                        assertTrue(scm is SCMChangeLogEnabled, "SCM is enabled for change logs")

                        val commit0 = scm.getBuildCommit(build0) ?: fail("Cannot find build commit")
                        val commit1 = scm.getBuildCommit(build1) ?: fail("Cannot find build commit")

                        val commits = runBlocking {
                            scm.getCommits(commit0, commit1)
                        }

                        assertEquals(
                            listOf(
                                "ISS-23 Fixing some CSS",
                                "ISS-22 Fixing some bugs",
                                "ISS-21 Some fixes for a feature",
                                "ISS-21 Some commits for a feature",
                            ),
                            commits.map { it.message }
                        )
                    }
                }
            }
        }
    }

    /**
     * Without the `mock.git` properties (#2172), the mock SCM has no clone access, as before.
     */
    @Test
    fun `No clone access without a Git server`() {
        asAdmin {
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch("main")
                    }
                    val scm = scmDetector.getSCM(this) ?: fail("Cannot find the SCM for the project")
                    assertNull(scm.getCloneAccess(), "No clone access by default")
                }
            }
        }
    }

}
