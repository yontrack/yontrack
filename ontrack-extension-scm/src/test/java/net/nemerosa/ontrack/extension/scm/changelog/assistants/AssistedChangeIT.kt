package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.scm.mock.MockSCMBuildCommitProperty
import net.nemerosa.ontrack.extension.scm.mock.MockSCMBuildCommitPropertyType
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.templating.TemplatingService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The assisted change is computed in the background, after the commit of the transaction which
 * triggers it: the data of the tests must be committed, and is removed at the end.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AsAdminTest
class AssistedChangeIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var assistedChangeService: AssistedChangeService

    @Autowired
    private lateinit var assistedChangeEventListener: AssistedChangeEventListener

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Autowired
    private lateinit var templatingService: TemplatingService

    @Autowired
    private lateinit var eventTemplatingService: EventTemplatingService

    @Test
    fun `Commit set after the creation of the build gives a computed value with the counts`() {
        withAssistedBranch { _, from, to ->
            val value = assistedChange(to)
            assertEquals(
                AssistedChangeProperty(
                    basis = AssistedChangeBasis.COMPUTED,
                    unknownReason = null,
                    assistants = listOf("Claude Code", "Codex"),
                    assistedCommits = 2,
                    totalCommits = 3,
                    sessionLinks = listOf("https://claude.ai/code/session_123"),
                    previousBuildId = from.id(),
                ),
                value
            )
        }
    }

    @Test
    fun `A change without assistants is computed as not assisted, and no event is posted`() {
        withBranch { _, branchBuild ->
            val from = branchBuild { withRepositoryCommit("Before") }
            val to = branchBuild {
                withRepositoryCommit("Some feature", property = false)
                withRepositoryCommit("Some fix")
            }
            awaitComputations()
            val value = assistedChange(to)
            assertEquals(
                AssistedChangeProperty(
                    basis = AssistedChangeBasis.COMPUTED,
                    unknownReason = null,
                    assistants = emptyList(),
                    assistedCommits = 0,
                    totalCommits = 2,
                    sessionLinks = emptyList(),
                    previousBuildId = from.id(),
                ),
                value
            )
            assertEquals(emptyList(), buildAssistedEvents(to))
        }
    }

    @Test
    fun `The build_assisted event is posted once`() {
        withAssistedBranch { _, _, to ->
            val events = buildAssistedEvents(to)
            assertEquals(1, events.size)
            val event = events.single()
            assertEquals("Claude Code, Codex", event.getValue(EventFactory.BUILD_ASSISTED_ASSISTANTS))
            assertEquals("2", event.getValue(EventFactory.BUILD_ASSISTED_ASSISTED_COMMITS))
            assertEquals("3", event.getValue(EventFactory.BUILD_ASSISTED_TOTAL_COMMITS))
            assertEquals(
                "https://claude.ai/code/session_123",
                event.getValue(EventFactory.BUILD_ASSISTED_SESSION_LINKS)
            )
            assertEquals(
                "Build ${to.name} is assisted by Claude Code, Codex (2 of 3 commits).",
                asAdmin {
                    eventTemplatingService.renderEvent(event, renderer = PlainEventRenderer.INSTANCE)
                }
            )

            // Recomputing gives the same value, and no new event
            val before = assistedChange(to)
            asAdmin { assistedChangeService.computeAssistedChange(to) }
            assertEquals(before, assistedChange(to))

            // A change of value posts no new event either
            asAdmin {
                propertyService.editProperty(
                    to,
                    AssistedChangePropertyType::class.java,
                    before!!.copy(assistants = listOf("Devin"), sessionLinks = emptyList()),
                )
            }
            awaitComputations()
            assertEquals(1, buildAssistedEvents(to).size)
        }
    }

    @Test
    fun `A value set by the CI is kept`() {
        withBranch { _, branchBuild ->
            branchBuild { withRepositoryCommit("Before") }
            val ciValue = AssistedChangeProperty(
                basis = AssistedChangeBasis.SET_BY_CI,
                assistants = listOf("Some agent"),
                assistedCommits = 1,
                totalCommits = 1,
            )
            val to = branchBuild {
                asGlobalRole(Roles.GLOBAL_AUTOMATION) {
                    propertyService.editProperty(this, AssistedChangePropertyType::class.java, ciValue)
                }
                withRepositoryCommit(
                    """
                        Some fix

                        Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                    """.trimIndent()
                )
            }
            awaitComputations()
            assertEquals(ciValue, assistedChange(to))
            // The event is posted for a value set by the CI
            val event = buildAssistedEvents(to).single()
            assertEquals("Some agent", event.getValue(EventFactory.BUILD_ASSISTED_ASSISTANTS))
            assertEquals("", event.getValue(EventFactory.BUILD_ASSISTED_SESSION_LINKS))
        }
    }

    @Test
    fun `The first build on a branch is unknown`() {
        withBranch { _, branchBuild ->
            val first = branchBuild {
                withRepositoryCommit(
                    """
                        Some fix

                        Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                    """.trimIndent()
                )
            }
            awaitComputations()
            assertEquals(
                AssistedChangeProperty.unknown("no previous build with a commit"),
                assistedChange(first)
            )
            assertEquals(emptyList(), buildAssistedEvents(first))
        }
    }

    @Test
    fun `Previous builds without a commit are skipped`() {
        withBranch { _, branchBuild ->
            val from = branchBuild { withRepositoryCommit("Before") }
            branchBuild {}
            val to = branchBuild { withRepositoryCommit("Some feature") }
            awaitComputations()
            assertEquals(from.id(), assistedChange(to)?.previousBuildId)
        }
    }

    @Test
    fun `No property for a build without a commit`() {
        withBranch { _, branchBuild ->
            branchBuild { withRepositoryCommit("Before") }
            val to = branchBuild {}
            awaitComputations()
            assertNull(assistedChange(to))
        }
    }

    @Test
    fun `A build with a commit in a project without SCM is unknown`() {
        val project = project()
        try {
            val build = asAdmin {
                project.branch<Build> {
                    build {
                        propertyService.editProperty(
                            this,
                            MockSCMBuildCommitPropertyType::class.java,
                            MockSCMBuildCommitProperty("some-commit"),
                        )
                    }
                }
            }
            awaitComputations()
            assertEquals(AssistedChangeProperty.unknown("no SCM"), assistedChange(build))
        } finally {
            deleteProject(project)
        }
    }

    @Test
    fun `No property for a build without a commit in a project without SCM`() {
        val project = project()
        try {
            val build = asAdmin { project.branch<Build> { build() } }
            awaitComputations()
            assertNull(assistedChange(build))
        } finally {
            deleteProject(project)
        }
    }

    @Test
    fun `An SCM error gives an unknown value, fixed on the next trigger`() {
        withBranch { repository, branchBuild ->
            val from = branchBuild { withRepositoryCommit("Before") }
            lateinit var commit: String
            val to = mockSCMTester.withCommitsInterceptor({ name ->
                if (name == repository) error("Repository unreachable")
            }) {
                branchBuild {
                    commit = withRepositoryCommit(
                        """
                            Some fix

                            Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                        """.trimIndent()
                    )
                }.also { awaitComputations() }
            }
            assertEquals(
                AssistedChangeProperty.unknown("SCM error: Repository unreachable", from.id()),
                assistedChange(to)
            )
            assertEquals(emptyList(), buildAssistedEvents(to))

            // Next trigger: the commit is set again
            asAdmin {
                propertyService.editProperty(
                    to,
                    MockSCMBuildCommitPropertyType::class.java,
                    MockSCMBuildCommitProperty(commit),
                )
            }
            awaitComputations()
            val value = assistedChange(to)
            assertEquals(AssistedChangeBasis.COMPUTED, value?.basis)
            assertEquals(listOf("Claude Code"), value?.assistants)
            assertEquals(1, buildAssistedEvents(to).size)
        }
    }

    @Test
    fun `The creation of a build does not wait on the SCM`() {
        withBranch { repository, branchBuild ->
            branchBuild { withRepositoryCommit("Before") }
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val to = mockSCMTester.withCommitsInterceptor({ name ->
                if (name == repository) {
                    started.countDown()
                    release.await(30, TimeUnit.SECONDS)
                }
            }) {
                try {
                    val build = branchBuild { withRepositoryCommit("Some feature") }
                    // The build and its commit are there, while the SCM is still being read
                    assertTrue(started.await(30, TimeUnit.SECONDS), "The SCM is read in the background")
                    assertNull(assistedChange(build), "Not computed yet")
                    build
                } finally {
                    release.countDown()
                }
            }
            awaitComputations()
            assertEquals(AssistedChangeBasis.COMPUTED, assistedChange(to)?.basis)
        }
    }

    @Test
    fun `Templating of the assisted status and of the assistants`() {
        withAssistedBranch { _, from, to ->
            assertEquals("true / Claude Code, Codex", render(to))
            // Before the change log: unknown
            assertEquals("unknown / ", render(from))
        }
        withBranch { _, branchBuild ->
            branchBuild { withRepositoryCommit("Before") }
            val to = branchBuild { withRepositoryCommit("Some feature") }
            val none = branchBuild {}
            awaitComputations()
            assertEquals("false / ", render(to))
            assertEquals("unknown / ", render(none))
        }
    }

    private fun render(build: Build): String =
        asAdmin {
            templatingService.render(
                template = "\${build.assisted} / \${build.assistants}",
                context = mapOf("build" to build),
                renderer = PlainEventRenderer.INSTANCE,
            )
        }

    private fun assistedChange(build: Build): AssistedChangeProperty? =
        asAdmin { assistedChangeService.getAssistedChange(build) }

    private fun buildAssistedEvents(build: Build): List<Event> =
        asAdmin {
            eventQueryService.getEvents(ProjectEntityType.BUILD, build.id, EventFactory.BUILD_ASSISTED, 0, 10)
        }

    private fun awaitComputations() {
        assistedChangeEventListener.awaitCompletion()
    }

    private fun deleteProject(project: Project) {
        awaitComputations()
        asAdmin { structureService.deleteProject(project.id) }
    }

    /**
     * Branch configured for the mock SCM, where builds are created one after the other.
     *
     * @param code Gets the name of the repository and a function creating a build on the branch
     */
    private fun withBranch(
        code: MockSCMTester.MockSCMRepositoryContext.(
            repository: String,
            branchBuild: (init: Build.() -> Unit) -> Build,
        ) -> Unit,
    ) {
        withCleanSettings<AgentMarkersSettings> {
            mockSCMTester.withMockSCMRepository {
                val project = project()
                try {
                    val branch = asAdmin {
                        project.branch<Branch> {
                            configureMockSCMBranch()
                            this
                        }
                    }
                    this.code(repositoryName) { init ->
                        asAdmin { branch.build(init = init) }
                    }
                } finally {
                    deleteProject(project)
                }
            }
        }
    }

    /**
     * Change log of three commits: a human one, one co-authored by Claude Code with a session link, and
     * one co-authored by Codex. The commit of the last build is set after its creation.
     */
    private fun withAssistedBranch(code: (repository: String, from: Build, to: Build) -> Unit) {
        withBranch { repository, branchBuild ->
            val from = branchBuild { withRepositoryCommit("Before the change log") }
            val to = branchBuild {
                withRepositoryCommit("Some feature", property = false)
                withRepositoryCommit(
                    """
                        Some fix

                        Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                        Claude-Session: https://claude.ai/code/session_123
                    """.trimIndent(),
                    property = false,
                )
                withRepositoryCommit(
                    """
                        Some other fix

                        Co-authored-by: Codex <codex@openai.com>
                    """.trimIndent()
                )
            }
            awaitComputations()
            code(repository, from, to)
        }
    }
}
