package net.nemerosa.ontrack.extension.git.reporting

import net.nemerosa.ontrack.extension.git.AbstractGitTestSupport
import net.nemerosa.ontrack.extension.issues.mock.TestIssue
import net.nemerosa.ontrack.extension.issues.mock.TestIssueServiceExtension
import net.nemerosa.ontrack.extension.issues.mock.TestIssueStatus
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * We want to get the last open issues on a pipeline represented by a branch.
 *
 * Given the following situation:
 *
 * ```
 * | Build | VS0    | VS1                   | VS2                 |
 * |-------|--------|-----------------------|---------------------|
 * | 1.1   | Passed | Passed                | Failed with #4 open |
 * | 1.0   | Failed | Failed with #3 closed | Passed              |
 * ```
 */
class IssueReportingIT : AbstractGitTestSupport() {

    @Autowired
    private lateinit var testIssueServiceExtension: TestIssueServiceExtension

    @Test
    fun `Getting last open issues on a branch`() {
        withTestContext { project ->
            // Running the query to get the opened issues
            val data = run("""{
                projects(id: ${project.id}) {
                    branches(name: "main") {
                        validationStamps {
                            name
                            validationRunsPaginated(size: 20, passed: false) {
                                pageItems {
                                    build {
                                        name
                                    }
                                    validationRunStatuses {
                                        statusID {
                                            id
                                        }
                                        issues(status: "OPEN") {
                                            key
                                            displayKey
                                            summary
                                            url
                                            status {
                                                name
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }""")
            val branch = data["projects"][0]["branches"][0]
            // Check issue #3 is not present
            branch.apply {
                val vs = get("validationStamps").first { it["name"].asText() == "VS1" }
                val run = vs["validationRunsPaginated"]["pageItems"].first { it["build"]["name"].asText() == "1.0" }
                val status = run["validationRunStatuses"].first { it["statusID"]["id"].asText() == "DEFECTIVE" }
                val issues = status["issues"]
                assertEquals(0, issues.size()) // #3 is excluded by the filter
            }
            // Check issue #4 is present
            branch.apply {
                val vs = get("validationStamps").first { it["name"].asText() == "VS2" }
                val run = vs["validationRunsPaginated"]["pageItems"].first { it["build"]["name"].asText() == "1.1" }
                val status = run["validationRunStatuses"].first { it["statusID"]["id"].asText() == "DEFECTIVE" }
                val issues = status["issues"]
                assertEquals(1, issues.size())
                val issue = issues[0]
                assertEquals(4, issue["key"].asInt())
                assertEquals("#4", issue["displayKey"].asText())
                assertEquals("Issue #4", issue["summary"].asText())
                assertEquals("uri:issue/4", issue["url"].asText())
                assertEquals("OPEN", issue["status"]["name"].asText())
            }
        }
    }

    @Test
    fun `Getting last issues using the branch`() {
        withTestContext { project ->
            // Running the query to get the opened issues
            val data = run("""{
                projects(id: ${project.id}) {
                    branches(name: "main") {
                        validationIssues {
                            validationRuns {
                                validationStamp {
                                    name
                                }
                                build {
                                    name
                                }
                            }
                            issue {
                                key
                                displayKey
                                summary
                                url
                                status {
                                    name
                                }
                            }
                        }
                    }
                }
            }""")
            val branch = data["projects"][0]["branches"][0]
            // Check issue #3 is present
            branch.apply {
                val validationIssue = get("validationIssues").first { it["issue"]["key"].asInt() == 3 }
                val validationRuns = validationIssue["validationRuns"]
                assertEquals(1, validationRuns.size())
                val validationRun = validationRuns[0]
                assertEquals("1.0", validationRun["build"]["name"].asText())
                assertEquals("VS1", validationRun["validationStamp"]["name"].asText())
            }
            // Check issue #4 is present
            branch.apply {
                val validationIssue = get("validationIssues").first { it["issue"]["key"].asInt() == 4 }
                val validationRuns = validationIssue["validationRuns"]
                assertEquals(1, validationRuns.size())
                val validationRun = validationRuns[0]
                assertEquals("1.1", validationRun["build"]["name"].asText())
                assertEquals("VS2", validationRun["validationStamp"]["name"].asText())
            }
        }
    }

