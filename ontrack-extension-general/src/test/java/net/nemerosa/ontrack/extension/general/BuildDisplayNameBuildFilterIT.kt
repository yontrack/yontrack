package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Branch
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Filtering of the builds of a branch on their display name.
 *
 * This lives in the general extension because the display name of a build is provided by the
 * [LabelBuildDisplayNameExtension], which is only available here.
 */
@AsAdminTest
class BuildDisplayNameBuildFilterIT : AbstractDSLTestSupport() {

    private fun Branch.filterOnDisplayName(regex: String): List<String> =
        buildFilterService.standardFilterProviderData(10)
            .withWithDisplayName(regex)
            .build()
            .filterBranchBuilds(this)
            .map { it.name }
            .sorted()

    @Test
    fun `Labelled builds are matched on their label and unlabelled builds on their name`() {
        project {
            branch {
                build("build-1") { releaseProperty(this, "1.2.0") }
                build("2.0.0-plain")
                build("build-3") { releaseProperty(this, "9.9.9") }

                assertEquals(
                    listOf("2.0.0-plain", "build-1"),
                    filterOnDisplayName("^(1\\.2|2\\.0)"),
                    "The label of the first build and the name of the second one are both matched"
                )
            }
        }
    }

    @Test
    fun `The display name filter is case insensitive`() {
        project {
            branch {
                build("build-1") { releaseProperty(this, "Release-1.0") }
                build("build-2") { releaseProperty(this, "other") }

                assertEquals(
                    listOf("build-1"),
                    filterOnDisplayName("^release-")
                )
            }
        }
    }

    @Test
    fun `The display name filter is a partial match unless it is anchored`() {
        project {
            branch {
                build("build-1") { releaseProperty(this, "v1.2.4") }

                assertEquals(
                    listOf("build-1"),
                    filterOnDisplayName("1\\.2\\."),
                    "The pattern is looked for anywhere in the display name"
                )
                assertTrue(
                    filterOnDisplayName("^1\\.2\\.").isEmpty(),
                    "The anchored pattern does not match a display name starting with v"
                )
            }
        }
    }

    @Test
    fun `A build with a blank label is matched on its name`() {
        project {
            branch {
                build("build-1") { releaseProperty(this, "") }

                assertEquals(
                    listOf("build-1"),
                    filterOnDisplayName("^build-"),
                    "A blank label falls back to the build name"
                )
            }
        }
    }

    @Test
    fun `The label is matched even when the project does not display labels`() {
        project {
            useLabel(this, false)
            branch {
                build("build-1") { releaseProperty(this, "1.2.0") }

                assertEquals(
                    listOf("build-1"),
                    filterOnDisplayName("^1\\.2\\."),
                    "The label is matched even if the project displays build names"
                )
            }
        }
    }

    @Test
    fun `The display name filter can be combined with a property filter`() {
        project {
            branch {
                build("build-1") { releaseProperty(this, "1.2.0") }
                build("build-2") { releaseProperty(this, "1.3.0") }

                // Both criteria join on the PROPERTIES table: they must not collide
                val builds = buildFilterService.standardFilterProviderData(10)
                    .withWithProperty(ReleasePropertyType::class.java.name)
                    .withWithPropertyValue("1.2.0")
                    .withWithDisplayName("^1\\.")
                    .build()
                    .filterBranchBuilds(this)
                    .map { it.name }

                assertEquals(listOf("build-1"), builds)
            }
        }
    }

    @Test
    fun `An invalid regular expression returns no build`() {
        project {
            branch {
                build("build-1") { releaseProperty(this, "1.2.0") }

                assertTrue(filterOnDisplayName("[unclosed").isEmpty())
            }
        }
    }

    @Test
    fun `The display name filter is applied when paginating`() {
        project {
            branch {
                build("build-1") { releaseProperty(this, "1.2.0") }
                build("2.0.0-plain")
                build("build-3") { releaseProperty(this, "9.9.9") }

                val page = buildFilterService.standardFilterProviderData(10)
                    .withWithDisplayName("^(1\\.2|2\\.0)")
                    .build()
                    .filterBranchBuildsWithPagination(this, 0, 10)

                assertEquals(2, page.pageInfo.totalSize)
                assertEquals(
                    listOf("2.0.0-plain", "build-1"),
                    page.pageItems.map { it.name }.sorted()
                )
            }
        }
    }

}
