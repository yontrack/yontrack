package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.extension.general.RELEASE_PROPERTY
import net.nemerosa.ontrack.kdsl.spec.extension.general.release
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals

/**
 * Properties written through a replica are read by the next request served by another one: they
 * are read once per request, never cached across requests (#2081).
 *
 * Each test first has B read the properties, which it would have kept in a cache, then changes them
 * through A, and checks that the very next request through B reads the new values.
 */
class ACCHAProperties : AbstractACCHATestSupport() {

    @Test
    fun `a property written through A is read through B on the next request`() {
        val build = ontrackA.createProject(uid("p_"), "")
            .createBranch(uid("b_"), "")
            .createBuild(uid("1_"))
        build.release = "1.0"
        assertEquals("1.0" to "1.0", readOnB(build), "Release read through B")

        build.release = "2.0"
        assertEquals("2.0" to "2.0", readOnB(build), "New release read through B")
    }

    @Test
    fun `a property written through A is read through B in a list of builds`() {
        val branch = ontrackA.createProject(uid("p_"), "")
            .createBranch(uid("b_"), "")
        val builds = (1..3).map { no ->
            branch.createBuild("$no").apply { release = "1.$no" }
        }
        assertEquals(listOf("1.3", "1.2", "1.1"), listOnB(branch.id.toInt()), "Releases read through B")

        builds[1].release = "2.2"
        assertEquals(listOf("1.3", "2.2", "1.1"), listOnB(branch.id.toInt()), "New release read through B")
    }

    /**
     * Release property and display name of the build, as read through B
     */
    private fun readOnB(build: Build): Pair<String?, String?> {
        val node = graphQL(
            ACCProperties.HA.B.url,
            """
                {
                    build(id: ${build.id}) {
                        displayName
                        properties(type: "$RELEASE_PROPERTY") {
                            value
                        }
                    }
                }
            """
        ).path("data").path("build")
        return node.path("properties").path(0).path("value").path("name").textOrNull() to
                node.path("displayName").textOrNull()
    }

    /**
     * Display names of the builds of the branch, as listed through B
     */
    private fun listOnB(branchId: Int): List<String> =
        graphQL(
            ACCProperties.HA.B.url,
            """
                {
                    branches(id: $branchId) {
                        buildsPaginated(size: 10) {
                            pageItems {
                                displayName
                            }
                        }
                    }
                }
            """
        ).path("data").path("branches").path(0).path("buildsPaginated").path("pageItems").values()
            .map { it.path("displayName").asString() }

    private fun JsonNode.textOrNull(): String? = takeIf { it.isString }?.asString()

}
