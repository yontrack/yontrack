package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.boot.BUILD_SEARCH_RESULT_TYPE
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
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
 * Search documents for the builds, on Postgres.
 */
class BuildSearchIT : AbstractSearchTestSupport() {

    /**
     * Random name, which no other one is similar to, even by trigram
     */
    private fun token() = "v" + UUID.randomUUID().toString().replace("-", "").take(15)

    private fun search(query: String) =
        searchService.search(
            SearchQueryRequest(query = query, types = listOf(BUILD_SEARCH_RESULT_TYPE), size = 50)
        )

    @Test
    fun `A created build is searchable in the transaction of its creation, with what its result renders`() {
        val name = token()
        val branch = project(token()).branch(token())
        val build = doCreateBuild(branch, NameDescription.nd(name, "Some description"))
        val results = asUser { search(name).items }
        assertEquals(1, results.size)
        results.first().apply {
            assertEquals(name, title)
            assertEquals("Some description", description)
            assertEquals(BUILD_SEARCH_RESULT_TYPE, type.id)
            assertEquals(name, data?.get("release"), "Name of the build when it has no display name")
            @Suppress("UNCHECKED_CAST")
            val buildData = data?.get("build") as Map<String, *>
            assertEquals(build.id(), buildData["id"])
            assertEquals(name, buildData["name"])
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
    fun `A build is found on its description`() {
        val word = token()
        val branch = project(token()).branch(token())
        val build = doCreateBuild(branch, NameDescription.nd(token(), "The $word build"))
        assertEquals(listOf(build.name), asUser { search(word).items.map { it.title } })
    }

    @Test
    fun `Exact build name first, then prefix, then fuzzy, whatever their recency`() {
        val name = token()
        val branch = project(token()).branch(token())
        val start = Time.now().minusDays(1)
        // From the oldest to the newest, so that the recency cannot explain the ranking
        doCreateBuild(branch, NameDescription.nd(name, ""), Signature.of(start, "test"))
        doCreateBuild(branch, NameDescription.nd("$name-1", ""), Signature.of(start.plusHours(1), "test"))
        doCreateBuild(branch, NameDescription.nd("x$name", ""), Signature.of(start.plusHours(2), "test"))
        val results = asUser { search(name).items }
        assertEquals(listOf(name, "$name-1", "x$name"), results.map { it.title })
    }

    @Test
    fun `The exact name is not case sensitive`() {
        val name = token()
        val branch = project(token()).branch(token())
        val start = Time.now().minusDays(1)
        doCreateBuild(branch, NameDescription.nd(name, ""), Signature.of(start, "test"))
        doCreateBuild(branch, NameDescription.nd("$name-1", ""), Signature.of(start.plusHours(1), "test"))
        val results = asUser { search(name.uppercase()).items }
        assertEquals(listOf(name, "$name-1"), results.map { it.title })
    }

    @Test
    fun `Equally relevant builds across branches are ranked newest first`() {
        val prefix = token()
        val project = project(token())
        val start = Time.now().minusDays(1)
        val oldest = doCreateBuild(
            project.branch(token()),
            NameDescription.nd("$prefix-1", ""),
            Signature.of(start, "test")
        )
        val newest = doCreateBuild(
            project.branch(token()),
            NameDescription.nd("$prefix-2", ""),
            Signature.of(start.plusHours(2), "test")
        )
        val middle = doCreateBuild(
            project.branch(token()),
            NameDescription.nd("$prefix-3", ""),
            Signature.of(start.plusHours(1), "test")
        )
        val results = asUser { search(prefix).items }
        assertEquals(listOf(newest.name, middle.name, oldest.name), results.map { it.title })
    }

    @Test
    fun `A build is found on its display name, which is its title`() {
        val name = token()
        val release = token()
        val branch = project(token()).branch(token())
        val build = branch.build(name)
        build.release(release)
        // Exact match on the display name
        val results = asUser { search(release).items }
        assertEquals(listOf(release), results.map { it.title })
        assertEquals(release, results.first().data?.get("release"))
        @Suppress("UNCHECKED_CAST")
        assertEquals(name, (results.first().data?.get("build") as Map<String, *>)["name"])
        // Prefix on the display name
        assertEquals(listOf(release), asUser { search(release.take(8)).items.map { it.title } })
        // The name still finds the build, shown by its display name
        assertEquals(listOf(release), asUser { search(name).items.map { it.title } })
    }

    @Test
    fun `An exact display name comes before builds sharing its prefix`() {
        val value = token()
        val branch = project(token()).branch(token())
        val start = Time.now().minusDays(1)
        doCreateBuild(branch, NameDescription.nd("1", ""), Signature.of(start, "test")).release("$value-1")
        doCreateBuild(branch, NameDescription.nd("2", ""), Signature.of(start.plusHours(1), "test")).release("$value-2")
        assertEquals(listOf("$value-1", "$value-2"), asUser { search("$value-1").items.map { it.title } })
        assertEquals(listOf("$value-2", "$value-1"), asUser { search(value).items.map { it.title } })
    }

    @Test
    fun `A build whose display name is removed is not found on it any longer`() {
        val name = token()
        val release = token()
        val branch = project(token()).branch(token())
        val build = branch.build(name)
        build.release(release)
        assertEquals(listOf(release), asUser { search(release).items.map { it.title } })
        asAdmin { propertyService.deleteProperty(build, ReleasePropertyType::class.java) }
        assertEquals(0, asUser { search(release).total })
        assertEquals(listOf(name), asUser { search(name).items.map { it.title } })
    }

    @Test
    fun `An updated build is searchable by its new name only`() {
        val oldName = token()
        val newName = token()
        val build = project(token()).branch(token()).build(oldName)
        asAdmin { structureService.saveBuild(build.withName(newName)) }
        assertEquals(0, asUser { search(oldName).total })
        assertEquals(listOf(newName), asUser { search(newName).items.map { it.title } })
    }

    @Test
    fun `A deleted build is not searchable any longer`() {
        val name = token()
        val build = project(token()).branch(token()).build(name)
        assertEquals(1, asUser { search(name).total })
        asAdmin { structureService.deleteBuild(build.id) }
        assertEquals(0, asUser { search(name).total })
    }

    @Test
    fun `Search builds and filter on access rights with correct totals`() {
        val prefix = token()
        val builds = (0..3).map {
            project(token()).branch(token()).build("$prefix-$it")
        }
        withNoGrantViewToAll {
            builds[0].project.asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                val results = search(prefix)
                assertEquals(1, results.total)
                assertEquals(listOf(builds[0].name), results.items.map { it.title })
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
    fun `Rebuilding the build documents`() {
        val prefix = token()
        val release = token()
        val project = project(token()) {
            val branch = branch(token())
            branch.build("$prefix-1")
            branch.build("$prefix-2").release(release)
        }
        try {
            searchService.reindex(BUILD_SEARCH_RESULT_TYPE)
            assertEquals(2, asUser { search(prefix).total })
            assertEquals(listOf(release), asUser { search(release).items.map { it.title } })
        } finally {
            asAdmin { structureService.deleteProject(project.id) }
        }
    }

}
