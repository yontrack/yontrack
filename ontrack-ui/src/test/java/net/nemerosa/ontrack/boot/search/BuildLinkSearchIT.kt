package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.general.BuildLinkSearchExtension
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.SearchQueryRequest
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Search documents for the build links, on Postgres.
 */
class BuildLinkSearchIT : AbstractSearchTestSupport() {

    /**
     * Random name, which no other one is similar to, even by trigram
     */
    private fun token() = "l" + UUID.randomUUID().toString().replace("-", "").take(15)

    private fun search(query: String) =
        searchService.search(
            SearchQueryRequest(
                query = query,
                types = listOf(BuildLinkSearchExtension.SEARCH_RESULT_TYPE),
                size = 50
            )
        )

    /**
     * IDs of the source builds found for a query
     */
    @Suppress("UNCHECKED_CAST")
    private fun sources(query: String): List<Int> =
        asUser { search(query).items }.map { (it.data?.get("sourceBuild") as Map<String, *>)["id"] as Int }

    private fun target(name: String = token()): Build = project(token()).branch(token()).build(name)

    private fun source(): Build = project(token()).branch(token()).build(token())

    @Test
    fun `A link is searchable as soon as it is created, with what its result renders`() {
        val target = target()
        val source = source()
        source.linkTo(target, "dev")
        val results = asUser { search("${target.project.name}:${target.name}").items }
        assertEquals(1, results.size)
        results.first().apply {
            assertEquals("${target.project.name}:${target.name}", title)
            assertEquals(BuildLinkSearchExtension.SEARCH_RESULT_TYPE, type.id)
            assertEquals("dev", data?.get("qualifier"))
            @Suppress("UNCHECKED_CAST")
            val sourceData = data?.get("sourceBuild") as Map<String, *>
            assertEquals(source.id(), sourceData["id"])
            assertEquals(source.name, sourceData["name"])
            @Suppress("UNCHECKED_CAST")
            val sourceBranch = sourceData["branch"] as Map<String, *>
            assertEquals(source.branch.id(), sourceBranch["id"])
            assertEquals(source.branch.name, sourceBranch["name"])
            @Suppress("UNCHECKED_CAST")
            val sourceProject = sourceBranch["project"] as Map<String, *>
            assertEquals(source.project.id(), sourceProject["id"])
            assertEquals(source.project.name, sourceProject["name"])
            @Suppress("UNCHECKED_CAST")
            val targetData = data?.get("targetBuild") as Map<String, *>
            assertEquals(target.id(), targetData["id"])
            assertEquals(target.name, targetData["name"])
            @Suppress("UNCHECKED_CAST")
            val targetBranch = targetData["branch"] as Map<String, *>
            assertEquals(target.branch.id(), targetBranch["id"])
            @Suppress("UNCHECKED_CAST")
            assertEquals(target.project.name, (targetBranch["project"] as Map<String, *>)["name"])
        }
    }

    @Test
    fun `A link is found on the project of its target`() {
        val target = target()
        val source = source()
        source.linkTo(target)
        assertEquals(listOf(source.id()), sources(target.project.name))
    }

    @Test
    fun `A link is found on the display name of its target, as one document`() {
        val version = token()
        val target = target()
        target.release(version)
        val source = source()
        source.linkTo(target)
        val results = asUser { search("${target.project.name}:$version").items }
        assertEquals(listOf("${target.project.name}:$version"), results.map { it.title })
        // Still found on the name of the target, as the same document
        assertEquals(listOf(source.id()), sources("${target.project.name}:${target.name}"))
        assertEquals(1, asUser { search(target.project.name).total })
    }

    @Test
    fun `A link follows the display name its target gets after the link`() {
        val version = token()
        val target = target()
        val source = source()
        source.linkTo(target)
        assertEquals(0, asUser { search("${target.project.name}:$version").total })
        target.release(version)
        assertEquals(listOf(source.id()), sources("${target.project.name}:$version"))
        asAdmin { propertyService.deleteProperty(target, ReleasePropertyType::class.java) }
        assertEquals(0, asUser { search("${target.project.name}:$version").total })
    }

    @Test
    fun `Each qualified link to the same target is its own document`() {
        val target = target()
        val source = source()
        source.linkTo(target)
        source.linkTo(target, "dev")
        val query = "${target.project.name}:${target.name}"
        assertEquals(2, asUser { search(query).total })
        asAdmin { structureService.deleteBuildLink(source, target, "dev") }
        val results = asUser { search(query).items }
        assertEquals(listOf(""), results.map { it.data?.get("qualifier") })
    }

