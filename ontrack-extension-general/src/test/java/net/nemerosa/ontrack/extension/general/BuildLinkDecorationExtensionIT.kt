package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildLinkDecorationExtensionIT : AbstractGeneralExtensionTestSupport() {

    @Autowired
    private lateinit var extension: BuildLinkDecorationExtension

    @Test
    fun `No link, no decoration`() {
        project {
            branch {
                build {
                    val decorations = extension.getDecorations(this)
                    assertTrue(decorations.isEmpty(), "No decoration when the build has no link")
                }
            }
        }
    }

    @Test
    fun `Dependency links only`() {
        val ref1 = otherBuild()
        val ref2 = otherBuild()
        project {
            branch {
                build {
                    linkTo(ref1)
                    linkTo(ref2)
                    val list = decorationOf(this)
                    assertEquals(id(), list.path("buildId").asInt())
                    assertEquals(2, list.path("dependencyCount").asInt())
                    assertEquals(0, list.path("consumerCount").asInt())
                    assertEquals(10, list.path("maxLinks").asInt())
                }
            }
        }
    }

    @Test
    fun `Consumer links only`() {
        val target = otherBuild()
        otherBuild().linkTo(target)
        otherBuild().linkTo(target)
        otherBuild().linkTo(target)
        val list = decorationOf(target)
        assertEquals(target.id(), list.path("buildId").asInt())
        assertEquals(0, list.path("dependencyCount").asInt())
        assertEquals(3, list.path("consumerCount").asInt())
        assertEquals(10, list.path("maxLinks").asInt())
    }

    @Test
    fun `Dependency and consumer links`() {
        val build = otherBuild()
        build.linkTo(otherBuild())
        otherBuild().linkTo(build)
        otherBuild().linkTo(build)
        val list = decorationOf(build)
        assertEquals(1, list.path("dependencyCount").asInt())
        assertEquals(2, list.path("consumerCount").asInt())
        assertEquals(10, list.path("maxLinks").asInt())
    }

    @Test
    fun `The names shipped in 5_5_11 stay as aliases of the counts`() {
        val build = otherBuild()
        build.linkTo(otherBuild())
        otherBuild().linkTo(build)
        otherBuild().linkTo(build)
        val list = decorationOf(build)
        assertEquals(1, list.path("linksCount").asInt())
        assertEquals(2, list.path("upstreamCount").asInt())
    }

    @Test
    fun `Maximum number of links taken from the configuration`() {
        val build = otherBuild()
        build.linkTo(otherBuild())
        val old = ontrackConfigProperties.buildLinks.decorationMaxLinks
        try {
            ontrackConfigProperties.buildLinks.decorationMaxLinks = 3
            assertEquals(3, decorationOf(build).path("maxLinks").asInt())
        } finally {
            ontrackConfigProperties.buildLinks.decorationMaxLinks = old
        }
    }

    private fun otherBuild(): Build = project<Build> {
        branch<Build> {
            build()
        }
    }

    /**
     * The decoration as its clients read it, as JSON.
     */
    private fun decorationOf(build: Build): JsonNode {
        val decorations = extension.getDecorations(build)
        assertEquals(1, decorations.size)
        return assertNotNull(decorations.first().data).asJson()
    }

}
