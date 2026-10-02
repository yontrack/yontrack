package net.nemerosa.ontrack.extension.scm.changelog

import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.fail

class SCMChangeLogIT : AbstractSCMChangeLogTestSupport() {

    @Autowired
    private lateinit var scmChangeLogService: SCMChangeLogService

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Test
    fun `Getting a change log using the SCM API`() {
        prepareChangeLogTestCase { _, from, to ->
            val changeLog = runBlocking {
                scmChangeLogService.getChangeLog(
                    from = from,
                    to = to,
                )
            } ?: fail("Could not get a change log")

            // Checking the commits
            assertEquals(
                listOf(
                    "ISS-23 Fixing some CSS",
                    "ISS-22 Fixing some bugs",
                    "ISS-21 Some fixes for a feature",
                    "ISS-21 Some commits for a feature",
                ),
                changeLog.commits.map { it.commit.message },
                "Change log commits"
            )

            // Checking the issues
            assertEquals(
                listOf(
                    "ISS-21" to "Some new feature",
                    "ISS-22" to "Some fixes are needed",
                    "ISS-23" to "Some nicer UI",
                ),
                changeLog.issues?.issues?.map { it.displayKey to it.summary },
                "Change log issues"
            )

        }
    }

    @Test
    fun `Issues are extracted from the subject and the trailers of a commit, not from its body`() {
        asAdmin {
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch()
                        val from = build {
                            withRepositoryCommit("ISS-10 Last commit before the change log")
                        }
                        build {
                            repositoryIssue("ISS-11", "Subject issue")
                            repositoryIssue("ISS-12", "Trailer issue")
                            repositoryIssue("ISS-13", "Body-only issue")
                            withRepositoryCommit(
                                """
                                    ISS-11 Some feature

                                    Follow-up of the rework of ISS-13, which is mentioned only
                                    in the body of this commit.

                                    Refs: ISS-12
                                """.trimIndent()
                            )

                            val changeLog = runBlocking {
                                scmChangeLogService.getChangeLog(
                                    from = from,
                                    to = this@build,
                                )
                            } ?: fail("Could not get a change log")

                            assertEquals(
                                listOf("ISS-11", "ISS-12"),
                                changeLog.issues?.issues?.map { it.displayKey },
                                "Only the subject and the trailer issues are in the change log"
                            )
                        }
                    }
                }
            }
        }
    }
}