    @Test
    fun `Getting last issues on a validation stamp using the branch`() {
        withTestContext { project ->
            // Running the query to get the opened issues
            val data = run("""{
                projects(id: ${project.id}) {
                    branches(name: "main") {
                        validationIssues(stamp: "VS2") {
                            validationRuns {
                                validationStamp {
                                    name
                                }
                                build {
                                    name
                                }
                            }
                            issue {
                                key
                                displayKey
                                summary
                                url
                                status {
                                    name
                                }
                            }
                        }
                    }
                }
            }""")
            val branch = data["projects"][0]["branches"][0]
            // Check issue #3 is NOT present
            branch.apply {
                val validationIssue = get("validationIssues").firstOrNull { it.path("issue").path("key").asInt() == 3 }
                assertNull(validationIssue, "Issue 3 has been filtered out")
            }
            // Check issue #4 is present
            branch.apply {
                val validationIssue = get("validationIssues").first { it["issue"]["key"].asInt() == 4 }
                val validationRuns = validationIssue["validationRuns"]
                assertEquals(1, validationRuns.size())
                val validationRun = validationRuns[0]
                assertEquals("1.1", validationRun["build"]["name"].asText())
                assertEquals("VS2", validationRun["validationStamp"]["name"].asText())
            }
        }
    }

    @Test
    fun `Getting last issues on failed validation runs using the branch`() {
        withTestContext { project ->
            // Running the query to get the opened issues
            val data = run("""{
                projects(id: ${project.id}) {
                    branches(name: "main") {
                        validationIssues(passed: false) {
                            validationRuns {
                                validationStamp {
                                    name
                                }
                                build {
                                    name
                                }
                            }
                            issue {
                                key
                                displayKey
                                summary
                                url
                                status {
                                    name
                                }
                            }
                        }
                    }
                }
            }""")
            val branch = data["projects"][0]["branches"][0]
            // Check issue #3 is present
            branch.apply {
                val validationIssue = get("validationIssues").first { it["issue"]["key"].asInt() == 3 }
                val validationRuns = validationIssue["validationRuns"]
                assertEquals(1, validationRuns.size())
                val validationRun = validationRuns[0]
                assertEquals("1.0", validationRun["build"]["name"].asText())
                assertEquals("VS1", validationRun["validationStamp"]["name"].asText())
            }
            // Check issue #4 is present
            branch.apply {
                val validationIssue = get("validationIssues").first { it["issue"]["key"].asInt() == 4 }
                val validationRuns = validationIssue["validationRuns"]
                assertEquals(1, validationRuns.size())
                val validationRun = validationRuns[0]
                assertEquals("1.1", validationRun["build"]["name"].asText())
                assertEquals("VS2", validationRun["validationStamp"]["name"].asText())
            }
        }
    }

    @Test
    fun `Getting last open issues on failed validation runs using the branch`() {
        withTestContext { project ->
            // Running the query to get the opened issues
            val data = run("""{
                projects(id: ${project.id}) {
                    branches(name: "main") {
                        validationIssues(passed: false, status: ["OPEN"]) {
                            validationRuns {
                                validationStamp {
                                    name
                                }
                                build {
                                    name
                                }
                            }
                            issue {
                                key
                                displayKey
                                summary
                                url
                                status {
                                    name
                                }
                            }
                        }
                    }
                }
            }""")
            val branch = data["projects"][0]["branches"][0]
            // Check issue #3 is NOT present
            branch.apply {
                val validationIssue = get("validationIssues").firstOrNull { it["issue"]["key"].asInt() == 3 }
                assertNull(validationIssue, "Issue 3 is closed")
            }
            // Check issue #4 is present
            branch.apply {
                val validationIssue = get("validationIssues").first { it["issue"]["key"].asInt() == 4 }
                val validationRuns = validationIssue["validationRuns"]
                assertEquals(1, validationRuns.size())
                val validationRun = validationRuns[0]
                assertEquals("1.1", validationRun["build"]["name"].asText())
                assertEquals("VS2", validationRun["validationStamp"]["name"].asText())
            }
        }
    }

    @Test
    fun `Getting last issues on a branch`() {
        withTestContext { project ->
            // Running the query to get the opened issues
            val data = run("""{
                projects(id: ${project.id}) {
                    branches(name: "main") {
                        validationStamps {
                            name
                            validationRunsPaginated(size: 20, passed: false) {
                                pageItems {
                                    build {
                                        name
                                    }
                                    validationRunStatuses {
                                        statusID {
                                            id
                                        }
                                        issues {
                                            key
                                            displayKey
                                            summary
                                            url
                                            status {
                                                name
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }""")
            val branch = data["projects"][0]["branches"][0]
            // Check issue #3 is not present
            branch.apply {
                val vs = get("validationStamps").first { it["name"].asText() == "VS1" }
                val run = vs["validationRunsPaginated"]["pageItems"].first { it["build"]["name"].asText() == "1.0" }
                val status = run["validationRunStatuses"].first { it["statusID"]["id"].asText() == "DEFECTIVE" }
                val issues = status["issues"]
                assertEquals(1, issues.size())
                val issue = issues[0]
                assertEquals(3, issue["key"].asInt())
                assertEquals("#3", issue["displayKey"].asText())
                assertEquals("Issue #3", issue["summary"].asText())
                assertEquals("uri:issue/3", issue["url"].asText())
                assertEquals("CLOSED", issue["status"]["name"].asText())
            }
            // Check issue #4 is present
            branch.apply {
                val vs = get("validationStamps").first { it["name"].asText() == "VS2" }
                val run = vs["validationRunsPaginated"]["pageItems"].first { it["build"]["name"].asText() == "1.1" }
                val status = run["validationRunStatuses"].first { it["statusID"]["id"].asText() == "DEFECTIVE" }
                val issues = status["issues"]
                assertEquals(1, issues.size())
                val issue = issues[0]
                assertEquals(4, issue["key"].asInt())
                assertEquals("#4", issue["displayKey"].asText())
                assertEquals("Issue #4", issue["summary"].asText())
                assertEquals("uri:issue/4", issue["url"].asText())
                assertEquals("OPEN", issue["status"]["name"].asText())
            }
        }
    }

