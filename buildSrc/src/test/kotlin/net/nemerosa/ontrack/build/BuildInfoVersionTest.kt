package net.nemerosa.ontrack.build

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A release re-tags the rc image and never rebuilds it, so the version the running application
 * shows has to be the one it will be released under, and it has to be stamped at build time.
 * The rc identity is kept aside, in `full`.
 */
class BuildInfoVersionTest {

    @Test
    fun `rc on main displays the base version and keeps the rc version as full`() {
        val info = BuildInfoVersion.compute(
            projectVersion = "5.4.0",
            version = "5.4.0-rc-142",
            branch = "main",
            build = "142",
            commit = "abc1234def5678",
        )
        assertEquals("5.4.0", info.version)
        assertEquals(
            mapOf(
                "full" to "5.4.0-rc-142",
                "branch" to "main",
                "build" to "142",
                "commit" to "abc1234def5678",
            ),
            info.additional,
        )
    }

    @Test
    fun `patch on a release branch displays the patch version`() {
        val info = BuildInfoVersion.compute(
            projectVersion = "5.4.1",
            version = "5.4.1-rc-7",
            branch = "release/5.4",
            build = "7",
            commit = "abc1234",
        )
        assertEquals("5.4.1", info.version)
        assertEquals("5.4.1-rc-7", info.additional["full"])
        assertEquals("release/5.4", info.additional["branch"])
    }

    @Test
    fun `feature branch displays its own version`() {
        val info = BuildInfoVersion.compute(
            projectVersion = "5.4-my-feature-abc1234",
            version = "5.4-my-feature-abc1234",
            branch = "my-feature",
            build = "12",
            commit = "abc1234",
        )
        assertEquals("5.4-my-feature-abc1234", info.version)
        assertEquals("5.4-my-feature-abc1234", info.additional["full"])
    }

    @Test
    fun `local build without any variable uses the project version for both`() {
        val info = BuildInfoVersion.compute(
            projectVersion = "5.4.0-dev",
            version = null,
            branch = null,
            build = null,
            commit = null,
        )
        assertEquals("5.4.0-dev", info.version)
        assertEquals(
            mapOf(
                "full" to "5.4.0-dev",
                "branch" to "n/a",
                "build" to "n/a",
                "commit" to "n/a",
            ),
            info.additional,
        )
    }

    @Test
    fun `blank variables count as unset`() {
        val info = BuildInfoVersion.compute(
            projectVersion = "5.4.0-dev",
            version = " ",
            branch = "",
            build = null,
            commit = null,
        )
        assertEquals("5.4.0-dev", info.additional["full"])
        assertEquals("n/a", info.additional["branch"])
    }
}
