package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
    fun `Downstream links only`() {
        val ref1 = otherBuild()
        val ref2 = otherBuild()
        project {
            branch {
                build {
                    linkTo(ref1)
                    linkTo(ref2)
                    val list = decorationOf(this)
                    assertEquals(id(), list.buildId)
                    assertEquals(2, list.linksCount)
                    assertEquals(0, list.upstreamCount)
                    assertEquals(10, list.maxLinks)
                }
            }
        }
    }

    @Test
    fun `Upstream links only`() {
        val target = otherBuild()
        otherBuild().linkTo(target)
        otherBuild().linkTo(target)
        otherBuild().linkTo(target)
        val list = decorationOf(target)
        assertEquals(target.id(), list.buildId)
        assertEquals(0, list.linksCount)
        assertEquals(3, list.upstreamCount)
        assertEquals(10, list.maxLinks)
    }

    @Test
    fun `Downstream and upstream links`() {
        val build = otherBuild()
        build.linkTo(otherBuild())
        otherBuild().linkTo(build)
        otherBuild().linkTo(build)
        val list = decorationOf(build)
        assertEquals(1, list.linksCount)
        assertEquals(2, list.upstreamCount)
        assertEquals(10, list.maxLinks)
    }

    @Test
    fun `Maximum number of links taken from the configuration`() {
        val build = otherBuild()
        build.linkTo(otherBuild())
        val old = ontrackConfigProperties.buildLinks.decorationMaxLinks
        try {
            ontrackConfigProperties.buildLinks.decorationMaxLinks = 3
            assertEquals(3, decorationOf(build).maxLinks)
        } finally {
            ontrackConfigProperties.buildLinks.decorationMaxLinks = old
        }
    }

    private fun otherBuild(): Build = project<Build> {
        branch<Build> {
            build()
        }
    }

    private fun decorationOf(build: Build): BuildLinkDecorationList {
        val decorations = extension.getDecorations(build)
        assertEquals(1, decorations.size)
        return assertNotNull(decorations.first().data)
    }

}
