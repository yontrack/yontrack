package net.nemerosa.ontrack.extension.audittrail.status

import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageHealthIndicator
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.message.GlobalMessageService
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.contributor.Status
import org.springframework.graphql.execution.ErrorType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The audit trail status: GraphQL, for the administrators, and the health of the evidence storage —
 * against the MinIO of the integration test stack.
 */
class AuditTrailStatusIT : AbstractAuditTrailITSupport() {

    @Autowired
    private lateinit var evidenceStorageService: EvidenceStorageService

    @Autowired
    private lateinit var instanceKeyService: InstanceKeyService

    @Autowired
    private lateinit var evidenceStorageHealthIndicator: EvidenceStorageHealthIndicator

    @Autowired
    private lateinit var globalMessageService: GlobalMessageService

    private val query = """
        {
            auditTrailStatus {
                licensed
                storage {
                    state
                    message
                    checkedAt
                    endpoint
                    bucket
                    region
                    pathStyle
                }
                keyStatus
                keys {
                    keyId
                    algorithm
                    publicKey
                }
            }
        }
    """

    @Test
    fun `Status of the audit trail for an administrator`() {
        evidenceStorageService.checkStatus()
        val key = instanceKeyService.getPublicKeys().single()
        asAdmin {
            run(query) { data ->
                val status = data.path("auditTrailStatus")
                assertEquals(true, status.path("licensed").asBoolean())
                val storage = status.path("storage")
                assertEquals("OK", storage.path("state").asString())
                assertTrue(storage.path("message").isNull, "No message when OK")
                assertTrue(storage.path("checkedAt").asString().isNotBlank(), "Checked at")
                assertTrue(storage.path("endpoint").asString().startsWith("http://"), "Endpoint")
                assertEquals("yontrack-audit-trail", storage.path("bucket").asString())
                assertEquals("us-east-1", storage.path("region").asString())
                assertEquals(true, storage.path("pathStyle").asBoolean())
                assertEquals("OK", status.path("keyStatus").asString())
                val keys = status.path("keys")
                assertEquals(1, keys.size())
                assertEquals(key.keyId, keys.path(0).path("keyId").asString())
                assertEquals(key.publicKey, keys.path(0).path("publicKey").asString())
            }
        }
    }

    @Test
    fun `Licence off on the status`() {
        withoutTrail {
            asAdmin {
                run(query) { data ->
                    assertEquals(false, data.path("auditTrailStatus").path("licensed").asBoolean())
                }
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Status of the audit trail denied without the global settings`() {
        listOf(
            Roles.GLOBAL_AUTOMATION,
            Roles.GLOBAL_CREATOR,
            Roles.GLOBAL_PARTICIPANT,
            Roles.GLOBAL_READ_ONLY,
        ).forEach { role ->
            asGlobalRole(role) {
                runWithMatchingError(query, errorClassification = ErrorType.FORBIDDEN)
            }
        }
    }

    @Test
    fun `Health of the evidence storage of the stack`() {
        evidenceStorageService.checkStatus()
        val health = evidenceStorageHealthIndicator.health()
        assertEquals(Status.UP, health.status)
        assertEquals("OK", health.details["state"])
    }

    @Test
    fun `No global message of the audit trail when its storage is OK and its key provisioned`() {
        evidenceStorageService.checkStatus()
        assertEquals(
            emptyList(),
            globalMessageService.globalMessages.filter { it.featureId == "audit-trail" }
        )
    }
}
