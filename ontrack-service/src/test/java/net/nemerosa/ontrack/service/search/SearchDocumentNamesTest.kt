package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.SearchDocumentReference
import net.nemerosa.ontrack.model.structure.branchSearchDocumentReferences
import net.nemerosa.ontrack.model.structure.buildSearchDocumentReferences
import net.nemerosa.ontrack.model.structure.projectSearchDocumentReferences
import net.nemerosa.ontrack.repository.search.SearchDocumentHit
import net.nemerosa.ontrack.repository.search.SearchDocumentReferenceName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SearchDocumentNamesTest {

    /**
     * Current names: projects 1 (visible) and 2 (not visible), branches 10 (of project 1) and 20
     * (of project 2)
     */
    private val names = listOf(
        SearchDocumentReferenceName(ProjectEntityType.PROJECT, 1, "P1", 1),
        SearchDocumentReferenceName(ProjectEntityType.PROJECT, 2, "P2", 2),
        SearchDocumentReferenceName(ProjectEntityType.BRANCH, 10, "B10", 1),
        SearchDocumentReferenceName(ProjectEntityType.BRANCH, 20, "B20", 2),
    )

    private val lookups = mutableListOf<Pair<Set<Int>, Set<Int>>>()

    private fun resolve(
        hits: List<SearchDocumentHit>,
        references: Map<String, List<SearchDocumentReference>>,
    ) = SearchDocumentNames.resolve(
        hits = hits,
        references = { type -> references[type] ?: emptyList() },
        lookup = { projectIds, branchIds ->
            lookups += projectIds to branchIds
            names.filter {
                (it.type == ProjectEntityType.PROJECT && it.id in projectIds) ||
                        (it.type == ProjectEntityType.BRANCH && it.id in branchIds)
            }
        },
        visible = { projectId -> projectId == 1 },
    )

    private fun hit(type: String, data: Any) = SearchDocumentHit(
        type = type,
        key = "key",
        projectId = 1,
        entity = null,
        title = "title",
        text = null,
        data = data.asJson(),
        updatedAt = LocalDateTime.now(),
        score = 1.0,
    )

    private fun build(branchId: Int, projectId: Int) = mapOf(
        "id" to 100,
        "name" to "1.0",
        "branch" to mapOf(
            "id" to branchId,
            "name" to "old-branch",
            "project" to mapOf("id" to projectId, "name" to "old-project"),
        ),
    )

    @Test
    fun `The names of the references are replaced by the current ones`() {
        val hits = resolve(
            listOf(hit("build", mapOf("build" to build(10, 1), "release" to "1.0"))),
            mapOf("build" to buildSearchDocumentReferences("build")),
        )
        assertEquals(
            mapOf(
                "build" to mapOf(
                    "id" to 100,
                    "name" to "1.0",
                    "branch" to mapOf(
                        "id" to 10,
                        "name" to "B10",
                        "project" to mapOf("id" to 1, "name" to "P1"),
                    ),
                ),
                "release" to "1.0",
            ).asJson(),
            hits.single().data,
        )
    }

    @Test
    fun `All the hits are resolved in one lookup`() {
        resolve(
            listOf(
                hit("link", mapOf("sourceBuild" to build(10, 1), "targetBuild" to build(20, 2))),
                hit("link", mapOf("sourceBuild" to build(10, 1), "targetBuild" to build(10, 1))),
                hit("project", mapOf("project" to mapOf("id" to 1, "name" to "old"))),
            ),
            mapOf(
                "link" to buildSearchDocumentReferences("sourceBuild") + buildSearchDocumentReferences("targetBuild"),
                "project" to projectSearchDocumentReferences("project"),
            ),
        )
        assertEquals(listOf(setOf(1, 2) to setOf(10, 20)), lookups)
    }

    @Test
    fun `The name of a project the user cannot see is null, and so is the name of its branches`() {
        val hits = resolve(
            listOf(hit("link", mapOf("sourceBuild" to build(10, 1), "targetBuild" to build(20, 2)))),
            mapOf("link" to buildSearchDocumentReferences("sourceBuild") + buildSearchDocumentReferences("targetBuild")),
        )
        val data = hits.single().data
        assertEquals("B10", data.at("/sourceBuild/branch/name").asString())
        assertEquals("P1", data.at("/sourceBuild/branch/project/name").asString())
        assertEquals(true, data.at("/targetBuild/branch/name").isNull)
        assertEquals(true, data.at("/targetBuild/branch/project/name").isNull)
    }

    @Test
    fun `The name of a project or branch which does not exist any longer is null`() {
        val hits = resolve(
            listOf(hit("build", mapOf("build" to build(30, 3)))),
            mapOf("build" to buildSearchDocumentReferences("build")),
        )
        val data = hits.single().data
        assertEquals(true, data.at("/build/branch/name").isNull)
        assertEquals(true, data.at("/build/branch/project/name").isNull)
    }

    @Test
    fun `Every element of an array on the path is a reference`() {
        val hits = resolve(
            listOf(
                hit(
                    "finding", mapOf(
                        "project" to mapOf("id" to 1, "name" to "old"),
                        "branches" to listOf(
                            mapOf("id" to 10, "name" to "old", "state" to "OPEN"),
                            mapOf("id" to 20, "name" to "old", "state" to "FIXED"),
                        ),
                    )
                )
            ),
            mapOf("finding" to projectSearchDocumentReferences("project") + SearchDocumentReference.branch("branches")),
        )
        val data = hits.single().data
        assertEquals("P1", data.at("/project/name").asString())
        assertEquals("B10", data.at("/branches/0/name").asString())
        assertEquals("OPEN", data.at("/branches/0/state").asString())
        assertEquals(true, data.at("/branches/1/name").isNull)
    }

    @Test
    fun `A reference absent from the data is ignored`() {
        val hits = resolve(
            listOf(hit("catalog", mapOf("scmCatalogEntry" to mapOf("repository" to "repo"), "project" to null))),
            mapOf("catalog" to branchSearchDocumentReferences("project.branch") + projectSearchDocumentReferences("project")),
        )
        assertEquals(
            mapOf("scmCatalogEntry" to mapOf("repository" to "repo"), "project" to null).asJson(),
            hits.single().data,
        )
        assertEquals(emptyList(), lookups)
    }

    @Test
    fun `Hits of types without references are returned as they are, without any lookup`() {
        val hit = hit("commit", mapOf("item" to mapOf("id" to "abcdef")))
        val hits = resolve(listOf(hit), emptyMap())
        assertSame(hit, hits.single())
        assertEquals(emptyList(), lookups)
    }

    @Test
    fun `The data of the hits found are not changed in place`() {
        val hit = hit("build", mapOf("build" to build(10, 1)))
        resolve(listOf(hit), mapOf("build" to buildSearchDocumentReferences("build")))
        assertEquals("old-branch", hit.data.at("/build/branch/name").asString())
    }
}
