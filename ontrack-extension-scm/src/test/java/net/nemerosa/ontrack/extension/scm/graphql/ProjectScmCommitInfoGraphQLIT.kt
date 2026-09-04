package net.nemerosa.ontrack.extension.scm.graphql

import net.nemerosa.ontrack.extension.scm.index.SCMBuildCommitIndexService
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class ProjectScmCommitInfoGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var scmBuildCommitIndexService: SCMBuildCommitIndexService

    @Test
    @AsAdminTest
    fun `Getting the basic commit info for a project`() {
        mockSCMTester.withMockSCMRepository {
            project {
                branch {
                    configureMockSCMBranch()

                    build("1.01") {
                        repositoryIssue("ISS-20", "Some issue")
                        val commitId = withRepositoryCommit("ISS-20 Some commit")

                        run(
                            """
                                {
                                    project(id: ${project.id}) {
                                        scmCommitInfo(commitId: "$commitId") {
                                            scmDecoratedCommit {
                                                commit {
                                                    id
                                                    shortId
                                                    message
                                                }
                                                annotatedMessage
                                            }
                                        }
                                    }
                                }
                            """
                        ) { data ->
                            val scmDecoratedCommit = data.path("project")
                                .path("scmCommitInfo")
                                .path("scmDecoratedCommit")
                            val scmCommit = scmDecoratedCommit.path("commit")
                            assertEquals(commitId, scmCommit.path("id").asText())
                            assertEquals("ISS-20 Some commit", scmCommit.path("message").asText())
                            assertEquals(
                                """<a href="mock://Mock issues/issue/ISS-20">ISS-20</a> Some commit""",
                                scmDecoratedCommit.path("annotatedMessage").asText()
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    @AsAdminTest
    fun `The annotated message is reduced to its subject by default`() {
        mockSCMTester.withMockSCMRepository {
            project {
                branch {
                    configureMockSCMBranch()

                    build("1.01") {
                        repositoryIssue("ISS-20", "Some issue")
                        val commitId = withRepositoryCommit(
                            "ISS-20 Some commit\n\nWith a long body explaining at length what the commit does."
                        )

                        run(
                            """
                                {
                                    project(id: ${project.id}) {
                                        scmCommitInfo(commitId: "$commitId") {
                                            scmDecoratedCommit {
                                                annotatedMessage
                                            }
                                        }
                                    }
                                }
                            """
                        ) { data ->
                            assertEquals(
                                """<a href="mock://Mock issues/issue/ISS-20">ISS-20</a> Some commit""",
                                data.path("project")
                                    .path("scmCommitInfo")
                                    .path("scmDecoratedCommit")
                                    .path("annotatedMessage").asText()
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    @AsAdminTest
    fun `The whole annotated message can be asked for with a zero max length`() {
        mockSCMTester.withMockSCMRepository {
            project {
                branch {
                    configureMockSCMBranch()

                    build("1.01") {
                        repositoryIssue("ISS-20", "Some issue")
                        val message =
                            "ISS-20 Some commit\n\nWith a long body explaining at length what the commit does."
                        val commitId = withRepositoryCommit(message)

                        run(
                            """
                                {
                                    project(id: ${project.id}) {
                                        scmCommitInfo(commitId: "$commitId") {
                                            scmDecoratedCommit {
                                                annotatedMessage(maxLength: 0)
                                            }
                                        }
                                    }
                                }
                            """
                        ) { data ->
                            assertEquals(
                                message.replace(
                                    "ISS-20",
                                    """<a href="mock://Mock issues/issue/ISS-20">ISS-20</a>"""
                                ),
                                data.path("project")
                                    .path("scmCommitInfo")
                                    .path("scmDecoratedCommit")
                                    .path("annotatedMessage").asText()
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Getting the branch info info for a commit in a project`() {
        mockSCMTester.withMockSCMRepository {
            project {
                branch {
                    configureMockSCMBranch()

                    val pl = promotionLevel()

                    build("1.01") {
                        repositoryIssue("ISS-20", "Some issue")
                        val commitId = withRepositoryCommit("ISS-20 Some previous commit", property = false)
                        withRepositoryCommit("ISS-20 Some commit associated with the build", property = true)

                        scmBuildCommitIndexService.indexBuildCommits(project)

                        promote(pl)

                        run(
                            """
                                {
                                    project(id: ${project.id}) {
                                        scmCommitInfo(commitId: "$commitId") {
                                            scmDecoratedCommit {
                                                commit {
                                                    id
                                                }
                                            }
                                            branchInfos {
                                                type
                                                branchInfoList {
                                                    branch {
                                                        name
                                                    }
                                                    firstBuild {
                                                        name
                                                    }
                                                    promotions {
                                                        promotionLevel {
                                                            name
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            """
                        ) { data ->
                            val scmCommitInfo = data.path("project").path("scmCommitInfo")

                            val scmCommit = scmCommitInfo.path("scmDecoratedCommit").path("commit")
                            assertEquals(commitId, scmCommit.path("id").asText())

                            val branchInfosList = scmCommitInfo.path("branchInfos")
                            assertEquals(1, branchInfosList.size())
                            val branchInfosListItem = branchInfosList.first()

                            assertEquals("Development", branchInfosListItem.path("type").asText())

                            val branchInfo = branchInfosListItem.path("branchInfoList").first()
                            assertEquals(branch.name, branchInfo.path("branch").path("name").asText())
                            assertEquals("1.01", branchInfo.path("firstBuild").path("name").asText())
                            assertEquals(1, branchInfo.path("promotions").size())
                            assertEquals(
                                pl.name,
                                branchInfo.path("promotions").first().path("promotionLevel").path("name").asText()
                            )
                        }
                    }
                }
            }
        }
    }

}