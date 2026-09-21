package net.nemerosa.ontrack.extension.github.ingestion.support

import net.nemerosa.ontrack.extension.github.ingestion.support.FilterHelper.excludes
import net.nemerosa.ontrack.extension.github.ingestion.support.FilterHelper.includes
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterHelperTest {

    @Test
    fun `Inclusions and exclusions`() {
        assertTrue(includes("ontrack-pro", ".*", ""))
        assertTrue(includes("ontrack-pro", "ontrack.*", ""))
        assertTrue(includes("ontrack-pro", "ontrack.*|nemerosa.*", ""))
        assertFalse(includes("ontrack-pro", "ontrack.*|nemerosa.*", ".*pro.*"))
        assertFalse(includes("ontrack-pro", ".*", ".*"))
        assertFalse(includes("ontrack-pro", ".*", "ontrack-.*"))

        assertFalse(excludes("ontrack-pro", ".*", ""))
        assertFalse(excludes("ontrack-pro", "ontrack.*", ""))
        assertFalse(excludes("ontrack-pro", "ontrack.*|nemerosa.*", ""))
        assertTrue(excludes("ontrack-pro", "ontrack.*|nemerosa.*", ".*pro.*"))
        assertTrue(excludes("ontrack-pro", ".*", ".*"))
        assertTrue(excludes("ontrack-pro", ".*", "ontrack-.*"))
    }
}