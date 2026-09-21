package net.nemerosa.ontrack.extension.issues.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IssueServiceConfigurationIdentifierTest {

    @Test
    fun `Parsing test`() {
        val id = IssueServiceConfigurationIdentifier.parse("jira//config")
        assertNotNull(id) {
            assertEquals("jira", it.serviceId)
            assertEquals("config", it.name)
        }
    }

    @Test
    fun `Do not fail on badly formatted identifier but return null instead`() {
        val id = IssueServiceConfigurationIdentifier.parse("jira")
        assertNull(id, "Invalid identifier returns null")
    }

}