package net.nemerosa.ontrack.extension.git.support

import net.nemerosa.ontrack.extension.scm.support.TagPattern
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagPatternTest {

    @Test
    fun `Tag name validation - default`() {
        val pattern = TagPattern("*")
        assertEquals("*", pattern.pattern)
        assertTrue(pattern.isValidTagName("any"))
        assertTrue(pattern.isValidTagName("2.0.0"))
    }

    @Test
    fun `Tag name validation - simple pattern`() {
        val pattern = TagPattern("2.0.*")
        assertEquals("2.0.*", pattern.pattern)
        assertFalse(pattern.isValidTagName("any"))
        assertTrue(pattern.isValidTagName("2.0.0"))
        assertTrue(pattern.isValidTagName("2.0.1"))
        assertTrue(pattern.isValidTagName("2.0.12"))
        assertFalse(pattern.isValidTagName("v2.0.12"))
        assertFalse(pattern.isValidTagName("2.1.0"))
    }

    @Test
    fun `Tag name validation - capturing group`() {
        val pattern = TagPattern("ontrack-(2.0.*)")
        assertEquals("ontrack-(2.0.*)", pattern.pattern)
        assertFalse(pattern.isValidTagName("any"))
        assertFalse(pattern.isValidTagName("2.0.0"))
        assertTrue(pattern.isValidTagName("ontrack-2.0.0"))
        assertTrue(pattern.isValidTagName("ontrack-2.0.1"))
        assertTrue(pattern.isValidTagName("ontrack-2.0.12"))
        assertFalse(pattern.isValidTagName("v2.0.12"))
        assertFalse(pattern.isValidTagName("ontrack-2.1.0"))
    }

    @Test
    fun `Build name from tag - default`() {
        val pattern = TagPattern("*")
        assertEquals("any", pattern.getBuildNameFromTagName("any"))
        assertEquals("2.0.0", pattern.getBuildNameFromTagName("2.0.0"))
    }

    @Test
    fun `Build name from tag - simple`() {
        val pattern = TagPattern("2.0.*")
        assertNull(pattern.getBuildNameFromTagName("any"))
        assertEquals("2.0.0", pattern.getBuildNameFromTagName("2.0.0"))
        assertEquals("2.0.1", pattern.getBuildNameFromTagName("2.0.1"))
        assertEquals("2.0.12", pattern.getBuildNameFromTagName("2.0.12"))
        assertNull(pattern.getBuildNameFromTagName("v2.0.12"))
        assertNull(pattern.getBuildNameFromTagName("2.1.0"))
    }

    @Test
    fun `Build name from tag - capturing group`() {
        val pattern = TagPattern("ontrack-(2.0.*)")
        assertNull(pattern.getBuildNameFromTagName("any"))
        assertNull(pattern.getBuildNameFromTagName("2.0.0"))
        assertEquals("2.0.0", pattern.getBuildNameFromTagName("ontrack-2.0.0"))
        assertEquals("2.0.1", pattern.getBuildNameFromTagName("ontrack-2.0.1"))
        assertEquals("2.0.12", pattern.getBuildNameFromTagName("ontrack-2.0.12"))
        assertNull(pattern.getBuildNameFromTagName("v2.0.12"))
        assertNull(pattern.getBuildNameFromTagName("ontrack-2.1.0"))
    }

    @Test
    fun `Tag name from build - default`() {
        val pattern = TagPattern("*")
        assertEquals("any", pattern.getTagNameFromBuildName("any"))
        assertEquals("2.0.0", pattern.getTagNameFromBuildName("2.0.0"))
    }

    @Test
    fun `Tag name from build - simple`() {
        val pattern = TagPattern("2.0.*")
        assertNull(pattern.getTagNameFromBuildName("any"))
        assertEquals("2.0.0", pattern.getTagNameFromBuildName("2.0.0"))
        assertEquals("2.0.1", pattern.getTagNameFromBuildName("2.0.1"))
        assertEquals("2.0.12", pattern.getTagNameFromBuildName("2.0.12"))
        assertNull(pattern.getTagNameFromBuildName("v2.0.12"))
        assertNull(pattern.getTagNameFromBuildName("2.1.0"))
    }

    @Test
    fun `Tag name from build - capturing group`() {
        val pattern = TagPattern("ontrack-(2.0.*)")
        assertNull(pattern.getTagNameFromBuildName("any"))
        assertEquals("ontrack-2.0.0", pattern.getTagNameFromBuildName("2.0.0"))
        assertEquals("ontrack-2.0.1", pattern.getTagNameFromBuildName("2.0.1"))
        assertEquals("ontrack-2.0.12", pattern.getTagNameFromBuildName("2.0.12"))
        assertNull(pattern.getTagNameFromBuildName("2.1.0"))
    }
}
