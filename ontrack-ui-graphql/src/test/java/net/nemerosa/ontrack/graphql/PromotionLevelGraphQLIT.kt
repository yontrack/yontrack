package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.general.BuildLinkDisplayProperty
import net.nemerosa.ontrack.extension.general.BuildLinkDisplayPropertyType
import net.nemerosa.ontrack.extension.general.ReleaseProperty
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.isNullOrNullNode
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.test.assertPresent
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests around the `promotionLevel` root query.
 */
@AsAdminTest
class PromotionLevelGraphQLIT : AbstractQLKTITSupport() {

    @Test
    fun `Creation of a promotion level`() {
        asAdmin {
            project {
                branch {
                    run("""
                        mutation {
                            setupPromotionLevel(input: {
                                project: "${project.name}",
                                branch: "$name",
                                promotion: "GOLD"
                            }) {
                                promotionLevel {
                                    id
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """).let { data ->
                        val node = assertNoUserError(data, "setupPromotionLevel")
                        assertTrue(node.path("promotionLevel").path("id").asInt() != 0, "PL created")

                        assertPresent(structureService.findPromotionLevelByName(project.name, name, "GOLD")) {
                            assertEquals("GOLD", it.name)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Update of a promotion level`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    run("""
                        mutation {
                            setupPromotionLevel(input: {
                                project: "${project.name}",
                                branch: "$name",
                                promotion: "${pl.name}",
                                description: "New description"
                            }) {
                                promotionLevel {
                                    id
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """).let { data ->
                        val node = assertNoUserError(data, "setupPromotionLevel")
                        assertEquals(pl.id(), node.path("promotionLevel").path("id").asInt(), "PL updated")

                        assertPresent(structureService.findPromotionLevelByName(project.name, name, pl.name)) {
                            assertEquals(pl.name, it.name)
                            assertEquals("New description", it.description)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Annotated description`() {
        project {
            branch {
                val pl = promotionLevel(description = "A description linking to https://documentation.org/reference")
                val data = run("""{
                    promotionLevel(id: ${pl.id}) {
                        name
                        description
                        annotatedDescription
                    }
                }""")
                val promotion = data["promotionLevel"]
                assertEquals(pl.name, promotion["name"].textValue())
                assertEquals("A description linking to https://documentation.org/reference",
                    promotion["description"].textValue())
                assertEquals("""A description linking to <a href="https://documentation.org/reference" target="_blank">https://documentation.org/reference</a>""",
                    promotion["annotatedDescription"].textValue())
            }
        }
    }

    @Test
    fun `Paginated list of promotion runs`() {
        project {
            branch {
                val pl = promotionLevel()
                val build = build {
                    (1..25).forEach { no ->
                        promote(pl, description = "Run n°$no")
                    }
                }
                // Getting a paginated list of promotion runs
                val query = """
                    query PromotionRuns(${'$'}id: Int!, ${'$'}offset: Int!, ${'$'}size: Int!) {
                        promotionLevel(id: ${'$'}id) {
                            promotionRunsPaginated(offset: ${'$'}offset, size: ${'$'}size) {
                                pageInfo {
                                    totalSize
                                    previousPage { offset size }
                                    nextPage { offset size }
                                }
                                pageItems {
                                    description
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
                """
                // Getting the first page
                run(query, mapOf("id" to pl.id(), "offset" to 0, "size" to 10)).apply {
                    val field = path("promotionLevel").path("promotionRunsPaginated")
                    assertEquals(25, field["pageInfo"]["totalSize"].asInt())
                    assertTrue(field["pageInfo"]["previousPage"].isNullOrNullNode())
                    assertNotNull(field["pageInfo"]["nextPage"]) {
                        assertEquals(10, it["offset"].asInt())
                        assertEquals(10, it["size"].asInt())
                    }
                    val items = field["pageItems"]
                    assertEquals(10, items.size())
                    items.forEachIndexed { index, item ->
                        assertEquals(build.name, item["build"]["name"].asText())
                        assertEquals("Run n°${25 - index}", item["description"].asText())
                    }
                }
            }
        }
    }

    @Test
    fun `Paginated list of promotion runs filter by build name`() {
        project {
            branch {
                val pl = promotionLevel()
                (5..25).forEach { no ->
                    build(name = "1.$no") {
                        promote(pl)
                    }
                }
                // Getting a paginated list of promotion runs, filtered by build name
                val query = """
                    query PromotionRuns(${'$'}id: Int!, ${'$'}name: String!) {
                        promotionLevel(id: ${'$'}id) {
                            promotionRunsPaginated(name: ${'$'}name, size: 5) {
                                pageInfo {
                                    totalSize
                                    previousPage { offset size }
                                    nextPage { offset size }
                                }
                                pageItems {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
                """
                // Getting the first page
                run(query, mapOf("id" to pl.id(), "name" to "1\\.1.*")).apply {
                    val field = path("promotionLevel").path("promotionRunsPaginated")
                    assertEquals(10, field["pageInfo"]["totalSize"].asInt())
                    assertTrue(field["pageInfo"]["previousPage"].isNullOrNullNode())
                    assertNotNull(field["pageInfo"]["nextPage"]) {
                        assertEquals(5, it["offset"].asInt())
                        assertEquals(5, it["size"].asInt())
                    }
                    val items = field["pageItems"]
                    assertEquals(5, items.size())
                    items.forEachIndexed { index, item ->
                        assertEquals("1.1${9 - index}", item["build"]["name"].asText())
                    }
                }
            }
        }
    }

    @Test
    fun `Paginated list of promotion runs filter by version name`() {
        project {
            property(BuildLinkDisplayPropertyType::class, BuildLinkDisplayProperty(useLabel = true))
            branch {
                val pl = promotionLevel()
                (5..25).forEach { no ->
                    build(name = "$no") {
                        property(ReleasePropertyType::class, ReleaseProperty("1.$no"))
                        promote(pl)
                    }
                }
                // Getting a paginated list of promotion runs, filtered by version name
                val query = """
                    query PromotionRuns(${'$'}id: Int!, ${'$'}version: String!) {
                        promotionLevel(id: ${'$'}id) {
                            promotionRunsPaginated(version: ${'$'}version, size: 5) {
                                pageInfo {
                                    totalSize
                                    previousPage { offset size }
                                    nextPage { offset size }
                                }
                                pageItems {
                                    build {
                                        name
                                        releaseProperty { value }
                                    }
                                }
                            }
                        }
                    }
                """
                // Getting the first page
                run(query, mapOf("id" to pl.id(), "version" to "1\\.1.*")).apply {
                    val field = path("promotionLevel").path("promotionRunsPaginated")
                    assertEquals(10, field["pageInfo"]["totalSize"].asInt())
                    assertTrue(field["pageInfo"]["previousPage"].isNullOrNullNode())
                    assertNotNull(field["pageInfo"]["nextPage"]) {
                        assertEquals(5, it["offset"].asInt())
                        assertEquals(5, it["size"].asInt())
                    }
                    val items = field["pageItems"]
                    assertEquals(5, items.size())
                    items.forEachIndexed { index, item ->
                        assertEquals("1.1${9 - index}", item["build"]["releaseProperty"]["value"]["name"].asText())
                        assertEquals("1${9 - index}", item["build"]["name"].asText())
                    }
                }
            }
        }
    }

    @Test
    fun `Paginated list of promotion runs filter by date`() {
        val refTime = Time.now()
        project {
            branch {
                val pl = promotionLevel()
                (1..20).forEach { no ->
                    build(name = "$no") {
                        promote(pl, signature = Signature.of(refTime - Duration.ofDays((20 - no).toLong()), "test"))
                    }
                }
                // Getting a paginated list of promotion runs, filtered by date
                val query = """
                    query PromotionRuns(${'$'}id: Int!, ${'$'}beforeDate: LocalDateTime, ${'$'}afterDate: LocalDateTime) {
                        promotionLevel(id: ${'$'}id) {
                            promotionRunsPaginated(beforeDate: ${'$'}beforeDate, afterDate: ${'$'}afterDate, size: 5) {
                                pageInfo {
                                    totalSize
                                    previousPage { offset size }
                                    nextPage { offset size }
                                }
                                pageItems {
                                    build {
                                        name
                                    }
                                }
                            }
                        }
                    }
                """
                // Getting the first page
                run(query, mapOf(
                    "id" to pl.id(),
                    "afterDate" to (refTime - Duration.ofDays(15L)),
                    "beforeDate" to (refTime - Duration.ofDays(5L))
                )).apply {
                    val field = path("promotionLevel").path("promotionRunsPaginated")
                    assertEquals(10, field["pageInfo"]["totalSize"].asInt())
                    assertTrue(field["pageInfo"]["previousPage"].isNullOrNullNode())
                    assertNotNull(field["pageInfo"]["nextPage"]) {
                        assertEquals(5, it["offset"].asInt())
                        assertEquals(5, it["size"].asInt())
                    }
                    val items = field["pageItems"]
                    assertEquals(5, items.size())
                    items.forEachIndexed { index, item ->
                        assertEquals("${15 - index}", item["build"]["name"].asText())
                    }
                }
            }
        }
    }

    @Test
    fun `Number of builds promoted to a promotion level`() {
        project {
            branch {
                val pl = promotionLevel()
                // One build promoted several times, which must count once
                build {
                    (1..3).forEach { no ->
                        promote(pl, description = "Run n°$no")
                    }
                }
                // Two builds promoted once
                build { promote(pl) }
                build { promote(pl) }
                // ... and one build never promoted at all
                build()
                run(
                    """
                        query PromotedBuildCount(${'$'}id: Int!) {
                            promotionLevel(id: ${'$'}id) {
                                promotedBuildCount
                                promotionRunsPaginated(size: 0) {
                                    pageInfo {
                                        totalSize
                                    }
                                }
                            }
                        }
                    """,
                    mapOf("id" to pl.id()),
                ).apply {
                    val field = path("promotionLevel")
                    // Three distinct builds reached the level...
                    assertEquals(3, field["promotedBuildCount"].asInt())
                    // ... over five promotion runs, which is the count NOT to display
                    assertEquals(5, field["promotionRunsPaginated"]["pageInfo"]["totalSize"].asInt())
                }
            }
        }
    }

    @Test
    fun `No build promoted to a promotion level`() {
        project {
            branch {
                val pl = promotionLevel()
                build()
                run(
                    """
                        query PromotedBuildCount(${'$'}id: Int!) {
                            promotionLevel(id: ${'$'}id) {
                                promotedBuildCount
                            }
                        }
                    """,
                    mapOf("id" to pl.id()),
                ).apply {
                    assertEquals(0, path("promotionLevel")["promotedBuildCount"].asInt())
                }
            }
        }
    }

}
