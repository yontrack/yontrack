package net.nemerosa.ontrack.extension.scm.graphql

import net.nemerosa.ontrack.extension.general.ReleaseProperty
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.execution.ErrorType
import kotlin.test.assertEquals

/**
 * Testing the `scmChangeLogByName` query, which resolves the boundaries of a change log
 * using build names or display names instead of build IDs.
 */
class GQLRootQuerySCMChangeLogByNameIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Test
    fun `Change log using build names`() {
        asAdmin {
            withChangeLog { from, to ->
                run(
                    """
                        {
                            scmChangeLogByName(
                                project: "${from.project.name}",
                                from: "${from.name}",
                                to: "${to.name}"
                            ) {
                                from { id }
                                to { id }
                                commits {
                                    commit { message }
                                }
                            }
                        }
                    """
                ) { data ->
                    val changeLog = data.path("scmChangeLogByName")
                    assertEquals(from.id(), changeLog.path("from").path("id").asInt())
                    assertEquals(to.id(), changeLog.path("to").path("id").asInt())
                    assertEquals(
                        listOf(
                            "ISS-23 Fixing some CSS",
                            "ISS-22 Fixing some bugs",
                            "ISS-21 Some fixes for a feature",
                            "ISS-21 Some commits for a feature",
                        ),
                        changeLog.path("commits").map { it.path("commit").path("message").asText() }
                    )
                }
            }
        }
    }

    @Test
    fun `Change log using display names`() {
        asAdmin {
            withChangeLog { from, to ->
                setDisplayName(from, "v1")
                setDisplayName(to, "v4")
                run(
                    """
                        {
                            scmChangeLogByName(
                                project: "${from.project.name}",
                                from: "v1",
                                to: "v4"
                            ) {
                                from { id }
                                to { id }
                            }
                        }
                    """
                ) { data ->
                    val changeLog = data.path("scmChangeLogByName")
                    assertEquals(from.id(), changeLog.path("from").path("id").asInt())
                    assertEquals(to.id(), changeLog.path("to").path("id").asInt())
                }
            }
        }
    }

    @Test
    fun `Change log using build names inside a branch`() {
        asAdmin {
            withChangeLog { from, to ->
                // Decoy builds, created last, having the same names on another branch of the same project.
                // Without the branch qualifiers, the project-wide search would return these ones because
                // they are the most recent.
                from.branch.project.branch<Unit> {
                    build(from.name)
                    build(to.name)
                }
                run(
                    """
                        {
                            scmChangeLogByName(
                                project: "${from.project.name}",
                                from: "${from.name}",
                                fromBranch: "${from.branch.name}",
                                to: "${to.name}",
                                toBranch: "${to.branch.name}"
                            ) {
                                from { id }
                                to { id }
                            }
                        }
                    """
                ) { data ->
                    val changeLog = data.path("scmChangeLogByName")
                    assertEquals(from.id(), changeLog.path("from").path("id").asInt())
                    assertEquals(to.id(), changeLog.path("to").path("id").asInt())
                }
            }
        }
    }

    /**
     * Documented behaviour: when a branch is given, the build name is looked for inside this branch,
     * but the display name fallback remains scoped to the whole project. This mirrors the resolution
     * done for the notifications and is documented as such.
     */
    @Test
    fun `Display name is looked for in the whole project even when a branch is given`() {
        asAdmin {
            mockSCMTester.withMockSCMRepository {
                project {
                    val main = branch("main") {
                        configureMockSCMBranch()
                    }
                    val other = branch("other") {
                        configureMockSCMBranch()
                    }

                    val from = main.build("1.0")
                    from.withRepositoryCommit("First commit")

                    // The display name is set on a build of ANOTHER branch
                    val decoy = other.build("decoy")
                    decoy.withRepositoryCommit("Decoy commit")
                    setDisplayName(decoy, "v1")

                    val to = main.build("2.0")
                    to.withRepositoryCommit("Last commit")

                    run(
                        """
                            {
                                scmChangeLogByName(
                                    project: "$name",
                                    from: "v1",
                                    fromBranch: "${main.name}",
                                    to: "${to.name}",
                                    toBranch: "${main.name}"
                                ) {
                                    from { id }
                                }
                            }
                        """
                    ) { data ->
                        val changeLog = data.path("scmChangeLogByName")
                        assertEquals(
                            decoy.id(),
                            changeLog.path("from").path("id").asInt(),
                            "The display name has been resolved outside of the given branch"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Change log boundaries are swapped when given in the reverse order`() {
        asAdmin {
            withChangeLog { from, to ->
                run(
                    """
                        {
                            scmChangeLogByName(
                                project: "${from.project.name}",
                                from: "${to.name}",
                                to: "${from.name}"
                            ) {
                                from { id }
                                to { id }
                            }
                        }
                    """
                ) { data ->
                    val changeLog = data.path("scmChangeLogByName")
                    assertEquals(from.id(), changeLog.path("from").path("id").asInt())
                    assertEquals(to.id(), changeLog.path("to").path("id").asInt())
                }
            }
        }
    }

    @Test
    fun `Deep change log using build names is identical to the one using build IDs`() {
        asAdmin {
            withChangeLog { depFrom, depTo ->
                // Parent project, linking to the project having the SCM
                project {
                    branch {
                        val from = build {
                            linkTo(depFrom)
                        }
                        val to = build {
                            linkTo(depTo)
                        }

                        val fields = """
                            from { id }
                            to { id }
                            commits {
                                commit { message }
                            }
                        """

                        val byId = run(
                            """
                                {
                                    scmChangeLog(
                                        from: ${from.id},
                                        to: ${to.id},
                                        projects: ["${depFrom.project.name}"]
                                    ) { $fields }
                                }
                            """
                        ).path("scmChangeLog")

                        val byName = run(
                            """
                                {
                                    scmChangeLogByName(
                                        project: "${from.project.name}",
                                        from: "${from.name}",
                                        to: "${to.name}",
                                        projects: ["${depFrom.project.name}"]
                                    ) { $fields }
                                }
                            """
                        ).path("scmChangeLogByName")

                        assertEquals(byId, byName)
                    }
                }
            }
        }
    }

    @Test
    fun `Project not found`() {
        asAdmin {
            runWithError(
                """
                    {
                        scmChangeLogByName(project: "no-such-project", from: "1", to: "2") {
                            from { id }
                        }
                    }
                """,
                errorClassification = ErrorType.NOT_FOUND,
                errorMessage = "Project name not found: no-such-project",
            )
        }
    }

    @Test
    fun `Project not visible is reported as not found`() {
        val project = asAdmin { doCreateProject() }
        withNoGrantViewToAll {
            asUser().execute {
                runWithError(
                    """
                        {
                            scmChangeLogByName(project: "${project.name}", from: "1", to: "2") {
                                from { id }
                            }
                        }
                    """,
                    errorClassification = ErrorType.NOT_FOUND,
                    errorMessage = "Project name not found: ${project.name}",
                )
            }
        }
    }

    @Test
    fun `Build not found`() {
        asAdmin {
            withChangeLog { from, _ ->
                runWithError(
                    """
                        {
                            scmChangeLogByName(
                                project: "${from.project.name}",
                                from: "${from.name}",
                                to: "no-such-build"
                            ) {
                                from { id }
                            }
                        }
                    """,
                    errorClassification = ErrorType.NOT_FOUND,
                    errorMessage = "Build not found: ${from.project.name}/no-such-build",
                )
            }
        }
    }

    @Test
    fun `Build not found in branch`() {
        asAdmin {
            withChangeLog { from, to ->
                runWithError(
                    """
                        {
                            scmChangeLogByName(
                                project: "${from.project.name}",
                                from: "${from.name}",
                                to: "no-such-build",
                                toBranch: "${to.branch.name}"
                            ) {
                                from { id }
                            }
                        }
                    """,
                    errorClassification = ErrorType.NOT_FOUND,
                    errorMessage = "Build not found: ${from.project.name}/${to.branch.name}/no-such-build",
                )
            }
        }
    }

    @Test
    fun `Branch not found`() {
        asAdmin {
            withChangeLog { from, to ->
                runWithError(
                    """
                        {
                            scmChangeLogByName(
                                project: "${from.project.name}",
                                from: "${from.name}",
                                to: "${to.name}",
                                toBranch: "no-such-branch"
                            ) {
                                from { id }
                            }
                        }
                    """,
                    errorClassification = ErrorType.NOT_FOUND,
                    errorMessage = "Branch not found: ${from.project.name}/no-such-branch",
                )
            }
        }
    }

    private fun setDisplayName(build: Build, name: String) {
        propertyService.editProperty(
            build,
            ReleasePropertyType::class.java,
            ReleaseProperty(name)
        )
    }

    /**
     * Creates a project having a mock SCM, and a set of builds with commits and issues,
     * and calls the [code] with the two boundaries of a change log.
     */
    private fun withChangeLog(code: (from: Build, to: Build) -> Unit) {
        mockSCMTester.withMockSCMRepository {
            project {
                branch {
                    configureMockSCMBranch()

                    build("1.01")

                    val from = build("1.02")
                    repositoryIssue("ISS-20", "Last issue before the change log")
                    from.withRepositoryCommit("ISS-20 Last commit before the change log")

                    val second = build("1.03")
                    repositoryIssue("ISS-21", "Some new feature")
                    second.withRepositoryCommit("ISS-21 Some commits for a feature", property = false)
                    second.withRepositoryCommit("ISS-21 Some fixes for a feature")

                    val third = build("1.04")
                    repositoryIssue("ISS-22", "Some fixes are needed")
                    third.withRepositoryCommit("ISS-22 Fixing some bugs")

                    val to = build("1.05")
                    repositoryIssue("ISS-23", "Some nicer UI")
                    to.withRepositoryCommit("ISS-23 Fixing some CSS")

                    code(from, to)
                }
            }
        }
    }

}
