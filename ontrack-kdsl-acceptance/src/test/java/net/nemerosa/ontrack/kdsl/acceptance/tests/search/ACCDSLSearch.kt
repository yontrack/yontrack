package net.nemerosa.ontrack.kdsl.acceptance.tests.search

import net.nemerosa.ontrack.common.waitFor
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.extension.general.release
import net.nemerosa.ontrack.kdsl.spec.search.search
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class ACCDSLSearch : AbstractACCDSLTestSupport() {

    @Test
    fun `Searching for a build based on its release information using the build index`() {
        val value = uid("V")
        val value1 = uid("V1")
        val value2 = uid("V2")
        project {
            branch("test") {
                build("1", "Build 1") {
                    release = "$value-$value1"
                    this
                }
                build("2", "Build 2") {
                    release = "$value-$value2"
                    this
                }

                // Checks that an exact match ranks the matching build first. Both builds are
                // returned, since they share the same prefix and the index is an autocomplete one.
                var results = ontrack.search("build", "$value-$value1")
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
                    ontrack.search("build", value)
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

}