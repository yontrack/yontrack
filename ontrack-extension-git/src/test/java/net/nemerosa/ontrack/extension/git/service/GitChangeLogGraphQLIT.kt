package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.extension.git.AbstractGitTestSupport
import net.nemerosa.ontrack.extension.issues.mock.TestIssue
import net.nemerosa.ontrack.extension.issues.mock.TestIssueServiceExtension
import net.nemerosa.ontrack.extension.issues.mock.TestIssueStatus
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.test.assertJsonNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals


/**
 * Integration tests for Git support.
 */
@Disabled("flaky")
class GitChangeLogGraphQLIT : AbstractGitTestSupport() {

    @Autowired
    private lateinit var testIssueServiceExtension: TestIssueServiceExtension

    @BeforeEach
    fun before() {
        testIssueServiceExtension.resetIssues()
    }

    private fun doTest(testCode: (Branch) -> Unit) {
        createRepo {
            commits(10, pauses = true)
        } and { repo, commits ->
            project {
                gitProject(repo, sync = true)
                branch {
                    gitBranch("main") {
                        commitAsProperty()
                    }

                    // Creates builds for some commits
                    listOf(2, 5, 7, 8).forEach { no ->
                        build(no.toString()) {
                            gitCommitProperty(commits.getValue(no))
                        }
                    }

                    // Test
                    asUserWithView(this).execute {
                        testCode(this)
                    }
                }
            }
        }
    }

    @Test
    fun `Change log based on Git property`() {
        doTest { branch ->
            // Getting the change log between build 5 and 7
            val data = run(
                """{
                branches(id: ${branch.id}) {
                    gitChangeLog(from: "5", to: "7") {
                        commits {
                            commit {
                                shortMessage
                            }
                        }
                    }
                }
            }"""
            )
            val messages = data["branches"][0]["gitChangeLog"]["commits"].values().map {
                it["commit"]["shortMessage"].asText()
            }
            assertEquals(
                listOf("Commit 7", "Commit 6"),
                messages
            )
        }
    }

