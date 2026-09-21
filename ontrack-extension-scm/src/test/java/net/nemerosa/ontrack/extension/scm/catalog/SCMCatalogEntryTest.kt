package net.nemerosa.ontrack.extension.scm.catalog

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SCMCatalogEntryTest {

    @Test
    fun `Backward compatibility with before the addition of last activity`() {
        val json = mapOf(
                "scm" to "github",
                "config" to "Test",
                "repository" to "nemerosa/ontrack",
                "repositoryPage" to "uri:web:github:nemerosa/ontrack",
                "timestamp" to Time.store(Time.now())
        ).asJson()
        val entry = json.parse<SCMCatalogEntry>()
        assertEquals("github", entry.scm)
        assertEquals("Test", entry.config)
        assertEquals("nemerosa/ontrack", entry.repository)
        assertEquals("uri:web:github:nemerosa/ontrack", entry.repositoryPage)
        assertNull(entry.lastActivity)
        assertNotNull(entry.timestamp)
    }

    @Test
    fun `Backward compatibility with before the addition of creation date`() {
        val json = mapOf(
                "scm" to "github",
                "config" to "Test",
                "repository" to "nemerosa/ontrack",
                "repositoryPage" to "uri:web:github:nemerosa/ontrack",
                "lastActivity" to Time.store(Time.now()),
                "timestamp" to Time.store(Time.now())
        ).asJson()
        val entry = json.parse<SCMCatalogEntry>()
        assertEquals("github", entry.scm)
        assertEquals("Test", entry.config)
        assertEquals("nemerosa/ontrack", entry.repository)
        assertEquals("uri:web:github:nemerosa/ontrack", entry.repositoryPage)
        assertNotNull(entry.lastActivity)
        assertNull(entry.createdAt)
        assertNotNull(entry.timestamp)
    }

}