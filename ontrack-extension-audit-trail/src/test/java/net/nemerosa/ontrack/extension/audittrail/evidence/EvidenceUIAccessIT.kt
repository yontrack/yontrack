package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.security.EvidenceAuthorizationContributor
import net.nemerosa.ontrack.extension.audittrail.status.AuditTrailStatusService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageServiceImpl
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageState
import net.nemerosa.ontrack.extension.audittrail.ui.AuditTrailGraphQLController
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * What the evidence cell of the validation run page needs to know: whether the user may upload an
 * evidence (`evidence/create` on the validation run) and the state of the evidence storage, which
 * every user may read.
 */
class EvidenceUIAccessIT : AbstractEvidenceITSupport() {

    @Autowired
    private lateinit var instanceKeyService: InstanceKeyService

    @Autowired
    private lateinit var auditTrailStatusService: AuditTrailStatusService

    /**
     * The `evidence/create` authorization of the validation run, for the current user.
     */
    private fun ValidationRun.canUpload(): Boolean? {
        var result: Boolean? = null
        run(
            """
                {
                    validationRuns(id: ${id()}) {
                        authorizations { name action authorized }
                    }
                }
            """
        ) { data ->
            result = data.path("validationRuns").single().path("authorizations")
                .firstOrNull {
                    it.path("name").asString() == EvidenceAuthorizationContributor.EVIDENCE &&
                            it.path("action").asString() == "create"
                }
                ?.path("authorized")?.asBoolean()
        }
        return result
    }

    @Test
    fun `A creator of validation runs may upload evidence`() {
        validationRun {
            assertEquals(true, asCreator { canUpload() })
        }
    }

    @Test
    fun `A user who can only see the validation run may not upload evidence`() {
        validationRun {
            assertEquals(false, asUserWithView(this).call { canUpload() })
        }
    }

    @Test
    fun `Nobody may upload evidence while the licence is off`() {
        validationRun {
            assertEquals(false, withoutTrail { asCreator { canUpload() } })
            assertEquals(false, withoutTrail { asAdmin { canUpload() } })
        }
    }

    @Test
    @AsAdminTest
    fun `Every user may read the state of the evidence storage`() {
        evidenceStorageService.checkStatus()
        listOf(
            Roles.GLOBAL_READ_ONLY,
            Roles.GLOBAL_PARTICIPANT,
            Roles.GLOBAL_AUTOMATION,
        ).forEach { role ->
            asGlobalRole(role) {
                run("{ auditTrailStorageState }") { data ->
                    assertEquals("OK", data.path("auditTrailStorageState").asString(), role)
                }
            }
        }
    }

    @Test
    fun `State of an evidence storage which is not configured`() {
        val storageService = EvidenceStorageServiceImpl(AuditTrailConfigProperties())
        try {
            val controller = AuditTrailGraphQLController(instanceKeyService, auditTrailStatusService, storageService)
            assertEquals(EvidenceStorageState.NOT_CONFIGURED, controller.auditTrailStorageState())
        } finally {
            storageService.destroy()
        }
    }
}
