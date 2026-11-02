package net.nemerosa.ontrack.extension.github.ingestion.extensions.links

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

internal class IngestionLinksEventProcessorIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var ingestionLinksEventProcessor: IngestionLinksEventProcessor

    @Test
    fun `Adding links only`() {
        runScenario(addOnly = true) { ref, a, b, c ->
            // Gets the final links
            val links = structureService.getBuildDependencyLinks(ref, size = Int.MAX_VALUE).pageItems
            assertEquals(
                listOf(a, b, c).map { it.id() }.toSet(),
                links.map { it.build.id() }.toSet(),
                "Existing links have been preserved"
            )
        }
    }

    @Test
    fun `Replacing links`() {
        runScenario(addOnly = false) { ref, _, b, c ->
            // Gets the final links
            val links = structureService.getBuildDependencyLinks(ref, size = Int.MAX_VALUE).pageItems
            assertEquals(
                listOf(b, c).map { it.id() }.toSet(),
                links.map { it.build.id() }.toSet(),
                "Existing links have been deleted"
            )
        }
    }

    private fun runScenario(addOnly: Boolean, test: (ref: Build, a: Build, b: Build, c: Build) -> Unit) {
        asAdmin {
            val a = doCreateBuild()
            val b = doCreateBuild()
            val c = doCreateBuild()
            project {
                branch {
                    build {
                        linkTo(a)
                        // Link to the two other builds
                        ingestionLinksEventProcessor.process(
                            GitHubIngestionLinksPayload(
                                owner = "not-used-here",
                                repository = project.name,
                                buildName = name,
                                buildLinks = listOf(
                                    GitHubIngestionLink(b.project.name, b.name),
                                    GitHubIngestionLink(c.project.name, c.name),
                                ),
                                addOnly = addOnly,
                            ),
                            null
                        )
                        // Testing
                        test(this, a, b, c)
                    }
                }
            }
        }
    }

}