    @Test
    fun `Change log of issues based on Git property`() {
        this.createRepo {
            mapOf(
                1 to commit(1, "#1 Issue 1"),
                2 to commit(2, "#2 Issue 2"),
                3 to commit(3, "#1 Also issue 1"),
                4 to commit(4, "No issue"),
                5 to commit(5, "#1 #2 Both issues")
            )
        } and { repo, commits ->
            project {
                gitProject(repo, sync = true)
                branch {
                    gitBranch("main") {
                        commitAsProperty()
                    }

                    // Creates builds for each commits
                    (1..5).forEach { no ->
                        build(no.toString()) {
                            gitCommitProperty(commits.getValue(no))
                        }
                    }

                    // Test
                    asUserWithView(this).execute {
                        // Getting the change log between build 1 and 5
                        run(
                            """{
                            branches(id: $id) {
                                gitChangeLog(from: "1", to: "5") {
                                    issues {
                                        issueServiceConfiguration {
                                            id
                                            name
                                            serviceId
                                        }
                                        list {
                                            issue {
                                                key
                                                displayKey
                                                summary
                                                url
                                                status {
                                                    name
                                                }
                                                updateTime
                                            }
                                        }
                                    }
                                }
                            }
                        }"""
                        ).let { data ->
                            val issues = data.path("branches").path(0).path("gitChangeLog").path("issues")
                            assertJsonNotNull(issues) {
                                val issueServiceConfiguration = path("issueServiceConfiguration")
                                assertJsonNotNull(issueServiceConfiguration) {
                                    assertEquals("mock//default", issueServiceConfiguration.getTextField("id"))
                                    assertEquals("default (Mock issue)", issueServiceConfiguration.getTextField("name"))
                                    assertEquals("mock", issueServiceConfiguration.getTextField("serviceId"))
                                }
                                val list = path("list")
                                assertJsonNotNull(list) {
                                    assertEquals(2, list.size())
                                    list.forEach { item ->
                                        val issue = item.path("issue")
                                        assertJsonNotNull(issue) {
                                            val key = issue.getRequiredTextField("key").toInt()
                                            assertEquals("#$key", issue.getTextField("displayKey"))
                                            assertEquals("Issue #$key", issue.getTextField("summary"))
                                            assertEquals("uri:issue/$key", issue.getTextField("url"))
                                            assertEquals("OPEN", issue.path("status").getTextField("name"))
                                            assertJsonNotNull(issue.path("updateTime"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun doTestChangeLogExport(
        expected: String,
        format: String? = null,
        grouping: String? = null,
        altGroup: String? = null,
        exclude: String? = null,
    ) {
        testIssueServiceExtension.register(
            TestIssue(1, TestIssueStatus.CLOSED, "bug"),
            TestIssue(2, TestIssueStatus.CLOSED, "feature"),
            TestIssue(3, TestIssueStatus.OPEN, "feature"),
        )

        val input = if (format != null || grouping != null || altGroup != null || exclude != null) {
            val args = mutableListOf<String>()
            format?.let {
                args += """format: "$it""""
            }
            grouping?.let {
                args += """grouping: "$it""""
            }
            altGroup?.let {
                args += """altGroup: "$it""""
            }
            exclude?.let {
                args += """exclude: "$it""""
            }
            "(request: {${args.joinToString(",")}})"
        } else {
            ""
        }

        this.createRepo {
            mapOf(
                1 to commit(1, "#1 Issue 1"),
                2 to commit(2, "#2 Issue 2"),
                3 to commit(3, "#1 Also issue 1"),
                4 to commit(4, "No issue"),
                5 to commit(5, "#1 #2 Both issues"),
                6 to commit(6, "#3 And now a third issue"),
            )
        } and { repo, commits ->
            project {
                gitProject(repo, sync = true)
                branch {
                    gitBranch("main") {
                        commitAsProperty()
                    }

                    // Creates builds for each commits
                    (1..6).forEach { no ->
                        build(no.toString()) {
                            gitCommitProperty(commits.getValue(no))
                        }
                    }

                    // Test
                    asUserWithView(this).execute {
                        // Getting the change log between build 1 and 5
                        run(
                            """{
                            branches(id: $id) {
                                gitChangeLog(from: "1", to: "6") {
                                    export$input
                                }
                            }
                        }"""
                        ).let { data ->
                            val export = data.path("branches").path(0).path("gitChangeLog").path("export").asText()
                            assertEquals(
                                expected.trim(),
                                export.trim()
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Change log export of issues with default format`() {
        doTestChangeLogExport(
            """
            |* #1 Issue #1
            |* #2 Issue #2
            |* #3 Issue #3
        """.trimMargin()
        )
    }

    @Test
    fun `Change log export of issues with markdown format`() {
        doTestChangeLogExport(
            """
            |* [#1](uri:issue/1) Issue #1
            |* [#2](uri:issue/2) Issue #2
            |* [#3](uri:issue/3) Issue #3
        """.trimMargin(),
            format = "markdown"
        )
    }

    @Test
    fun `Change log export of issues with grouping`() {
        doTestChangeLogExport(
            """
            |Bugs
            |
            |* #1 Issue #1
            |
            |Features
            |
            |* #2 Issue #2
            |* #3 Issue #3
        """.trimMargin(),
            grouping = "Bugs=bug|Features=feature"
        )
    }

    @Test
    fun `Change log export of issues with grouping and default alt group`() {
        doTestChangeLogExport(
            """            |
            |Features
            |
            |* #2 Issue #2
            |* #3 Issue #3
            |
            |Other
            |
            |* #1 Issue #1
        """.trimMargin(),
            grouping = "Features=feature"
        )
    }

    @Test
    fun `Change log export of issues with grouping and specific alt group`() {
        doTestChangeLogExport(
            """            |
            |Features
            |
            |* #2 Issue #2
            |* #3 Issue #3
            |
            |Misc
            |
            |* #1 Issue #1
        """.trimMargin(),
            grouping = "Features=feature", altGroup = "Misc"
        )
    }

    @Test
    fun `Change log export of issues without grouping and with exclusions`() {
        doTestChangeLogExport(
            """            |
            |* #2 Issue #2
            |* #3 Issue #3
        """.trimMargin(),
            exclude = "bug"
        )
    }

    @Test
    fun `Change log based on Git property using root query`() {
        doTest { branch ->
            // Gets the build ids
            val build5 = structureService.findBuildByName(
                branch.project.name,
                branch.name,
                "5"
            ).orElse(null).id()
            val build7 = structureService.findBuildByName(
                branch.project.name,
                branch.name,
                "7"
            ).orElse(null).id()
            // Getting the change log between build 5 and 7
            val data = run(
                """{
                gitChangeLog(from: $build5, to: $build7) {
                    commits {
                        commit {
                            shortMessage
                        }
                    }
                }
            }"""
            )
            val messages = data["gitChangeLog"]["commits"].values().map {
                it["commit"]["shortMessage"].asText()
            }
            assertEquals(
                listOf("Commit 7", "Commit 6"),
                messages
            )
        }
    }

}