    /**
     * "Open issues" are the issues of the runs which are **currently** not passed (#1712):
     *
     * ```
     * | Build | VS0                                              | VS1                                   |
     * |-------|--------------------------------------------------|---------------------------------------|
     * | 1.0   | Failed -> Investigating with #5 -> Fixed (passed) | Failed with #6 -> Defective (no issue) |
     * ```
     *
     * #5 is left out because its run is now fixed; #6 is kept although it is only in a past status
     * description of a run which is still not passed.
     */
    @Test
    fun `Getting last issues on runs which are currently not passed using the branch`() {
        createRepo {
            commits(1)
        } and { repo, _ ->
            testIssueServiceExtension.resetIssues()
            testIssueServiceExtension.register(
                    TestIssue(5, TestIssueStatus.OPEN, "bug"),
                    TestIssue(6, TestIssueStatus.OPEN, "bug")
            )
            val project = project {
                gitProject(repo)
                branch("main") {
                    gitBranch {
                        commitAsProperty()
                    }
                    val vs0 = validationStamp("VS0")
                    val vs1 = validationStamp("VS1")
                    build("1.0") {
                        validate(vs0, ValidationRunStatusID.STATUS_FAILED)
                                .validationStatus(ValidationRunStatusID.STATUS_INVESTIGATING, "Issue #5 is being looked at")
                                .validationStatus(ValidationRunStatusID.STATUS_FIXED, "Fixed")
                        validate(vs1, ValidationRunStatusID.STATUS_FAILED, description = "Issue #6 is the cause")
                                .validationStatus(ValidationRunStatusID.STATUS_DEFECTIVE, "Defective")
                    }
                }
            }
            asUserWithView(project) {
                val data = run("""{
                    projects(id: ${project.id}) {
                        branches(name: "main") {
                            validationIssues(passed: false) {
                                validationRuns {
                                    validationStamp {
                                        name
                                    }
                                }
                                issue {
                                    key
                                }
                            }
                        }
                    }
                }""")
                val issues = data["projects"][0]["branches"][0]["validationIssues"]
                assertEquals(listOf(6), issues.map { it["issue"]["key"].asInt() })
                assertEquals(
                        listOf("VS1"),
                        issues[0]["validationRuns"].map { it["validationStamp"]["name"].asText() }
                )
            }
        }
    }

    private fun withTestContext(code: (Project) -> Unit) {
        createRepo {
            commits(1)
        } and { repo, _ ->
            // Registers the issues
            testIssueServiceExtension.resetIssues()
            testIssueServiceExtension.register(
                    TestIssue(3, TestIssueStatus.CLOSED, "bug"),
                    TestIssue(4, TestIssueStatus.OPEN, "bug")
            )
            // Project & branch
            val project = project {
                gitProject(repo)
                branch("main") {
                    gitBranch {
                        commitAsProperty()
                    }
                    // Creates all the validations we want to monitor
                    val stamps = (0..2).map { validationStamp("VS$it") }
                    // Creates a list of builds
                    val builds = (0..1).map { build("1.$it") }
                    // Activates the validations & descriptions
                    builds[0].validate(stamps[0], ValidationRunStatusID.STATUS_FAILED)
                    builds[0].validate(stamps[1], ValidationRunStatusID.STATUS_FAILED)
                            .validationStatus(ValidationRunStatusID.STATUS_DEFECTIVE, "Issue #3 will be closed")
                    builds[0].validate(stamps[2], ValidationRunStatusID.STATUS_PASSED)
                    builds[1].validate(stamps[0], ValidationRunStatusID.STATUS_PASSED)
                    builds[1].validate(stamps[1], ValidationRunStatusID.STATUS_PASSED)
                    builds[1].validate(stamps[2], ValidationRunStatusID.STATUS_FAILED)
                            .validationStatus(ValidationRunStatusID.STATUS_DEFECTIVE, "Issue #4 will remain opened")
                }
            }
            // Running the test
            asUserWithView(project) {
                code(project)
            }
        }
    }

}