    @Test
    fun `Links are ranked newest source first`() {
        val target = target()
        val project = project(token())
        val start = Time.now().minusDays(1)
        val oldest = doCreateBuild(project.branch(token()), NameDescription.nd(token(), ""), Signature.of(start, "test"))
        val newest = doCreateBuild(project.branch(token()), NameDescription.nd(token(), ""), Signature.of(start.plusHours(2), "test"))
        val middle = doCreateBuild(project.branch(token()), NameDescription.nd(token(), ""), Signature.of(start.plusHours(1), "test"))
        listOf(oldest, newest, middle).forEach { it.linkTo(target) }
        assertEquals(
            listOf(newest.id(), middle.id(), oldest.id()),
            sources("${target.project.name}:${target.name}")
        )
    }

    @Test
    fun `A removed link is not searchable any longer`() {
        val target = target()
        val source = source()
        source.linkTo(target)
        assertEquals(listOf(source.id()), sources(target.project.name))
        asAdmin { source.unlinkTo(target) }
        assertEquals(0, asUser { search(target.project.name).total })
    }

    @Test
    fun `The link of a deleted source build is not searchable any longer`() {
        val target = target()
        val source = source()
        source.linkTo(target)
        asAdmin { structureService.deleteBuild(source.id) }
        assertEquals(0, asUser { search(target.project.name).total })
    }

    @Test
    fun `The link of a deleted source branch is not searchable any longer`() {
        val target = target()
        val source = source()
        source.linkTo(target)
        asAdmin { structureService.deleteBranch(source.branch.id) }
        assertEquals(0, asUser { search(target.project.name).total })
    }

    @Test
    fun `The link to a deleted target build is not searchable any longer`() {
        val target = target()
        val source = source()
        source.linkTo(target)
        asAdmin { structureService.deleteBuild(target.id) }
        assertEquals(0, asUser { search(target.project.name).total })
    }

    @Test
    fun `The link of a renamed source build renders its new name`() {
        val target = target()
        val source = source()
        source.linkTo(target)
        val newName = token()
        asAdmin { structureService.saveBuild(source.withName(newName)) }
        val result = asUser { search(target.project.name).items }.single()
        @Suppress("UNCHECKED_CAST")
        assertEquals(newName, (result.data?.get("sourceBuild") as Map<String, *>)["name"])
    }

    @Test
    fun `The link to a renamed target build is found on its new name only`() {
        val target = target()
        val source = source()
        source.linkTo(target)
        val newName = token()
        asAdmin { structureService.saveBuild(target.withName(newName)) }
        assertEquals(0, asUser { search("${target.project.name}:${target.name}").total })
        assertEquals(listOf(source.id()), sources("${target.project.name}:$newName"))
    }

    @Test
    fun `Search links and filter on access rights to the source`() {
        val target = target()
        val authorized = source()
        val restricted = source()
        authorized.linkTo(target)
        restricted.linkTo(target)
        withNoGrantViewToAll {
            asUserWithView(authorized, target) {
                val results = search(target.project.name)
                assertEquals(1, results.total)
                @Suppress("UNCHECKED_CAST")
                assertEquals(
                    listOf(authorized.id()),
                    results.items.map { (it.data?.get("sourceBuild") as Map<String, *>)["id"] }
                )
            }
            asUserWithView(target) {
                assertEquals(0, search(target.project.name).total)
            }
            asGlobalRole(Roles.GLOBAL_READ_ONLY) {
                assertEquals(2, search(target.project.name).total)
            }
        }
    }

    /**
     * The rebuild runs in its own transactions: the builds and links must be committed.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `Rebuilding the link documents deletes the links to a deleted target branch`() {
        val version = token()
        val target = target()
        target.release(version)
        val source = source()
        source.linkTo(target)
        source.linkTo(target, "dev")
        try {
            searchService.reindex(BuildLinkSearchExtension.SEARCH_RESULT_TYPE)
            assertEquals(2, asUser { search("${target.project.name}:$version").total })
            // Deleting the branch of the target is repaired by the reconciliation
            asAdmin { structureService.deleteBranch(target.branch.id) }
            searchService.reindex(BuildLinkSearchExtension.SEARCH_RESULT_TYPE)
            assertEquals(0, asUser { search(target.project.name).total })
        } finally {
            asAdmin {
                structureService.deleteProject(source.project.id)
                structureService.deleteProject(target.project.id)
            }
        }
    }

}
