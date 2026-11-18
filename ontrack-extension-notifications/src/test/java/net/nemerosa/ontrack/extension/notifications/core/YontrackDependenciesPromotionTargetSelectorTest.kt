package net.nemerosa.ontrack.extension.notifications.core

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class YontrackDependenciesPromotionTargetSelectorTest {

    private lateinit var structureService: StructureService
    private lateinit var selector: YontrackDependenciesPromotionTargetSelector

    private val source = build(id = 1, project = "A", name = "1.0")

    @BeforeEach
    fun before() {
        structureService = mockk()
        selector = YontrackDependenciesPromotionTargetSelector(structureService)
    }

    @Test
    fun `No filter selects all the links, qualified or not`() {
        val b12 = build(id = 2, project = "B", name = "1.2")
        val b13 = build(id = 3, project = "B", name = "1.3")
        val c20 = build(id = 4, project = "C", name = "2.0")
        links(BuildLink(b12, ""), BuildLink(b13, "moduleX"), BuildLink(c20, ""))

        assertEquals(
            listOf(
                YontrackDependenciesPromotionTarget(b12, listOf("")),
                YontrackDependenciesPromotionTarget(b13, listOf("moduleX")),
                YontrackDependenciesPromotionTarget(c20, listOf("")),
            ),
            selector.selectTargets(source, project = null, qualifier = null),
        )
    }

    @Test
    fun `The project filter must match the whole project name`() {
        val b = build(id = 2, project = "B", name = "1.2")
        val bb = build(id = 3, project = "BB", name = "1.3")
        val c = build(id = 4, project = "C", name = "2.0")
        val d = build(id = 5, project = "D", name = "3.0")
        links(BuildLink(b, ""), BuildLink(bb, ""), BuildLink(c, ""), BuildLink(d, ""))

        assertEquals(
            listOf(b, c),
            selector.selectTargets(source, project = "B|C", qualifier = null).map { it.build },
        )
    }

    @Test
    fun `A blank project filter selects all the projects`() {
        val b = build(id = 2, project = "B", name = "1.2")
        links(BuildLink(b, ""))

        assertEquals(
            listOf(b),
            selector.selectTargets(source, project = "  ", qualifier = null).map { it.build },
        )
    }

    @Test
    fun `The qualifier filter must match the whole qualifier`() {
        val x = build(id = 2, project = "B", name = "1.3")
        val other = build(id = 3, project = "B", name = "1.4")
        val unqualified = build(id = 4, project = "B", name = "1.2")
        links(BuildLink(x, "moduleX"), BuildLink(other, "my-moduleX"), BuildLink(unqualified, ""))

        assertEquals(
            listOf(x),
            selector.selectTargets(source, project = null, qualifier = "module.*").map { it.build },
        )
    }

    @Test
    fun `An empty qualifier filter selects the unqualified links only`() {
        val qualified = build(id = 2, project = "B", name = "1.3")
        val unqualified = build(id = 3, project = "B", name = "1.2")
        links(BuildLink(qualified, "moduleX"), BuildLink(unqualified, ""))

        assertEquals(
            listOf(unqualified),
            selector.selectTargets(source, project = null, qualifier = "").map { it.build },
        )
    }

    @Test
    fun `A build linked under several qualifiers is one target carrying its matching qualifiers`() {
        val b = build(id = 2, project = "B", name = "1.2")
        links(BuildLink(b, "moduleX"), BuildLink(b, "moduleY"), BuildLink(b, "other"))

        assertEquals(
            listOf(YontrackDependenciesPromotionTarget(b, listOf("moduleX", "moduleY"))),
            selector.selectTargets(source, project = null, qualifier = "module.*"),
        )
    }

    @Test
    fun `The build of the event is never a target`() {
        val b = build(id = 2, project = "B", name = "1.2")
        links(BuildLink(source, ""), BuildLink(b, ""))

        assertEquals(
            listOf(b),
            selector.selectTargets(source, project = null, qualifier = null).map { it.build },
        )
    }

    @Test
    fun `All the links are read, beyond the default page size`() {
        val builds = (1..250).map { build(id = 100 + it, project = "B", name = "1.$it") }
        links(*builds.map { BuildLink(it, "") }.toTypedArray())

        assertEquals(
            builds,
            selector.selectTargets(source, project = null, qualifier = null).map { it.build },
        )
    }

    @Test
    fun `No link selects no target`() {
        links()

        assertEquals(
            emptyList(),
            selector.selectTargets(source, project = null, qualifier = null),
        )
    }

    private fun links(vararg links: BuildLink) {
        every {
            structureService.getBuildDependencyLinks(source, any(), any(), 0, any())
        } answers {
            PaginatedList.create(links.toList(), secondArg(), thirdArg())
        }
    }

    private fun build(id: Int, project: String, name: String): Build =
        BuildFixtures.testBuild(
            branch = BranchFixtures.testBranch(project = ProjectFixtures.testProject(name = project)),
            name = name,
        ).withId(ID.of(id))

}
