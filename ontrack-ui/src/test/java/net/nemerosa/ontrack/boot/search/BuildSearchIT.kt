package net.nemerosa.ontrack.boot.search

import net.nemerosa.ontrack.boot.BUILD_SEARCH_INDEX
import net.nemerosa.ontrack.boot.BUILD_SEARCH_RESULT_TYPE
import net.nemerosa.ontrack.common.waitFor
import net.nemerosa.ontrack.extension.general.ReleaseProperty
import net.nemerosa.ontrack.extension.general.ReleasePropertyType
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.SearchRequest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class BuildSearchIT : AbstractSearchTestSupport() {

    @Test
    fun `Searching builds`() {
        // Creates two builds with the same name
        val name = uid("B")
        val builds = (1..2).map {
            project<Build> {
                branch<Build> {
                    build(name)
                }
            }
        }
        // Creates other builds
        repeat(3) { project { branch { build() } } }
        // Indexes the builds
        index(BUILD_SEARCH_INDEX)
        // Searches for the builds using the name
        val results = asUser {
            searchService.paginatedSearch(
                SearchRequest(
                    token = name,
                    type = BUILD_SEARCH_RESULT_TYPE,
                )
            ).items
        }
        // Checks the builds have been found
        builds.forEach { build ->
            assertTrue(build.name in results.map { it.title })
        }
    }

    @Test
    fun `Searching builds on release`() {
        val name = uid("B")
        val release = uid("R")
        project<Build> {
            branch<Build> {
                build(name) {
                    setProperty(
                        this,
                        ReleasePropertyType::class.java,
                        ReleaseProperty(release)
                    )
                }
            }
        }
        // Creates other builds
        repeat(3) { project { branch { build() } } }
        // Indexes the builds
        index(BUILD_SEARCH_INDEX)
        // Searches for the builds using the name
        val results = asUser {
            searchService.paginatedSearch(
                SearchRequest(
                    token = name,
                    type = BUILD_SEARCH_RESULT_TYPE,
                )
            ).items
        }
        // Checks the builds have been found
        assertTrue(release in results.map { it.title })
    }

    @Test
    @AsAdminTest
    fun `Searching for a build based on its release information using the build index`() {
        val value = uid("V")
        val value1 = uid("V1")
        val value2 = uid("V2")
        project {
            branch {
                build("Build 1") {
                    release("$value-$value1")
                }
                build("Build 2") {
                    release("$value-$value2")
                }

                // Checks that an exact match ranks the matching build first. Both builds are
                // returned, since they share the same prefix and the index is an autocomplete one.
                var results = searchService.paginatedSearch(
                    SearchRequest(
                        token = "$value-$value1",
                        type = "build",
                    )
                )
                assertEquals(
                    "$value-$value1",
                    results.items.first().title,
                    "Build 1 as first result"
                )

                // Checks that we find two builds on the prefix match
                results = waitFor(
                    message = "Waiting for the indexation of the builds",
                    interval = 1.seconds,
                ) {
                    searchService.paginatedSearch(
                        SearchRequest(
                            token = value,
                            type = "build",
                        )
                    )
                } until {
                    it.items.size >= 2
                }
                assertNotNull(
                    results.items.find { it.title == "$value-$value1" },
                    "Build 1 found on prefix"
                )
                assertNotNull(
                    results.items.find { it.title == "$value-$value2" },
                    "Build 2 found on prefix"
                )
            }
        }

    }

    @Test
    @AsAdminTest
    fun `Exact match on a release label is case insensitive`() {
        val value = uid("V")
        val value1 = uid("V1")
        val value2 = uid("V2")
        project {
            branch {
                build("Build 1") {
                    release("$value-$value1")
                }
                build("Build 2") {
                    release("$value-$value2")
                }

                val results = searchService.paginatedSearch(
                    SearchRequest(
                        token = "$value-$value1".lowercase(),
                        type = "build",
                    )
                )
                assertEquals(
                    "$value-$value1",
                    results.items.first().title,
                    "Build 1 as first result, whatever the case of the token"
                )
            }
        }
    }

}