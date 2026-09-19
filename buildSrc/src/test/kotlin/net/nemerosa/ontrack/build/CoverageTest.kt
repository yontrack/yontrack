package net.nemerosa.ontrack.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverageTest {

    @Test
    fun `the JaCoCo version is a three-part version`() {
        assertTrue(Regex("""\d+\.\d+\.\d+""").matches(Coverage.JACOCO_VERSION))
    }

    @Test
    fun `unit tests are collected as the unit session`() {
        assertEquals("unit", Coverage.sessionId("test", null))
    }

    @Test
    fun `integration tests are collected as the integration session`() {
        assertEquals("integration", Coverage.sessionId("integrationTest", null))
    }

    @Test
    fun `the suffix names the shard`() {
        assertEquals("integration-3", Coverage.sessionId("integrationTest", "3"))
        assertEquals("unit-2", Coverage.sessionId("test", "2"))
    }

    @Test
    fun `a blank suffix is no suffix`() {
        assertEquals("integration", Coverage.sessionId("integrationTest", ""))
        assertEquals("integration", Coverage.sessionId("integrationTest", "   "))
    }

    @Test
    fun `the suffix is trimmed and slugified`() {
        assertEquals("integration-3", Coverage.sessionId("integrationTest", " 3 "))
        assertEquals("integration-shard-3", Coverage.sessionId("integrationTest", "shard 3"))
    }

    @Test
    fun `test tasks of other kinds are not collected in the Gradle JVM`() {
        assertNull(Coverage.sessionId("kdslAcceptanceTest", null))
        assertNull(Coverage.sessionId("kdslAcceptanceTest", "1"))
        assertNull(Coverage.sessionId("uiTest", null))
    }

    @Test
    fun `collected task names`() {
        assertTrue(Coverage.collects("test"))
        assertTrue(Coverage.collects("integrationTest"))
        assertFalse(Coverage.collects("kdslAcceptanceTest"))
    }

    @Test
    fun `one exec file per test task`() {
        assertEquals("jacoco/test.exec", Coverage.execFilePath("test"))
        assertEquals("jacoco/integrationTest.exec", Coverage.execFilePath("integrationTest"))
    }
}
