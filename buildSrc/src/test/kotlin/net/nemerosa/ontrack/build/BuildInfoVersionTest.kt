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
            version = "5.4.0-rc.142",
            branch = "main",
            build = "142",
            commit = "abc1234def5678",
        )
        assertEquals("5.4.0", info.version)
        assertEquals(
            mapOf(
                "full" to "5.4.0-rc.142",
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
            version = "5.4.1-rc.7",
            branch = "release/5.4",
            build = "7",
            commit = "abc1234",
        )
        assertEquals("5.4.1", info.version)
        assertEquals("5.4.1-rc.7", info.additional["full"])
        assertEquals("release/5.4", info.additional["branch"])
    }

    @Test
    fun `a GA candidate displays the version it is a candidate for`() {
        val info = BuildInfoVersion.compute(
            projectVersion = "6.0.1",
            version = "6.0.1-rc.700",
            branch = "main",
            build = "700",
            commit = "abc1234",
        )
        assertEquals("6.0.1", info.version)
        assertEquals("6.0.1-rc.700", info.additional["full"])
    }

    @Test
    fun `a pre-release candidate displays the pre-release it is a candidate for`() {
        val info = BuildInfoVersion.compute(
            projectVersion = "6.0.0-beta.6",
            version = "6.0.0-beta.6.rc.700",
            branch = "main",
            build = "700",
            commit = "abc1234",
        )
        assertEquals("6.0.0-beta.6", info.version)
        assertEquals("6.0.0-beta.6.rc.700", info.additional["full"])
    }

    /**
     * Released 5.5 builds and the legacy pre-releases keep the `-rc-<run>` shape they were built
     * with.
     */
    @Test
    fun `a legacy candidate displays its base version`() {
        val info = BuildInfoVersion.compute(
            projectVersion = "5.5.8",
            version = "5.5.8-rc-41",
            branch = "release/5.5",
            build = "41",
            commit = "abc1234",
        )
        assertEquals("5.5.8", info.version)
        assertEquals("5.5.8-rc-41", info.additional["full"])
    }

    @Test
    fun `feature branch displays its own version`() {
        val info = BuildInfoVersion.compute(
            projectVersion = "5.4.0-my-feature-abc1234",
            version = "5.4.0-my-feature-abc1234",
            branch = "my-feature",
            build = "12",
            commit = "abc1234",
        )
        assertEquals("5.4.0-my-feature-abc1234", info.version)
        assertEquals("5.4.0-my-feature-abc1234", info.additional["full"])
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
