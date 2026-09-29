package net.nemerosa.ontrack.service.templating

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.events.HtmlNotificationEventRenderer
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.templating.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TemplatingServiceImplTest {

    private lateinit var templatingService: TemplatingService
    private lateinit var ontrackConfigProperties: OntrackConfigProperties
    private lateinit var entityDisplayNameService: EntityDisplayNameService

    private val testTemplatingContextHandler = TestTemplatingContextHandler()

    @BeforeEach
    fun init() {

        ontrackConfigProperties = OntrackConfigProperties()
        ontrackConfigProperties.templating.errors = OntrackConfigProperties.TemplatingErrors.MESSAGE

        val scmBranchSource = object : AbstractTemplatingSource(
            field = "scmBranch",
            type = ProjectEntityType.BRANCH,
        ) {
            override fun render(
                entity: ProjectEntity,
                config: TemplatingSourceConfig,
                renderer: EventRenderer
            ): String =
                "feature/${entity.defaultDisplayName}"
        }

        val listProjectSource = object : AbstractTemplatingSource(
            field = "list",
            type = ProjectEntityType.PROJECT
        ) {
            override fun render(
                entity: ProjectEntity,
                config: TemplatingSourceConfig,
                renderer: EventRenderer
            ): String {
                val list = config.getExpandedList("projects")
                return renderer.renderList(list)
            }
        }

        val repositorySource = object : AbstractTemplatingSource(
            field = "repository",
            type = ProjectEntityType.PROJECT
        ) {
            override fun render(
                entity: ProjectEntity,
                config: TemplatingSourceConfig,
                renderer: EventRenderer
            ): String {
                val name = config.getRequiredString("name")
                val organization = config.getRequiredString("org")
                return "https://github.com/$organization/$name"
            }
        }

        val templatingSources = listOf(
            scmBranchSource,
            listProjectSource,
            repositorySource,
        )

        val uppercaseTemplatingFilter = object : TemplatingFilter {
            override val id: String = "uppercase"
            override fun apply(text: String, renderer: EventRenderer): String = text.uppercase()
        }

        val templatingFilters = listOf<TemplatingFilter>(
            uppercaseTemplatingFilter,
        )

        val templatingFunctions = listOf<TemplatingFunction>(
            LinkTemplatingFunction(),
        )

        val templatingContextHandlers = listOf<TemplatingContextHandler<*>>(
            testTemplatingContextHandler,
        )

        entityDisplayNameService = mockk()
        every { entityDisplayNameService.getEntityDisplayName(any()) } answers {
            val entity = it.invocation.args.first() as ProjectEntity
            entity.defaultDisplayName
        }

        templatingService = TemplatingServiceImpl(
            templatingSources = templatingSources,
            templatingFilters = templatingFilters,
            templatingFunctions = templatingFunctions,
            templatingContextHandlers = templatingContextHandlers,
            ontrackConfigProperties = ontrackConfigProperties,
            entityDisplayNameService = entityDisplayNameService,
        )
    }

    @Test
    fun `Using list of strings as config parameters for a source`() {
        val project = ProjectFixtures.testProject()
        val text = templatingService.render(
            template = $$"""
                List:
                
                ${project.list?projects=one,two,three}
            """.trimIndent(),
            context = mapOf(
                "project" to project,
            ),
            renderer = PlainEventRenderer()
        )
        assertEquals(
            """
                List:
                
                * one
                * two
                * three
            """.trimIndent(),
            text
        )
    }

    @Test
    fun `Rendering entity names`() {
        val branch = BranchFixtures.testBranch()
        val text = templatingService.render(
            template = """
                Branch ${'$'}{branch} has been created
                in project ${'$'}{project}.
            """.trimIndent(),
            context = mapOf(
                "project" to branch.project,
                "branch" to branch,
            ),
            renderer = PlainEventRenderer()
        )
        assertEquals(
            """
                Branch ${branch.name} has been created
                in project ${branch.project.name}.
            """.trimIndent(),
            text
        )
    }

    @Test
    fun `Rendering links for HTML`() {
        val text = templatingService.render(
            template = """
                Link to ${'$'}{#.link?href=LINK_HREF&text=LINK_TEXT}.
            """.trimIndent(),
            context = mapOf(
                "LINK_TEXT" to "Ontrack",
                "LINK_HREF" to "https://nemerosa.github.io/ontrack",
            ),
            renderer = HtmlNotificationEventRenderer(ontrackConfigProperties)
        )
        assertEquals(
            """
                Link to <a href="https://nemerosa.github.io/ontrack">Ontrack</a>.
            """.trimIndent(),
            text
        )
    }

    @Test
    fun `Rendering links for text`() {
        val text = templatingService.render(
            template = """
                Link to ${'$'}{#.link?href=LINK_HREF&text=LINK_TEXT}.
            """.trimIndent(),
            context = mapOf(
                "LINK_TEXT" to "Ontrack",
                "LINK_HREF" to "https://nemerosa.github.io/ontrack",
            ),
            renderer = PlainEventRenderer.INSTANCE
        )
        assertEquals(
            """
                Link to Ontrack.
            """.trimIndent(),
            text
        )
    }

    @Test
    fun `Rendering values`() {
        val text = templatingService.render(
            template = """
                Project ${'$'}{project} has been deleted.
            """.trimIndent(),
            context = mapOf(
                "project" to "PRJ",
            ),
            renderer = PlainEventRenderer()
        )
        assertEquals(
            """
                Project PRJ has been deleted.
            """.trimIndent(),
            text
        )
    }

    @Test
    fun `Rendering with a mix of entities and values`() {
        val project = ProjectFixtures.testProject(name = "ontrack")
        val branch = BranchFixtures.testBranch(project = project, name = "develop")
        val text = templatingService.render(
            template = """
                The ${'$'}{project|uppercase} project has a branch
                which points to a "${'$'}{branch.scmBranch}" SCM branch.
                
                This causes the CVE ${'$'}{cve}.
                
                This would need some action on your side
                to fix the repository at ${'$'}{project.repository?org=nemerosa&name=ontrack}.
            """.trimIndent(),
            context = mapOf(
                "project" to branch.project,
                "branch" to branch,
                "cve" to "CVE-123",
            ),
            renderer = PlainEventRenderer()
        )
        assertEquals(
            """
                The ONTRACK project has a branch
                which points to a "feature/develop" SCM branch.
                
                This causes the CVE CVE-123.
                
                This would need some action on your side
                to fix the repository at https://github.com/nemerosa/ontrack.
            """.trimIndent(),
            text
        )
    }

    @Test
    fun `Templating context`() {
        val project = ProjectFixtures.testProject(name = "ontrack")
        val text = templatingService.render(
            template = """
                Project ${'$'}{project} is being deployed by ${'$'}{deployment.url}
            """.trimIndent(),
            context = mapOf(
                "project" to project,
                "deployment" to testTemplatingContextHandler.createTemplatingContextData(
                    TestTemplatingContextData(id = "123")
                )
            ),
            renderer = PlainEventRenderer.INSTANCE,
        )
        assertEquals(
            "Project ontrack is being deployed by mock://123",
            text
        )
    }

    @Test
    fun `Escaping expressions`() {
        val project = ProjectFixtures.testProject(name = "ontrack")
        val text = templatingService.render(
            template = """
                Parameter ${'$'}${'$'}{PARAM} is not expanded but ${'$'}{project} is.
            """.trimIndent(),
            context = mapOf(
                "project" to project,
            ),
            renderer = PlainEventRenderer.INSTANCE,
        )
        assertEquals(
            "Parameter ${'$'}{PARAM} is not expanded but ontrack is.",
            text
        )
    }

}