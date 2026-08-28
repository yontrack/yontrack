package net.nemerosa.ontrack.common

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticVersionTest {

    @Test
    fun `Parsing of a complete version`() {
        assertEquals(
            SemanticVersion(1, 2, 3),
            SemanticVersion.parse("1.2.3")
        )
    }

    @Test
    fun `Parsing of a version with a v prefix`() {
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("v1.2.3"))
        assertEquals(SemanticVersion(1, 2, 3), SemanticVersion.parse("V1.2.3"))
    }

    @Test
    fun `Parsing of a partial version`() {
        assertEquals(SemanticVersion(1, 0, 0), SemanticVersion.parse("1"))
        assertEquals(SemanticVersion(1, 2, 0), SemanticVersion.parse("1.2"))
        assertEquals(SemanticVersion(1, 0, 0), SemanticVersion.parse("v1"))
    }

    @Test
    fun `Parsing of a prerelease`() {
        assertEquals(
            SemanticVersion(1, 2, 3, prerelease = "rc.1"),
            SemanticVersion.parse("1.2.3-rc.1")
        )
    }

    @Test
    fun `Parsing of build metadata`() {
        assertEquals(
            SemanticVersion(1, 2, 3, build = "20130313144700"),
            SemanticVersion.parse("1.2.3+20130313144700")
        )
        assertEquals(
            SemanticVersion(1, 2, 3, prerelease = "beta", build = "exp.sha.5114f85"),
            SemanticVersion.parse("1.2.3-beta+exp.sha.5114f85")
        )
    }

    @Test
    fun `Parsing of a prerelease on a partial version`() {
        assertEquals(
            SemanticVersion(1, 0, 0, prerelease = "rc1"),
            SemanticVersion.parse("1-rc1")
        )
    }

    @Test
    fun `Parsing of invalid versions`() {
        assertNull(SemanticVersion.parse(null))
        assertNull(SemanticVersion.parse(""))
        assertNull(SemanticVersion.parse("   "))
        assertNull(SemanticVersion.parse("main"))
        assertNull(SemanticVersion.parse("1.x.3"))
        assertNull(SemanticVersion.parse("a1b2c3d"))
        assertNull(SemanticVersion.parse("1.2.3.4"))
        assertNull(SemanticVersion.parse("01.2.3"))
        assertNull(SemanticVersion.parse("1.2.3-"))
        assertNull(SemanticVersion.parse("1.2.3-rc..1"))
        assertNull(SemanticVersion.parse("1.2.3+"))
    }

    @Test
    fun `Comparison on the numeric parts`() {
        assertOrdered("1.0.0", "2.0.0")
        assertOrdered("2.0.0", "2.1.0")
        assertOrdered("2.1.0", "2.1.1")
        assertOrdered("1.9.0", "1.10.0")
    }

    @Test
    fun `A prerelease comes before its release`() {
        assertOrdered("1.0.0-alpha", "1.0.0")
        assertOrdered("1.0.0-rc.1", "1.0.0")
    }

    @Test
    fun `Prerelease precedence follows semver rules`() {
        assertOrdered("1.0.0-alpha", "1.0.0-alpha.1")
        assertOrdered("1.0.0-alpha.1", "1.0.0-alpha.beta")
        assertOrdered("1.0.0-alpha.beta", "1.0.0-beta")
        assertOrdered("1.0.0-beta", "1.0.0-beta.2")
        assertOrdered("1.0.0-beta.2", "1.0.0-beta.11")
        assertOrdered("1.0.0-beta.11", "1.0.0-rc.1")
        assertOrdered("1.0.0-rc.1", "1.0.0")
    }

    @Test
    fun `Numeric prerelease identifiers come before alphanumeric ones`() {
        assertOrdered("1.0.0-1", "1.0.0-alpha")
    }

    @Test
    fun `Build metadata is ignored for the precedence`() {
        assertEquals(
            0,
            SemanticVersion.parse("1.2.3+build.1")!!.compareTo(SemanticVersion.parse("1.2.3+build.2")!!)
        )
    }

    @Test
    fun `Equivalent versions compare as equal`() {
        assertEquals(0, SemanticVersion.parse("v1.2")!!.compareTo(SemanticVersion.parse("1.2.0")!!))
    }

    @Test
    fun `Rendering as a string`() {
        assertEquals("1.2.3", SemanticVersion(1, 2, 3).toString())
        assertEquals("1.2.3-rc.1", SemanticVersion(1, 2, 3, prerelease = "rc.1").toString())
        assertEquals("1.2.3-rc.1+sha", SemanticVersion(1, 2, 3, prerelease = "rc.1", build = "sha").toString())
        assertEquals("1.2.3+sha", SemanticVersion(1, 2, 3, build = "sha").toString())
    }

    private fun assertOrdered(smaller: String, greater: String) {
        val a = SemanticVersion.parse(smaller) ?: error("Cannot parse $smaller")
        val b = SemanticVersion.parse(greater) ?: error("Cannot parse $greater")
        assertTrue(a < b, "$smaller must be lower than $greater")
        assertTrue(b > a, "$greater must be greater than $smaller")
    }

}
