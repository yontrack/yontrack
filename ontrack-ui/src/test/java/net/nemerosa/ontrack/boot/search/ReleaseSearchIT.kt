package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.extension.general.ReleaseSearchExtension
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.SearchQueryRequest
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Search documents for the release property of the builds, on Postgres.
 */
class ReleaseSearchIT : AbstractSearchTestSupport() {

    /**
     * Random name, which no other one is similar to, even by trigram
     */
    private fun token() = "r" + UUID.randomUUID().toString().replace("-", "").take(15)

    private fun search(query: String) =
        searchService.search(
            SearchQueryRequest(query = query, types = listOf(ReleaseSearchExtension.SEARCH_RESULT_TYPE), size = 50)
        )

    @Test
    fun `A release is searchable as soon as it is set, with what its result renders`() {
        val release = token()
        val branch = project(token()).branch(token())
        val build = doCreateBuild(branch, NameDescription.nd(token(), "Some description"))
        build.release(release)
        val results = asUser { search(release).items }
        assertEquals(1, results.size)
        results.first().apply {
            assertEquals(release, title)
            assertEquals(ReleaseSearchExtension.SEARCH_RESULT_TYPE, type.id)
            assertEquals(release, data?.get("release"))
            @Suppress("UNCHECKED_CAST")
            val buildData = data?.get("build") as Map<String, *>
            assertEquals(build.id(), buildData["id"])
            assertEquals(build.name, buildData["name"])
            assertEquals("Some description", buildData["description"])
            @Suppress("UNCHECKED_CAST")
            val branchData = buildData["branch"] as Map<String, *>
            assertEquals(branch.id(), branchData["id"])
            assertEquals(branch.name, branchData["name"])
            @Suppress("UNCHECKED_CAST")
            val projectData = branchData["project"] as Map<String, *>
            assertEquals(branch.project.id(), projectData["id"])
            assertEquals(branch.project.name, projectData["name"])
        }
    }

    @Test
    fun `Exact release first, then prefix, whatever their recency`() {
        val value = token()
        val branch = project(token()).branch(token())
        val start = Time.now().minusDays(1)
        doCreateBuild(branch, NameDescription.nd("1", ""), Signature.of(start, "test")).release(value)
        doCreateBuild(branch, NameDescription.nd("2", ""), Signature.of(start.plusHours(1), "test")).release("$value-2")
        assertEquals(listOf(value, "$value-2"), asUser { search(value).items.map { it.title } })
        // The exact value first, the similar one after it
        assertEquals(listOf("$value-2", value), asUser { search("$value-2").items.map { it.title } })
    }

    @Test
    fun `A release is not searchable by the name of its build`() {
        val name = token()
        val build = project(token()).branch(token()).build(name)
        build.release(token())
        assertEquals(0, asUser { search(name).total })
    }

    @Test
    fun `A changed release is searchable by its new value only`() {
        val value1 = token()
        val value2 = token()
        val build = project(token()).branch(token()).build(token())
        build.release(value1)
        assertEquals(listOf(value1), asUser { search(value1).items.map { it.title } })
        build.release(value2)
        assertEquals(0, asUser { search(value1).total })
        assertEquals(listOf(value2), asUser { search(value2).items.map { it.title } })
    }

    @Test
    fun `A removed release is not searchable any longer`() {
        val value = token()
        val build = project(token()).branch(token()).build(token())
        build.release(value)
        assertEquals(1, asUser { search(value).total })
        asAdmin { propertyService.deleteProperty(build, ReleasePropertyType::class.java) }
        assertEquals(0, asUser { search(value).total })
    }

    @Test
    fun `The release of a renamed build renders its new name`() {
        val value = token()
        val newName = token()
        val build = project(token()).branch(token()).build(token())
        build.release(value)
        asAdmin { structureService.saveBuild(build.withName(newName)) }
        val result = asUser { search(value).items }.single()
        @Suppress("UNCHECKED_CAST")
        assertEquals(newName, (result.data?.get("build") as Map<String, *>)["name"])
    }

    @Test
    fun `The release of a deleted build is not searchable any longer`() {
        val value = token()
        val build = project(token()).branch(token()).build(token())
        build.release(value)
        assertEquals(1, asUser { search(value).total })
        asAdmin { structureService.deleteBuild(build.id) }
        assertEquals(0, asUser { search(value).total })
    }

    @Test
    fun `The releases of a deleted branch are not searchable any longer`() {
        val value = token()
        val build = project(token()).branch(token()).build(token())
        build.release(value)
        assertEquals(1, asUser { search(value).total })
        asAdmin { structureService.deleteBranch(build.branch.id) }
        assertEquals(0, asUser { search(value).total })
    }

    @Test
    fun `Search releases and filter on access rights with correct totals`() {
        val prefix = token()
        val builds = (0..3).map {
            project(token()).branch(token()).build(token()).apply { release("$prefix-$it") }
        }
        withNoGrantViewToAll {
            builds[0].project.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                val results = search(prefix)
                assertEquals(1, results.total)
                assertEquals(listOf("$prefix-0"), results.items.map { it.title })
            }
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(4, search(prefix).total)
            }
        }
    }

    /**
     * The rebuild runs in its own transactions: the builds must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Rebuilding the release documents`() {
        val prefix = token()
        val project = project(token()) {
            val branch = branch(token())
            branch.build(token()).release("$prefix-1")
            branch.build(token()).release("$prefix-2")
            branch.build(token())
        }
        try {
            searchService.reindex(ReleaseSearchExtension.SEARCH_RESULT_TYPE)
            assertEquals(
                listOf("$prefix-1", "$prefix-2").sorted(),
                asUser { search(prefix).items.map { it.title }.sorted() }
            )
        } finally {
            asAdmin { structureService.deleteProject(project.id) }
        }
    }

}
